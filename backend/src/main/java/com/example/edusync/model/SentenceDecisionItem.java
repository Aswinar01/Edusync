package com.example.edusync.model;

/**
 * Individual decision entry within a batch document review request.
 */
public class SentenceDecisionItem {

    private int sentenceId;
    private ReviewDecision decision;

    public SentenceDecisionItem() {
    }

    public SentenceDecisionItem(int sentenceId, ReviewDecision decision) {
        this.sentenceId = sentenceId;
        this.decision = decision;
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
        return "SentenceDecisionItem{" +
                "sentenceId=" + sentenceId +
                ", decision=" + decision +
                '}';
    }
}
