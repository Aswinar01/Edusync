package com.example.edusync.service;

import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentReviewRequest;
import com.example.edusync.model.DocumentReviewResult;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ReviewDecision;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.SentenceDecisionItem;
import com.example.edusync.model.SentenceReviewItem;
import com.example.edusync.model.SentenceReviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for managing human review decisions on proposed updates.
 * Decisions operate strictly on server-generated DocumentAnalysisResults to prevent
 * client-side evidence or proposal fabrication.
 * Ensures that original sentences are preserved immutably and enforces strict state transitions.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ConcurrentMap<String, DocumentReviewResult> reviewSessions = new ConcurrentHashMap<>();

    /**
     * Initializes an in-memory review session from a server-generated DocumentAnalysisResult.
     *
     * @param analysisResult server-generated document analysis result
     * @return initialized document review result
     */
    public DocumentReviewResult createReviewSession(DocumentAnalysisResult analysisResult) {
        if (analysisResult == null) {
            throw new ReviewValidationException("Document analysis result cannot be null.");
        }

        if (analysisResult.getRequestId() == null || analysisResult.getRequestId().isBlank()) {
            throw new ReviewValidationException("Document analysis result must contain a valid requestId.");
        }

        DocumentReviewResult reviewResult = DocumentReviewResult.fromAnalysisResult(analysisResult);
        reviewSessions.put(reviewResult.getRequestId(), reviewResult);
        log.info("Created in-memory review session for requestId '{}' ({} sentences, {} reviewable).",
                reviewResult.getRequestId(), reviewResult.getTotalSentences(), reviewResult.getReviewableCount());
        return reviewResult;
    }

    /**
     * Retrieves an in-memory review session by its requestId.
     *
     * @param requestId the requestId of the review session
     * @return current document review result
     */
    public DocumentReviewResult getReviewSession(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ReviewValidationException("Request ID cannot be null or empty.");
        }

        DocumentReviewResult session = reviewSessions.get(requestId);
        if (session == null) {
            throw new ReviewValidationException("Review session not found for requestId: " + requestId);
        }

        return session;
    }

    /**
     * Evaluates and applies a review decision for an individual sentence against the server-stored session.
     *
     * @param request the sentence review request containing requestId, sentenceId, and decision
     * @return review-ready item with decision and updated status
     */
    public SentenceReviewItem reviewSentence(SentenceReviewRequest request) {
        if (request == null) {
            throw new ReviewValidationException("Review request cannot be null.");
        }

        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new ReviewValidationException("Request ID cannot be null or empty.");
        }

        if (request.getSentenceId() <= 0) {
            throw new ReviewValidationException("Sentence ID must be a positive integer (found: " + request.getSentenceId() + ").");
        }

        if (request.getDecision() == null) {
            throw new ReviewValidationException("Review decision cannot be null. Must be APPROVE or REJECT.");
        }

        DocumentReviewResult session = getReviewSession(request.getRequestId());
        SentenceReviewItem targetItem = findItemInSession(session, request.getSentenceId(), request.getRequestId());

        applyDecisionToItem(targetItem, request.getDecision());
        session.recalculateCounts();

        return targetItem;
    }

    /**
     * Evaluates and applies multiple review decisions across a document's server-stored session.
     *
     * @param request document review request containing requestId and list of decisions
     * @return updated document review result with recalculated metrics
     */
    public DocumentReviewResult reviewDocument(DocumentReviewRequest request) {
        if (request == null) {
            throw new ReviewValidationException("Document review request cannot be null.");
        }

        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new ReviewValidationException("Request ID cannot be null or empty.");
        }

        if (request.getDecisions() == null || request.getDecisions().isEmpty()) {
            throw new ReviewValidationException("Review decisions list cannot be null or empty.");
        }

        DocumentReviewResult session = getReviewSession(request.getRequestId());

        for (SentenceDecisionItem decisionItem : request.getDecisions()) {
            if (decisionItem == null) {
                throw new ReviewValidationException("Sentence decision item cannot be null.");
            }

            if (decisionItem.getSentenceId() <= 0) {
                throw new ReviewValidationException("Sentence ID must be a positive integer (found: " + decisionItem.getSentenceId() + ").");
            }

            if (decisionItem.getDecision() == null) {
                throw new ReviewValidationException("Review decision cannot be null for sentence ID: " + decisionItem.getSentenceId() + ".");
            }

            SentenceReviewItem targetItem = findItemInSession(session, decisionItem.getSentenceId(), request.getRequestId());
            applyDecisionToItem(targetItem, decisionItem.getDecision());
        }

        session.recalculateCounts();
        return session;
    }

    /**
     * Applies a review decision to a specific sentence in an in-memory review session.
     *
     * @param requestId  the review session ID
     * @param sentenceId the sentence ID to decide upon
     * @param decision   the review decision (APPROVE or REJECT)
     * @return updated document review result
     */
    public DocumentReviewResult applySessionDecision(String requestId, int sentenceId, ReviewDecision decision) {
        if (sentenceId <= 0) {
            throw new ReviewValidationException("Sentence ID must be a positive integer (found: " + sentenceId + ").");
        }

        if (decision == null) {
            throw new ReviewValidationException("Review decision cannot be null. Must be APPROVE or REJECT.");
        }

        DocumentReviewResult session = getReviewSession(requestId);
        SentenceReviewItem targetItem = findItemInSession(session, sentenceId, requestId);

        applyDecisionToItem(targetItem, decision);
        session.recalculateCounts();

        log.info("Applied decision '{}' to sentence {} in session '{}' (Approved: {}, Rejected: {}, Pending: {}).",
                decision, sentenceId, requestId, session.getApprovedCount(), session.getRejectedCount(), session.getPendingCount());
        return session;
    }

    private SentenceReviewItem findItemInSession(DocumentReviewResult session, int sentenceId, String requestId) {
        for (SentenceReviewItem item : session.getItems()) {
            if (item.getSentenceId() == sentenceId) {
                return item;
            }
        }
        throw new ReviewValidationException("Sentence ID " + sentenceId + " not found in review session: " + requestId);
    }

    /**
     * Internal helper to apply and validate a review decision on a SentenceReviewItem.
     * Strictly preserves the original sentence while validating state transitions.
     */
    private void applyDecisionToItem(SentenceReviewItem item, ReviewDecision decision) {
        int sentenceId = item.getSentenceId();

        // Enforce state transition rules:
        // Once APPROVED or REJECTED, cannot be modified or silently changed
        if (item.getReviewStatus() == ReviewStatus.APPROVED) {
            throw new ReviewValidationException("Cannot modify decision for sentence ID " + sentenceId
                    + ": sentence is already APPROVED.");
        }

        if (item.getReviewStatus() == ReviewStatus.REJECTED) {
            throw new ReviewValidationException("Cannot modify decision for sentence ID " + sentenceId
                    + ": sentence is already REJECTED.");
        }

        if (decision == ReviewDecision.APPROVE) {
            validateApprovalEligibility(item);
            item.setDecision(ReviewDecision.APPROVE);
            item.setReviewStatus(ReviewStatus.APPROVED);
            item.setReviewedAt(Instant.now().toString());
            log.debug("Sentence {} proposal approved.", sentenceId);
        } else if (decision == ReviewDecision.REJECT) {
            if (item.getReviewStatus() == ReviewStatus.NOT_APPLICABLE) {
                throw new ReviewValidationException("Cannot reject sentence ID " + sentenceId
                        + ": sentence does not have an active proposal to reject.");
            }
            item.setDecision(ReviewDecision.REJECT);
            item.setReviewStatus(ReviewStatus.REJECTED);
            item.setReviewedAt(Instant.now().toString());
            log.debug("Sentence {} proposal rejected.", sentenceId);
        }
    }

    /**
     * Verifies that a sentence has a valid, verified proposed update before allowing approval.
     */
    private void validateApprovalEligibility(SentenceReviewItem item) {
        int sentenceId = item.getSentenceId();

        if (item.getPipelineStatus() == PipelineStatus.UNCHANGED) {
            throw new ReviewValidationException("Cannot approve sentence ID " + sentenceId
                    + ": sentence is unchanged and has no proposed update.");
        }

        if (item.getPipelineStatus() == PipelineStatus.POTENTIALLY_OUTDATED_UNVERIFIED) {
            throw new ReviewValidationException("Cannot approve sentence ID " + sentenceId
                    + ": external source could not verify the claim (POTENTIALLY_OUTDATED_UNVERIFIED).");
        }

        if (item.getPipelineStatus() == PipelineStatus.VERIFIED_NO_UPDATE
                || item.getProposalStatus() == ProposalStatus.NO_UPDATE) {
            throw new ReviewValidationException("Cannot approve sentence ID " + sentenceId
                    + ": verified evidence indicates no update is required (NO_UPDATE).");
        }

        if (item.getPipelineStatus() == PipelineStatus.VERIFIED_INSUFFICIENT_EVIDENCE
                || item.getProposalStatus() == ProposalStatus.INSUFFICIENT_EVIDENCE) {
            throw new ReviewValidationException("Cannot approve sentence ID " + sentenceId
                    + ": verified evidence was insufficient to formulate a safe replacement (INSUFFICIENT_EVIDENCE).");
        }

        if (item.getProposalStatus() == ProposalStatus.GENERATION_FAILED) {
            throw new ReviewValidationException("Cannot approve sentence ID " + sentenceId
                    + ": update proposal generation failed (GENERATION_FAILED).");
        }

        if (item.getPipelineStatus() == PipelineStatus.SOURCE_UNAVAILABLE) {
            throw new ReviewValidationException("Cannot approve sentence ID " + sentenceId
                    + ": external verification source was unavailable (SOURCE_UNAVAILABLE).");
        }

        if (item.getPipelineStatus() == PipelineStatus.AI_UNAVAILABLE) {
            throw new ReviewValidationException("Cannot approve sentence ID " + sentenceId
                    + ": AI analysis service was unavailable (AI_UNAVAILABLE).");
        }

        if (item.getPipelineStatus() != PipelineStatus.VERIFIED_UPDATE_PROPOSED
                || item.getProposalStatus() != ProposalStatus.PROPOSED
                || item.getProposedSentence() == null
                || item.getProposedSentence().isBlank()) {
            throw new ReviewValidationException("Cannot approve sentence ID " + sentenceId
                    + ": sentence does not have a valid, verified proposed update.");
        }
    }
}
