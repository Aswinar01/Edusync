package com.example.edusync.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for analyzing a batch of segmented document sentences through the full pipeline.
 */
public class DocumentAnalysisRequest {

    private String requestId;
    private List<DocumentSentence> sentences = new ArrayList<>();

    public DocumentAnalysisRequest() {
    }

    public DocumentAnalysisRequest(List<DocumentSentence> sentences) {
        this.sentences = sentences != null ? sentences : new ArrayList<>();
    }

    public DocumentAnalysisRequest(String requestId, List<DocumentSentence> sentences) {
        this.requestId = requestId;
        this.sentences = sentences != null ? sentences : new ArrayList<>();
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public List<DocumentSentence> getSentences() {
        return sentences;
    }

    public void setSentences(List<DocumentSentence> sentences) {
        this.sentences = sentences != null ? sentences : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "DocumentAnalysisRequest{" +
                "requestId='" + requestId + '\'' +
                ", sentenceCount=" + (sentences != null ? sentences.size() : 0) +
                '}';
    }
}
