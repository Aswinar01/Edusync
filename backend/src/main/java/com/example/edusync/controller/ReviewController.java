package com.example.edusync.controller;

import com.example.edusync.model.DocumentReviewRequest;
import com.example.edusync.model.DocumentReviewResult;
import com.example.edusync.model.SentenceDecisionItem;
import com.example.edusync.model.SentenceReviewItem;
import com.example.edusync.model.SentenceReviewRequest;
import com.example.edusync.service.ReviewService;
import com.example.edusync.service.ReviewValidationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for human review and approval of proposed updates.
 * Supports both request-scoped review decision submission and in-memory review session management.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Submits a review decision (APPROVE or REJECT) on an individual sentence in a request-scoped context.
     */
    @PostMapping(value = "/decision", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SentenceReviewItem> submitSentenceDecision(@RequestBody(required = false) SentenceReviewRequest request) {
        if (request == null) {
            throw new ReviewValidationException("Review request body cannot be null.");
        }

        SentenceReviewItem result = reviewService.reviewSentence(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Submits batch review decisions across a document's analysis results in a request-scoped context.
     */
    @PostMapping(value = "/document", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentReviewResult> submitDocumentDecisions(@RequestBody(required = false) DocumentReviewRequest request) {
        if (request == null) {
            throw new ReviewValidationException("Document review request body cannot be null.");
        }

        DocumentReviewResult result = reviewService.reviewDocument(request);
        return ResponseEntity.ok(result);
    }


    /**
     * Retrieves the current state of an in-memory review session by requestId.
     */
    @GetMapping(value = "/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentReviewResult> getReviewSession(@PathVariable("requestId") String requestId) {
        DocumentReviewResult result = reviewService.getReviewSession(requestId);
        return ResponseEntity.ok(result);
    }

    /**
     * Applies a review decision to an in-memory review session.
     */
    @PostMapping(value = "/{requestId}/decisions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentReviewResult> applySessionDecision(
            @PathVariable("requestId") String requestId,
            @RequestBody(required = false) SentenceDecisionItem decisionItem) {
        if (decisionItem == null) {
            throw new ReviewValidationException("Sentence decision item body cannot be null.");
        }

        DocumentReviewResult result = reviewService.applySessionDecision(
                requestId, decisionItem.getSentenceId(), decisionItem.getDecision());
        return ResponseEntity.ok(result);
    }
}
