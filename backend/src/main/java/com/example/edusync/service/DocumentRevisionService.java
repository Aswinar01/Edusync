package com.example.edusync.service;

import com.example.edusync.model.DocumentRevisionItem;
import com.example.edusync.model.DocumentRevisionResult;
import com.example.edusync.model.DocumentReviewResult;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.SentenceReviewItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for preparing document revision instructions from server-owned review sessions.
 * Strictly operates on review sessions managed by ReviewService.
 * Does not modify PDF/DOCX files or mutate review sessions.
 */
@Service
public class DocumentRevisionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRevisionService.class);

    private final ReviewService reviewService;

    public DocumentRevisionService(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Prepares revision instructions for the specified requestId by inspecting the server-owned review session.
     * Only sentences with ReviewStatus.APPROVED are converted into revision items.
     *
     * @param requestId unique identifier of the review session
     * @return DocumentRevisionResult containing revision items and status
     */
    public DocumentRevisionResult prepareRevision(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ReviewValidationException("Request ID cannot be null or empty.");
        }

        // Retrieve server-owned review session (throws ReviewValidationException if not found)
        DocumentReviewResult reviewSession = reviewService.getReviewSession(requestId);

        int totalSentences = reviewSession.getTotalSentences();
        List<SentenceReviewItem> sessionItems = reviewSession.getItems();

        List<DocumentRevisionItem> approvedRevisionItems = new ArrayList<>();

        if (sessionItems != null) {
            for (SentenceReviewItem item : sessionItems) {
                if (item != null && item.getReviewStatus() == ReviewStatus.APPROVED) {
                    approvedRevisionItems.add(DocumentRevisionItem.fromApprovedSentence(item));
                }
            }
        }

        if (approvedRevisionItems.isEmpty()) {
            log.info("No approved updates found for review session '{}' (out of {} sentences).",
                    requestId, totalSentences);
            return DocumentRevisionResult.noApprovedUpdates(requestId, totalSentences);
        }

        log.info("Prepared {} revision instruction(s) for review session '{}' (out of {} sentences).",
                approvedRevisionItems.size(), requestId, totalSentences);
        return DocumentRevisionResult.ready(requestId, totalSentences, approvedRevisionItems);
    }
}
