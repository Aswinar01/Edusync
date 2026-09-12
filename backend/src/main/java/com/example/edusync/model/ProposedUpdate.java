package com.example.edusync.model;

/**
 * Represents a proposed grounded update for an outdated sentence.
 * The original sentence is preserved untouched.
 */
public class ProposedUpdate {

    private int sentenceId;
    private String originalSentence;
    private String proposedSentence;
    private String reason;
    private String verifiedInformation;
    private String sourceTitle;
    private String sourceUrl;
    private String officialReferenceUrl;
    private SourceType sourceType;
    private ProposalStatus status;

    public ProposedUpdate() {
    }

    public static ProposedUpdate proposed(int sentenceId,
                                          String originalSentence,
                                          String proposedSentence,
                                          String reason,
                                          String verifiedInformation,
                                          String sourceTitle,
                                          String sourceUrl,
                                          String officialReferenceUrl,
                                          SourceType sourceType) {
        ProposedUpdate update = new ProposedUpdate();
        update.sentenceId = sentenceId;
        update.originalSentence = originalSentence;
        update.proposedSentence = proposedSentence;
        update.reason = reason;
        update.verifiedInformation = verifiedInformation;
        update.sourceTitle = sourceTitle;
        update.sourceUrl = sourceUrl;
        update.officialReferenceUrl = officialReferenceUrl;
        update.sourceType = sourceType;
        update.status = ProposalStatus.PROPOSED;
        return update;
    }

    public static ProposedUpdate noUpdate(int sentenceId,
                                          String originalSentence,
                                          String reason,
                                          String verifiedInformation,
                                          String sourceTitle,
                                          String sourceUrl,
                                          String officialReferenceUrl,
                                          SourceType sourceType) {
        ProposedUpdate update = new ProposedUpdate();
        update.sentenceId = sentenceId;
        update.originalSentence = originalSentence;
        update.proposedSentence = originalSentence;
        update.reason = reason;
        update.verifiedInformation = verifiedInformation;
        update.sourceTitle = sourceTitle;
        update.sourceUrl = sourceUrl;
        update.officialReferenceUrl = officialReferenceUrl;
        update.sourceType = sourceType;
        update.status = ProposalStatus.NO_UPDATE;
        return update;
    }

    public static ProposedUpdate insufficientEvidence(int sentenceId,
                                                      String originalSentence,
                                                      String reason,
                                                      String verifiedInformation,
                                                      String sourceTitle,
                                                      String sourceUrl,
                                                      String officialReferenceUrl,
                                                      SourceType sourceType) {
        ProposedUpdate update = new ProposedUpdate();
        update.sentenceId = sentenceId;
        update.originalSentence = originalSentence;
        update.proposedSentence = "";
        update.reason = reason;
        update.verifiedInformation = verifiedInformation;
        update.sourceTitle = sourceTitle;
        update.sourceUrl = sourceUrl;
        update.officialReferenceUrl = officialReferenceUrl;
        update.sourceType = sourceType;
        update.status = ProposalStatus.INSUFFICIENT_EVIDENCE;
        return update;
    }

    public static ProposedUpdate failed(int sentenceId,
                                        String originalSentence,
                                        String errorMessage) {
        ProposedUpdate update = new ProposedUpdate();
        update.sentenceId = sentenceId;
        update.originalSentence = originalSentence;
        update.proposedSentence = "";
        update.reason = errorMessage;
        update.verifiedInformation = "";
        update.sourceTitle = "";
        update.sourceUrl = "";
        update.officialReferenceUrl = null;
        update.sourceType = SourceType.UNKNOWN;
        update.status = ProposalStatus.GENERATION_FAILED;
        return update;
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

    public String getProposedSentence() {
        return proposedSentence;
    }

    public void setProposedSentence(String proposedSentence) {
        this.proposedSentence = proposedSentence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getVerifiedInformation() {
        return verifiedInformation;
    }

    public void setVerifiedInformation(String verifiedInformation) {
        this.verifiedInformation = verifiedInformation;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
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

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public void setStatus(ProposalStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ProposedUpdate{" +
                "sentenceId=" + sentenceId +
                ", status=" + status +
                ", originalSentence='" + originalSentence + '\'' +
                ", proposedSentence='" + proposedSentence + '\'' +
                ", sourceUrl='" + sourceUrl + '\'' +
                ", officialReferenceUrl='" + officialReferenceUrl + '\'' +
                '}';
    }
}
