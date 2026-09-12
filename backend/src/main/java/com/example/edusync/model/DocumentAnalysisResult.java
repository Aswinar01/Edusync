package com.example.edusync.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete document analysis plan summarizing sentence outcomes, counts, and proposed updates.
 */
public class DocumentAnalysisResult {

    private String requestId;
    private int totalSentences;
    private int analyzedSentences;
    private int potentiallyOutdatedCount;
    private int verifiedCount;
    private int proposalCount;
    private List<SentencePipelineResult> results = new ArrayList<>();

    public DocumentAnalysisResult() {
    }

    public DocumentAnalysisResult(String requestId,
                                  int totalSentences,
                                  int analyzedSentences,
                                  int potentiallyOutdatedCount,
                                  int verifiedCount,
                                  int proposalCount,
                                  List<SentencePipelineResult> results) {
        this.requestId = requestId;
        this.totalSentences = totalSentences;
        this.analyzedSentences = analyzedSentences;
        this.potentiallyOutdatedCount = potentiallyOutdatedCount;
        this.verifiedCount = verifiedCount;
        this.proposalCount = proposalCount;
        this.results = results != null ? results : new ArrayList<>();
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public int getTotalSentences() {
        return totalSentences;
    }

    public void setTotalSentences(int totalSentences) {
        this.totalSentences = totalSentences;
    }

    public int getAnalyzedSentences() {
        return analyzedSentences;
    }

    public void setAnalyzedSentences(int analyzedSentences) {
        this.analyzedSentences = analyzedSentences;
    }

    public int getPotentiallyOutdatedCount() {
        return potentiallyOutdatedCount;
    }

    public void setPotentiallyOutdatedCount(int potentiallyOutdatedCount) {
        this.potentiallyOutdatedCount = potentiallyOutdatedCount;
    }

    public int getVerifiedCount() {
        return verifiedCount;
    }

    public void setVerifiedCount(int verifiedCount) {
        this.verifiedCount = verifiedCount;
    }

    public int getProposalCount() {
        return proposalCount;
    }

    public void setProposalCount(int proposalCount) {
        this.proposalCount = proposalCount;
    }

    public List<SentencePipelineResult> getResults() {
        return results;
    }

    public void setResults(List<SentencePipelineResult> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "DocumentAnalysisResult{" +
                "requestId='" + requestId + '\'' +
                ", totalSentences=" + totalSentences +
                ", potentiallyOutdatedCount=" + potentiallyOutdatedCount +
                ", verifiedCount=" + verifiedCount +
                ", proposalCount=" + proposalCount +
                '}';
    }
}
