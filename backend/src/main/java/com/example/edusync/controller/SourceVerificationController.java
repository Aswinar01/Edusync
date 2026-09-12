package com.example.edusync.controller;

import com.example.edusync.model.SourceVerificationRequest;
import com.example.edusync.model.SourceVerificationResult;
import com.example.edusync.service.SourceVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for external source verification.
 */
@RestController
@RequestMapping("/api/verification")
public class SourceVerificationController {

    private final SourceVerificationService sourceVerificationService;

    public SourceVerificationController(SourceVerificationService sourceVerificationService) {
        this.sourceVerificationService = sourceVerificationService;
    }

    @PostMapping("/source")
    public ResponseEntity<SourceVerificationResult> verifySource(@RequestBody(required = false) SourceVerificationRequest request) {
        if (request == null || request.getOriginalSentence() == null || request.getOriginalSentence().isBlank()) {
            int sentenceId = request != null ? request.getSentenceId() : 0;
            String text = request != null && request.getOriginalSentence() != null ? request.getOriginalSentence() : "";
            return ResponseEntity.badRequest().body(
                    SourceVerificationResult.failed(sentenceId, text, "Original sentence cannot be empty.")
            );
        }

        if (request.getReason() == null || request.getReason().isBlank()) {
            return ResponseEntity.badRequest().body(
                    SourceVerificationResult.failed(request.getSentenceId(), request.getOriginalSentence(), "Verification reason cannot be empty.")
            );
        }

        if (request.getSentenceId() <= 0) {
            return ResponseEntity.badRequest().body(
                    SourceVerificationResult.failed(request.getSentenceId(), request.getOriginalSentence(), "Sentence ID must be a positive integer.")
            );
        }

        SourceVerificationResult result = sourceVerificationService.verifySource(request);
        return ResponseEntity.ok(result);
    }
}
