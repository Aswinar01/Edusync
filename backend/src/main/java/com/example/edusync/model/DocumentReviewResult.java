package com.example.edusync.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the complete review status of a document, including summary counts
 * and per-sentence review representations.
 */
public class DocumentReviewResult {

    private String requestId;
    private int totalSentences;
    private int reviewableCount;
    private int approvedCount;
    private int rejectedCount;
    private int pendingCount;
    private List<SentenceReviewItem> items = new ArrayList<>();

    public DocumentReviewResult() {
    }

    public static DocumentReviewResult fromAnalysisResult(DocumentAnalysisResult analysisResult) {
        if (analysisResult == null) {
            return null;
        }

        DocumentReviewResult reviewResult = new DocumentReviewResult();
        reviewResult.requestId = analysisResult.getRequestId();
        reviewResult.totalSentences = analysisResult.getTotalSentences();

        List<SentenceReviewItem> reviewItems = new ArrayList<>();
        if (analysisResult.getResults() != null) {
            for (SentencePipelineResult res : analysisResult.getResults()) {
                reviewItems.add(SentenceReviewItem.fromPipelineResult(res));
            }
        }
        reviewResult.items = reviewItems;
        reviewResult.recalculateCounts();
        return reviewResult;
    }

    /**
     * Recalculates summary counts based on current items list.
     */
    public void recalculateCounts() {
        int reviewable = 0;
        int approved = 0;
        int rejected = 0;
        int pending = 0;

        if (items != null) {
            for (SentenceReviewItem item : items) {
                if (item.getReviewStatus() != ReviewStatus.NOT_APPLICABLE) {
                    reviewable++;
                    if (item.getReviewStatus() == ReviewStatus.APPROVED) {
                        approved++;
                    } else if (item.getReviewStatus() == ReviewStatus.REJECTED) {
                        rejected++;
                    } else if (item.getReviewStatus() == ReviewStatus.PENDING) {
                        pending++;
                    }
                }
            }
        }

        this.reviewableCount = reviewable;
        this.approvedCount = approved;
        this.rejectedCount = rejected;
        this.pendingCount = pending;
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

    public int getReviewableCount() {
        return reviewableCount;
    }

    public void setReviewableCount(int reviewableCount) {
        this.reviewableCount = reviewableCount;
    }

    public int getApprovedCount() {
        return approvedCount;
    }

    public void setApprovedCount(int approvedCount) {
        this.approvedCount = approvedCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(int rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public List<SentenceReviewItem> getItems() {
        return items;
    }

    public void setItems(List<SentenceReviewItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        recalculateCounts();
    }

    @Override
    public String toString() {
        return "DocumentReviewResult{" +
                "requestId='" + requestId + '\'' +
                ", totalSentences=" + totalSentences +
                ", reviewableCount=" + reviewableCount +
                ", approvedCount=" + approvedCount +
                ", rejectedCount=" + rejectedCount +
                ", pendingCount=" + pendingCount +
                '}';
    }
}
