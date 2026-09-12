package com.example.edusync.model;

import java.util.Objects;

public class SentenceAnalysisResult {

    private int sentenceId;
    private String sentenceText;
    private boolean potentiallyOutdated;
    private String reason;
    private String status; // SUCCESS, AI_UNAVAILABLE, RATE_LIMITED, ANALYSIS_FAILED

    public SentenceAnalysisResult() {
    }

    public SentenceAnalysisResult(int sentenceId, String sentenceText, boolean potentiallyOutdated, String reason) {
        this(sentenceId, sentenceText, potentiallyOutdated, reason, "SUCCESS");
    }

    public SentenceAnalysisResult(int sentenceId, String sentenceText, boolean potentiallyOutdated, String reason, String status) {
        this.sentenceId = sentenceId;
        this.sentenceText = sentenceText;
        this.potentiallyOutdated = potentiallyOutdated;
        this.reason = reason;
        this.status = status;
    }

    public static SentenceAnalysisResult success(int sentenceId, String sentenceText, boolean potentiallyOutdated, String reason) {
        return new SentenceAnalysisResult(sentenceId, sentenceText, potentiallyOutdated, reason, "SUCCESS");
    }

    public static SentenceAnalysisResult aiUnavailable(int sentenceId, String sentenceText, String reason) {
        return new SentenceAnalysisResult(sentenceId, sentenceText, false, reason, "AI_UNAVAILABLE");
    }

    public static SentenceAnalysisResult rateLimited(int sentenceId, String sentenceText, String reason) {
        return new SentenceAnalysisResult(sentenceId, sentenceText, false, reason, "RATE_LIMITED");
    }

    public static SentenceAnalysisResult failed(int sentenceId, String sentenceText, String reason) {
        return new SentenceAnalysisResult(sentenceId, sentenceText, false, reason, "ANALYSIS_FAILED");
    }

    public int getSentenceId() {
        return sentenceId;
    }

    public void setSentenceId(int sentenceId) {
        this.sentenceId = sentenceId;
    }

    public String getSentenceText() {
        return sentenceText;
    }

    public void setSentenceText(String sentenceText) {
        this.sentenceText = sentenceText;
    }

    public boolean isPotentiallyOutdated() {
        return potentiallyOutdated;
    }

    public void setPotentiallyOutdated(boolean potentiallyOutdated) {
        this.potentiallyOutdated = potentiallyOutdated;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SentenceAnalysisResult that = (SentenceAnalysisResult) o;
        return sentenceId == that.sentenceId &&
                potentiallyOutdated == that.potentiallyOutdated &&
                Objects.equals(sentenceText, that.sentenceText) &&
                Objects.equals(reason, that.reason) &&
                Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sentenceId, sentenceText, potentiallyOutdated, reason, status);
    }

    @Override
    public String toString() {
        return "SentenceAnalysisResult{" +
                "sentenceId=" + sentenceId +
                ", sentenceText='" + sentenceText + '\'' +
                ", potentiallyOutdated=" + potentiallyOutdated +
                ", reason='" + reason + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
