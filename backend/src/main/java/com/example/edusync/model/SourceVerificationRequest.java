package com.example.edusync.model;

/**
 * Request payload for verifying a potentially outdated sentence against external sources.
 */
public class SourceVerificationRequest {

    private int sentenceId;
    private String originalSentence;
    private String reason;

    public SourceVerificationRequest() {
    }

    public SourceVerificationRequest(int sentenceId, String originalSentence, String reason) {
        this.sentenceId = sentenceId;
        this.originalSentence = originalSentence;
        this.reason = reason;
    }

    public int getSentenceId() {
        return sentenceId;
    }

    public void setSentenceId(int sentenceId) {
        this.sentenceId = sentenceId;
    }

    public String getOriginalSentence() {
        return originalSentence;
    }

    public void setOriginalSentence(String originalSentence) {
        this.originalSentence = originalSentence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "SourceVerificationRequest{" +
                "sentenceId=" + sentenceId +
                ", originalSentence='" + originalSentence + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
