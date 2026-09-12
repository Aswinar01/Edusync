package com.example.edusync.model;

/**
 * Encapsulates an individual revision instruction derived from an approved proposal.
 * The original sentence remains strictly immutable.
 */
public class DocumentRevisionItem {

    private int sentenceId;
    private String originalSentence;
    private String approvedSentence;
    private String sourceUrl;
    private String officialReferenceUrl;
    private String highlightColor;
    private String revisionAction;
    private String citationNote;

    public DocumentRevisionItem() {
        this.highlightColor = "YELLOW";
        this.revisionAction = "REPLACE";
    }

    public DocumentRevisionItem(int sentenceId,
                                String originalSentence,
                                String approvedSentence,
                                String sourceUrl,
                                String officialReferenceUrl,
                                String highlightColor,
                                String revisionAction,
                                String citationNote) {
        this.sentenceId = sentenceId;
        this.originalSentence = originalSentence;
        this.approvedSentence = approvedSentence;
        this.sourceUrl = sourceUrl;
        this.officialReferenceUrl = officialReferenceUrl;
        this.highlightColor = highlightColor != null ? highlightColor : "YELLOW";
        this.revisionAction = revisionAction != null ? revisionAction : "REPLACE";
        this.citationNote = citationNote;
    }

    /**
     * Factory method creating a revision item from an approved sentence review item.
     *
     * @param item approved review item
     * @return constructed revision item
     */
    public static DocumentRevisionItem fromApprovedSentence(SentenceReviewItem item) {
        if (item == null) {
            throw new IllegalArgumentException("SentenceReviewItem cannot be null.");
        }
        if (item.getReviewStatus() != ReviewStatus.APPROVED) {
            throw new IllegalArgumentException("Only APPROVED sentences can become revision items (found: "
                    + item.getReviewStatus() + ").");
        }

        String note = item.getVerifiedInformation() != null && !item.getVerifiedInformation().isBlank()
                ? item.getVerifiedInformation()
                : (item.getSourceUrl() != null ? "Source: " + item.getSourceUrl() : "");

        return new DocumentRevisionItem(
                item.getSentenceId(),
                item.getOriginalSentence(),
                item.getProposedSentence(),
                item.getSourceUrl(),
                item.getOfficialReferenceUrl(),
                "YELLOW",
                "REPLACE",
                note
        );
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

    // Defensive: prevent mutation of originalSentence
    public void setOriginalSentence(String originalSentence) {
        if (this.originalSentence == null) {
            this.originalSentence = originalSentence;
        } else if (!this.originalSentence.equals(originalSentence)) {
            throw new UnsupportedOperationException("Original sentence is immutable and cannot be modified.");
        }
    }

    public String getApprovedSentence() {
        return approvedSentence;
    }

    public void setApprovedSentence(String approvedSentence) {
        this.approvedSentence = approvedSentence;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getOfficialReferenceUrl() {
        return officialReferenceUrl;
    }

    public void setOfficialReferenceUrl(String officialReferenceUrl) {
        this.officialReferenceUrl = officialReferenceUrl;
    }

    public String getHighlightColor() {
        return highlightColor;
    }

    public void setHighlightColor(String highlightColor) {
        this.highlightColor = highlightColor;
    }

    public String getRevisionAction() {
        return revisionAction;
    }

    public void setRevisionAction(String revisionAction) {
        this.revisionAction = revisionAction;
    }

    public String getCitationNote() {
        return citationNote;
    }

    public void setCitationNote(String citationNote) {
        this.citationNote = citationNote;
    }

    @Override
    public String toString() {
        return "DocumentRevisionItem{" +
                "sentenceId=" + sentenceId +
                ", originalSentence='" + originalSentence + '\'' +
                ", approvedSentence='" + approvedSentence + '\'' +
                ", sourceUrl='" + sourceUrl + '\'' +
                ", officialReferenceUrl='" + officialReferenceUrl + '\'' +
                ", highlightColor='" + highlightColor + '\'' +
                ", revisionAction='" + revisionAction + '\'' +
                '}';
    }
}
