package com.example.edusync.controller;

import com.example.edusync.model.DocumentAnalysisRequest;
import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.ErrorResponse;
import com.example.edusync.service.DocumentAnalysisOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for document-level analysis orchestration.
 * Analyzes segmented document sentences through the full verification and update-proposal pipeline.
 */
@RestController
@RequestMapping("/api/analysis")
public class DocumentAnalysisController {

    private final DocumentAnalysisOrchestrator orchestrator;

    public DocumentAnalysisController(DocumentAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/document")
    public ResponseEntity<?> analyzeDocument(@RequestBody(required = false) DocumentAnalysisRequest request) {
        if (request == null || request.getSentences() == null || request.getSentences().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Bad Request", "Sentence list cannot be null or empty.")
            );
        }

        for (DocumentSentence sentence : request.getSentences()) {
            if (sentence.getSentenceId() <= 0) {
                return ResponseEntity.badRequest().body(
                        new ErrorResponse("Bad Request", "Sentence ID must be a positive integer (found: " + sentence.getSentenceId() + ").")
                );
            }
            if (sentence.getSentenceText() == null || sentence.getSentenceText().isBlank()) {
                return ResponseEntity.badRequest().body(
                        new ErrorResponse("Bad Request", "Sentence text cannot be empty for sentence ID: " + sentence.getSentenceId() + ".")
                );
            }
        }

        DocumentAnalysisResult result = orchestrator.analyzeDocument(request);
        return ResponseEntity.ok(result);
    }
}
