package com.example.edusync.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result model representing prepared revision instructions derived from a server-owned review session.
 */
public class DocumentRevisionResult {

    private String requestId;
    private RevisionStatus revisionStatus;
    private int totalSentencesConsidered;
    private int approvedUpdateCount;
    private List<DocumentRevisionItem> revisionItems = new ArrayList<>();
    private String statusMessage;

    public DocumentRevisionResult() {
    }

    public DocumentRevisionResult(String requestId,
                                  RevisionStatus revisionStatus,
                                  int totalSentencesConsidered,
                                  int approvedUpdateCount,
                                  List<DocumentRevisionItem> revisionItems,
                                  String statusMessage) {
        this.requestId = requestId;
        this.revisionStatus = revisionStatus;
        this.totalSentencesConsidered = totalSentencesConsidered;
        this.approvedUpdateCount = approvedUpdateCount;
        this.revisionItems = revisionItems != null ? revisionItems : new ArrayList<>();
        this.statusMessage = statusMessage;
    }

    public static DocumentRevisionResult ready(String requestId,
                                               int totalSentencesConsidered,
                                               List<DocumentRevisionItem> revisionItems) {
        int count = revisionItems != null ? revisionItems.size() : 0;
        return new DocumentRevisionResult(
                requestId,
                RevisionStatus.READY,
                totalSentencesConsidered,
                count,
                revisionItems,
                "Revision instructions successfully prepared with " + count + " approved update(s)."
        );
    }

    public static DocumentRevisionResult noApprovedUpdates(String requestId,
                                                           int totalSentencesConsidered) {
        return new DocumentRevisionResult(
                requestId,
                RevisionStatus.NO_APPROVED_UPDATES,
                totalSentencesConsidered,
                0,
                new ArrayList<>(),
                "No approved updates found for revision."
        );
    }

    public static DocumentRevisionResult failed(String requestId,
                                                String errorMessage) {
        return new DocumentRevisionResult(
                requestId,
                RevisionStatus.REVISION_FAILED,
                0,
                0,
                new ArrayList<>(),
                errorMessage
        );
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public RevisionStatus getRevisionStatus() {
        return revisionStatus;
    }

    public void setRevisionStatus(RevisionStatus revisionStatus) {
        this.revisionStatus = revisionStatus;
    }

    public int getTotalSentencesConsidered() {
        return totalSentencesConsidered;
    }

    public void setTotalSentencesConsidered(int totalSentencesConsidered) {
        this.totalSentencesConsidered = totalSentencesConsidered;
    }

    public int getApprovedUpdateCount() {
        return approvedUpdateCount;
    }

    public void setApprovedUpdateCount(int approvedUpdateCount) {
        this.approvedUpdateCount = approvedUpdateCount;
    }

    public List<DocumentRevisionItem> getRevisionItems() {
        return revisionItems;
    }

    public void setRevisionItems(List<DocumentRevisionItem> revisionItems) {
        this.revisionItems = revisionItems != null ? revisionItems : new ArrayList<>();
        this.approvedUpdateCount = this.revisionItems.size();
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    @Override
    public String toString() {
        return "DocumentRevisionResult{" +
                "requestId='" + requestId + '\'' +
                ", revisionStatus=" + revisionStatus +
                ", totalSentencesConsidered=" + totalSentencesConsidered +
                ", approvedUpdateCount=" + approvedUpdateCount +
                ", statusMessage='" + statusMessage + '\'' +
                '}';
    }
}
