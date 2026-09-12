package com.example.edusync.controller;

import com.example.edusync.model.SentenceAnalysisRequest;
import com.example.edusync.model.SentenceAnalysisResult;
import com.example.edusync.service.GeminiAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class SentenceAnalysisController {

    private final GeminiAnalysisService geminiAnalysisService;

    public SentenceAnalysisController(GeminiAnalysisService geminiAnalysisService) {
        this.geminiAnalysisService = geminiAnalysisService;
    }

    @PostMapping("/sentence")
    public ResponseEntity<SentenceAnalysisResult> analyzeSentence(@RequestBody(required = false) SentenceAnalysisRequest request) {
        if (request == null || request.getSentenceText() == null || request.getSentenceText().isBlank()) {
            int sentenceId = request != null ? request.getSentenceId() : 0;
            String text = request != null && request.getSentenceText() != null ? request.getSentenceText() : "";
            return ResponseEntity.badRequest().body(
                    SentenceAnalysisResult.failed(sentenceId, text, "Sentence text cannot be empty.")
            );
        }

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(request);
        return ResponseEntity.ok(result);
    }
}
