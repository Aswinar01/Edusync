package com.example.edusync.service;

import com.example.edusync.model.SourceEvidence;
import com.example.edusync.model.SourceType;
import com.example.edusync.model.SourceVerificationRequest;
import com.example.edusync.model.SourceVerificationResult;
import com.example.edusync.model.VerificationStatus;
import com.example.edusync.provider.SourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service responsible for orchestrating external source verification.
 * Decoupled from Gemini AI analysis and document controllers.
 */
@Service
public class SourceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SourceVerificationService.class);

    private final List<SourceProvider> providers;

    public SourceVerificationService(List<SourceProvider> providers) {
        this.providers = providers;
    }

    /**
     * Verifies a potentially outdated sentence against external authoritative sources.
     */
    public SourceVerificationResult verifySource(SourceVerificationRequest request) {
        if (request == null) {
            return SourceVerificationResult.failed(0, "", "Verification request cannot be null.");
        }

        int sentenceId = request.getSentenceId();
        String originalSentence = request.getOriginalSentence() != null ? request.getOriginalSentence().trim() : "";
        String reason = request.getReason() != null ? request.getReason().trim() : "";

        if (originalSentence.isEmpty()) {
            return SourceVerificationResult.failed(sentenceId, "", "Original sentence text cannot be empty.");
        }

        // Find a matching provider
        SourceProvider matchedProvider = null;
        for (SourceProvider provider : providers) {
            if (provider.supports(request)) {
                matchedProvider = provider;
                break;
            }
        }

        if (matchedProvider == null) {
            log.info("No source provider supported verification for sentence {}: '{}'", sentenceId, originalSentence);
            return SourceVerificationResult.notVerified(
                    sentenceId,
                    originalSentence,
                    "No authoritative source provider could confidently identify the product or topic.",
                    "",
                    "",
                    null,
                    SourceType.UNKNOWN
            );
        }

        log.debug("Using provider '{}' for sentence {}", matchedProvider.getProviderName(), sentenceId);

        // Step A: Source Retrieval
        SourceEvidence evidence = matchedProvider.fetchEvidence(request);

        if (evidence == null) {
            return SourceVerificationResult.failed(sentenceId, originalSentence, "Source provider returned null evidence.");
        }

        if (!evidence.isSuccessful()) {
            int httpStatus = evidence.getHttpStatus();
            if (httpStatus == 404 || httpStatus == 429 || httpStatus == 500 || httpStatus == 504) {
                return SourceVerificationResult.sourceUnavailable(
                        sentenceId,
                        originalSentence,
                        evidence.getErrorMessage(),
                        evidence.getSourceUrl()
                );
            }
            return SourceVerificationResult.failed(
                    sentenceId,
                    originalSentence,
                    evidence.getErrorMessage()
            );
        }

        // Step B: Evidence Evaluation (Separation of retrieval from verification)
        return evaluateEvidence(request, evidence);
    }

    /**
     * Determines whether the retrieved source evidence actually supports/updates the claim.
     * If the evidence cannot reliably substantiate the claim, NOT_VERIFIED is returned.
     */
    private SourceVerificationResult evaluateEvidence(SourceVerificationRequest request, SourceEvidence evidence) {
        int sentenceId = request.getSentenceId();
        String originalSentence = request.getOriginalSentence();
        String content = evidence.getContent();

        if (content == null || content.isBlank()) {
            return SourceVerificationResult.notVerified(
                    sentenceId,
                    originalSentence,
                    "Retrieved source content was empty.",
                    evidence.getTitle(),
                    evidence.getSourceUrl(),
                    evidence.getOfficialReferenceUrl(),
                    evidence.getSourceType()
            );
        }

        // Evaluate whether the sentence is a temporal version/lifecycle claim
        Pattern versionPattern = Pattern.compile("(?i)\\b(\\d+(\\.\\d+)*)\\b");
        Matcher sentenceMatcher = versionPattern.matcher(originalSentence);

        boolean isTemporalClaim = Pattern.compile("(?i)\\b(latest|current|newest|recent|released|lts|support|version)\\b")
                .matcher(originalSentence + " " + request.getReason()).find();

        if (isTemporalClaim && sentenceMatcher.find()) {
            String claimedVersion = sentenceMatcher.group(1);

            // Check if authoritative content provides a newer or definitive version
            Pattern latestPattern = Pattern.compile("(?i)latest\\s+(?:release\\s+)?(?:cycle|version):\\s*(\\d+(\\.\\d+)*)");
            Matcher contentMatcher = latestPattern.matcher(content);

            if (contentMatcher.find()) {
                String actualLatest = contentMatcher.group(1);

                String currentInfo = String.format(
                        "Authoritative lifecycle evidence: %s Current verified state: Latest version is %s.",
                        content, actualLatest
                );

                return SourceVerificationResult.verified(
                        sentenceId,
                        originalSentence,
                        currentInfo,
                        evidence.getTitle(),
                        evidence.getSourceUrl(),
                        evidence.getOfficialReferenceUrl(),
                        evidence.getSourceType()
                );
            }

            // If content mentions LTS releases and sentence claims LTS
            if (originalSentence.toLowerCase().contains("lts") && content.contains("LTS")) {
                return SourceVerificationResult.verified(
                        sentenceId,
                        originalSentence,
                        "Authoritative lifecycle evidence: " + content,
                        evidence.getTitle(),
                        evidence.getSourceUrl(),
                        evidence.getOfficialReferenceUrl(),
                        evidence.getSourceType()
                );
            }
        }

        // If the source was retrieved successfully, but the evidence does not prove/disprove the specific claim
        log.info("Evidence retrieved for sentence {} but does not definitively substantiate the claim.", sentenceId);
        return SourceVerificationResult.notVerified(
                sentenceId,
                originalSentence,
                "Authoritative source retrieved, but its lifecycle data does not contain sufficient evidence to evaluate this specific claim.",
                evidence.getTitle(),
                evidence.getSourceUrl(),
                evidence.getOfficialReferenceUrl(),
                evidence.getSourceType()
        );
    }
}
