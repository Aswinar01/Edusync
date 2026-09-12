package com.example.edusync.model;

import java.util.Objects;

public class SentenceAnalysisRequest {

    private int sentenceId;
    private String sentenceText;

    public SentenceAnalysisRequest() {
    }

    public SentenceAnalysisRequest(int sentenceId, String sentenceText) {
        this.sentenceId = sentenceId;
        this.sentenceText = sentenceText;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SentenceAnalysisRequest that = (SentenceAnalysisRequest) o;
        return sentenceId == that.sentenceId && Objects.equals(sentenceText, that.sentenceText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sentenceId, sentenceText);
    }

    @Override
    public String toString() {
        return "SentenceAnalysisRequest{" +
                "sentenceId=" + sentenceId +
                ", sentenceText='" + sentenceText + '\'' +
                '}';
    }
}
