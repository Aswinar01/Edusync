package com.example.edusync.model;

/**
 * Request payload for submitting a human review decision on an individual sentence.
 * Strictly decoupled from analysis/evidence to prevent client-side fabrication.
 */
public class SentenceReviewRequest {

    private String requestId;
    private int sentenceId;
    private ReviewDecision decision;

    public SentenceReviewRequest() {
    }

    public SentenceReviewRequest(String requestId, int sentenceId, ReviewDecision decision) {
        this.requestId = requestId;
        this.sentenceId = sentenceId;
        this.decision = decision;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public int getSentenceId() {
        return sentenceId;
    }

    public void setSentenceId(int sentenceId) {
        this.sentenceId = sentenceId;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public void setDecision(ReviewDecision decision) {
        this.decision = decision;
    }

    @Override
    public String toString() {
        return "SentenceReviewRequest{" +
                "requestId='" + requestId + '\'' +
                ", sentenceId=" + sentenceId +
                ", decision=" + decision +
                '}';
    }
}
