package com.example.edusync.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for submitting review decisions across a document analysis result.
 * Strictly operates on the server-stored session identified by requestId.
 */
public class DocumentReviewRequest {

    private String requestId;
    private List<SentenceDecisionItem> decisions = new ArrayList<>();

    public DocumentReviewRequest() {
    }

    public DocumentReviewRequest(String requestId, List<SentenceDecisionItem> decisions) {
        this.requestId = requestId;
        this.decisions = decisions != null ? decisions : new ArrayList<>();
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public List<SentenceDecisionItem> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<SentenceDecisionItem> decisions) {
        this.decisions = decisions != null ? decisions : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "DocumentReviewRequest{" +
                "requestId='" + requestId + '\'' +
                ", decisionsCount=" + (decisions != null ? decisions.size() : 0) +
                '}';
    }
}
