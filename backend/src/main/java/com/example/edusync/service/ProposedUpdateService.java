package com.example.edusync.service;

import com.example.edusync.config.GeminiProperties;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ProposedUpdate;
import com.example.edusync.model.SentenceAnalysisResult;
import com.example.edusync.model.SourceVerificationResult;
import com.example.edusync.model.VerificationStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates grounded update proposals for verified outdated sentences.
 * Operates strictly on verified external evidence without hallucinating facts.
 */
@Service
public class ProposedUpdateService {

    private static final Logger log = LoggerFactory.getLogger(ProposedUpdateService.class);

    private final RestClient restClient;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;

    public ProposedUpdateService(@Qualifier("geminiRestClient") RestClient restClient,
                                 GeminiProperties geminiProperties,
                                 ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a proposed sentence update strictly grounded in verified source evidence.
     */
    public ProposedUpdate generateProposal(DocumentSentence sentence,
                                           SentenceAnalysisResult analysis,
                                           SourceVerificationResult verification) {
        if (sentence == null) {
            return ProposedUpdate.failed(0, "", "Input sentence cannot be null.");
        }

        int sentenceId = sentence.getSentenceId();
        String originalSentence = sentence.getSentenceText() != null ? sentence.getSentenceText().trim() : "";

        if (originalSentence.isEmpty()) {
            return ProposedUpdate.failed(sentenceId, "", "Original sentence text cannot be empty.");
        }

        if (verification == null || !verification.isVerified() || verification.getVerificationStatus() != VerificationStatus.VERIFIED) {
            return ProposedUpdate.insufficientEvidence(
                    sentenceId,
                    originalSentence,
                    "Sentence was not verified by an external authoritative source.",
                    "",
                    "",
                    "",
                    null,
                    null
            );
        }

        String verifiedInfo = verification.getCurrentInformation() != null ? verification.getCurrentInformation().trim() : "";
        if (verifiedInfo.isEmpty()) {
            return ProposedUpdate.insufficientEvidence(
                    sentenceId,
                    originalSentence,
                    "Verified source information is empty.",
                    "",
                    verification.getSourceTitle(),
                    verification.getSourceUrl(),
                    verification.getOfficialReferenceUrl(),
                    verification.getSourceType()
            );
        }

        // Check if Gemini API key is configured
        if (!geminiProperties.isKeyConfigured()) {
            log.warn("Cannot generate proposal for sentence {}: Gemini API key not configured", sentenceId);
            return ProposedUpdate.failed(sentenceId, originalSentence, "AI service unavailable: Gemini API key is not configured.");
        }

        try {
            String prompt = buildPrompt(originalSentence, verification);

            Map<String, Object> requestPayload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    ),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "temperature", 0.0
                    )
            );

            String requestUrl = String.format("%s/models/%s:generateContent",
                    geminiProperties.getUrl(), geminiProperties.getModel());

            ResponseEntity<String> response = restClient.post()
                    .uri(requestUrl)
                    .header("x-goog-api-key", geminiProperties.getKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .toEntity(String.class);

            return parseAndValidateProposal(response.getBody(), sentenceId, originalSentence, verification);

        } catch (HttpStatusCodeException ex) {
            log.error("Gemini API error during proposal generation for sentence {}: HTTP {}", sentenceId, ex.getStatusCode().value());
            return ProposedUpdate.failed(sentenceId, originalSentence, "AI service error (HTTP " + ex.getStatusCode().value() + ").");
        } catch (ResourceAccessException ex) {
            log.error("Gemini API timeout or connection failure during proposal generation for sentence {}", sentenceId);
            return ProposedUpdate.failed(sentenceId, originalSentence, "AI proposal generation timed out or connection failed.");
        } catch (Exception ex) {
            log.error("Unexpected error during proposal generation for sentence {}: {}", sentenceId, ex.getMessage());
            return ProposedUpdate.failed(sentenceId, originalSentence, "Internal error generating proposal.");
        }
    }

    private String buildPrompt(String originalSentence, SourceVerificationResult verification) {
        String officialRef = verification.getOfficialReferenceUrl() != null ? verification.getOfficialReferenceUrl() : "N/A";
        return """
                You are EduSync's factual update generator for educational study materials.
                Your task is to propose an updated version of a potentially outdated educational sentence.

                CRITICAL GROUNDING CONSTRAINTS:
                1. You must base the update ONLY on the VERIFIED CURRENT INFORMATION provided below.
                2. Do NOT use outside knowledge.
                3. Do NOT invent versions, dates, numbers, policies, names, or technical facts.
                4. Update ONLY the outdated factual portion. Preserve the educational style and sentence structure as closely as possible.
                5. If the VERIFIED CURRENT INFORMATION does not contain sufficient factual details to formulate a safe and accurate replacement sentence, set "insufficientEvidence": true.
                6. If the verified evidence indicates that no textual update is needed, set "noUpdateNeeded": true.

                INPUT DATA:
                - ORIGINAL SENTENCE: "%s"
                - VERIFIED CURRENT INFORMATION: "%s"
                - SOURCE TITLE: "%s"
                - SOURCE URL: "%s"
                - OFFICIAL REFERENCE: "%s"

                Respond strictly with valid JSON conforming to this schema:
                {
                  "proposedSentence": "Updated replacement sentence preserving original style",
                  "reason": "Brief explanation of factual update based strictly on the verified evidence",
                  "insufficientEvidence": false,
                  "noUpdateNeeded": false
                }
                """.formatted(
                escapeJson(originalSentence),
                escapeJson(verification.getCurrentInformation()),
                escapeJson(verification.getSourceTitle() != null ? verification.getSourceTitle() : ""),
                escapeJson(verification.getSourceUrl() != null ? verification.getSourceUrl() : ""),
                escapeJson(officialRef)
        );
    }

    private ProposedUpdate parseAndValidateProposal(String responseBody,
                                                    int sentenceId,
                                                    String originalSentence,
                                                    SourceVerificationResult verification) {
        if (responseBody == null || responseBody.isBlank()) {
            return ProposedUpdate.failed(sentenceId, originalSentence, "Empty response received from AI service.");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode candidatesNode = rootNode.path("candidates");

            if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
                return ProposedUpdate.failed(sentenceId, originalSentence, "No candidate output returned by AI service.");
            }

            JsonNode firstCandidate = candidatesNode.get(0);
            JsonNode textNode = firstCandidate.path("content").path("parts");

            if (!textNode.isArray() || textNode.isEmpty()) {
                return ProposedUpdate.failed(sentenceId, originalSentence, "No text parts returned in model candidate.");
            }

            String rawContent = textNode.get(0).path("text").asText("");
            String jsonText = extractJsonText(rawContent);

            JsonNode proposalNode = objectMapper.readTree(jsonText);

            boolean insufficientEvidence = proposalNode.path("insufficientEvidence").asBoolean(false);
            if (insufficientEvidence) {
                String reason = proposalNode.path("reason").asText("Verified evidence is insufficient to formulate a safe replacement.");
                return ProposedUpdate.insufficientEvidence(
                        sentenceId,
                        originalSentence,
                        reason,
                        verification.getCurrentInformation(),
                        verification.getSourceTitle(),
                        verification.getSourceUrl(),
                        verification.getOfficialReferenceUrl(),
                        verification.getSourceType()
                );
            }

            boolean noUpdateNeeded = proposalNode.path("noUpdateNeeded").asBoolean(false);
            if (noUpdateNeeded) {
                String reason = proposalNode.path("reason").asText("Verified evidence indicates the original sentence is still factually accurate.");
                return ProposedUpdate.noUpdate(
                        sentenceId,
                        originalSentence,
                        reason,
                        verification.getCurrentInformation(),
                        verification.getSourceTitle(),
                        verification.getSourceUrl(),
                        verification.getOfficialReferenceUrl(),
                        verification.getSourceType()
                );
            }

            String proposedSentence = proposalNode.path("proposedSentence").asText("").trim();
            String reason = proposalNode.path("reason").asText("").trim();

            if (proposedSentence.isEmpty()) {
                return ProposedUpdate.insufficientEvidence(
                        sentenceId,
                        originalSentence,
                        "AI model returned an empty proposed sentence.",
                        verification.getCurrentInformation(),
                        verification.getSourceTitle(),
                        verification.getSourceUrl(),
                        verification.getOfficialReferenceUrl(),
                        verification.getSourceType()
                );
            }

            // Defensive Validation: verify that new version numbers or numeric tokens exist in verified information
            if (!validateFactualGrounding(originalSentence, proposedSentence, verification.getCurrentInformation())) {
                log.warn("Defensive validation rejected proposed sentence for sentence {}: ungrounded numeric/version tokens detected", sentenceId);
                return ProposedUpdate.insufficientEvidence(
                        sentenceId,
                        originalSentence,
                        "Proposed sentence failed defensive grounding validation: unsupported factual tokens detected.",
                        verification.getCurrentInformation(),
                        verification.getSourceTitle(),
                        verification.getSourceUrl(),
                        verification.getOfficialReferenceUrl(),
                        verification.getSourceType()
                );
            }

            return ProposedUpdate.proposed(
                    sentenceId,
                    originalSentence,
                    proposedSentence,
                    reason,
                    verification.getCurrentInformation(),
                    verification.getSourceTitle(),
                    verification.getSourceUrl(),
                    verification.getOfficialReferenceUrl(),
                    verification.getSourceType()
            );

        } catch (Exception ex) {
            log.error("Failed to parse or validate proposal JSON for sentence {}: {}", sentenceId, ex.getMessage());
            return ProposedUpdate.failed(sentenceId, originalSentence, "Failed to parse structured update proposal.");
        }
    }

    /**
     * Defensive grounding validation: ensures that any version numbers or numeric figures in the proposed sentence
     * are either already present in the original sentence or explicitly mentioned in the verified source evidence.
     */
    private boolean validateFactualGrounding(String originalSentence, String proposedSentence, String verifiedInformation) {
        if (proposedSentence == null || verifiedInformation == null) {
            return false;
        }

        Pattern numberPattern = Pattern.compile("\\b\\d+(?:\\.\\d+)*\\b");
        Matcher proposedMatcher = numberPattern.matcher(proposedSentence);

        String allowedContext = originalSentence + " " + verifiedInformation;

        while (proposedMatcher.find()) {
            String token = proposedMatcher.group();
            // If the token is not present in originalSentence or verifiedInformation, it was hallucinated
            if (!allowedContext.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private String extractJsonText(String rawText) {
        String trimmed = rawText.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring("```json".length()).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring("```".length()).trim();
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - "```".length()).trim();
        }

        return trimmed;
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
