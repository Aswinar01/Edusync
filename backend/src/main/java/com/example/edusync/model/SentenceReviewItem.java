package com.example.edusync.model;

/**
 * Review representation of an individual sentence for human evaluation.
 * Contains original sentence, AI analysis, verification evidence, proposed update, and review decision.
 */
public class SentenceReviewItem {

    private int sentenceId;
    private String originalSentence;
    private String proposedSentence;
    private ProposalStatus proposalStatus;
    private PipelineStatus pipelineStatus;
    private String detectionReason;
    private VerificationStatus verificationStatus;
    private String verifiedInformation;
    private String sourceUrl;
    private String officialReferenceUrl;
    private ReviewDecision decision;
    private ReviewStatus reviewStatus;
    private String reviewedAt;

    public SentenceReviewItem() {
    }

    public static SentenceReviewItem fromPipelineResult(SentencePipelineResult result) {
        if (result == null) {
            return null;
        }

        SentenceReviewItem item = new SentenceReviewItem();
        item.sentenceId = result.getSentenceId();
        item.originalSentence = result.getOriginalSentence();
        item.pipelineStatus = result.getStatus();

        if (result.getAnalysis() != null) {
            item.detectionReason = result.getAnalysis().getReason();
        }

        if (result.getVerification() != null) {
            item.verificationStatus = result.getVerification().getVerificationStatus();
        }

        if (result.getProposedUpdate() != null) {
            ProposedUpdate pu = result.getProposedUpdate();
            item.proposedSentence = pu.getProposedSentence();
            item.proposalStatus = pu.getStatus();
            item.verifiedInformation = pu.getVerifiedInformation();
            item.sourceUrl = pu.getSourceUrl();
            item.officialReferenceUrl = pu.getOfficialReferenceUrl();
        } else if (result.getVerification() != null) {
            SourceVerificationResult vr = result.getVerification();
            item.verifiedInformation = vr.getCurrentInformation();
            item.sourceUrl = vr.getSourceUrl();
            item.officialReferenceUrl = vr.getOfficialReferenceUrl();
        }

        // Determine if review is applicable
        if (result.getStatus() == PipelineStatus.VERIFIED_UPDATE_PROPOSED
                && result.getProposedUpdate() != null
                && result.getProposedUpdate().getStatus() == ProposalStatus.PROPOSED
                && result.getProposedUpdate().getProposedSentence() != null
                && !result.getProposedUpdate().getProposedSentence().isBlank()) {
            item.reviewStatus = ReviewStatus.PENDING;
        } else {
            item.reviewStatus = ReviewStatus.NOT_APPLICABLE;
        }

        return item;
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

    public ProposalStatus getProposalStatus() {
        return proposalStatus;
    }

    public void setProposalStatus(ProposalStatus proposalStatus) {
        this.proposalStatus = proposalStatus;
    }

    public PipelineStatus getPipelineStatus() {
        return pipelineStatus;
    }

    public void setPipelineStatus(PipelineStatus pipelineStatus) {
        this.pipelineStatus = pipelineStatus;
    }

    public String getDetectionReason() {
        return detectionReason;
    }

    public void setDetectionReason(String detectionReason) {
        this.detectionReason = detectionReason;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getVerifiedInformation() {
        return verifiedInformation;
    }

    public void setVerifiedInformation(String verifiedInformation) {
        this.verifiedInformation = verifiedInformation;
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

    public ReviewDecision getDecision() {
        return decision;
    }

    public void setDecision(ReviewDecision decision) {
        this.decision = decision;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(String reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    @Override
    public String toString() {
        return "SentenceReviewItem{" +
                "sentenceId=" + sentenceId +
                ", reviewStatus=" + reviewStatus +
                ", decision=" + decision +
                ", originalSentence='" + originalSentence + '\'' +
                ", proposedSentence='" + proposedSentence + '\'' +
                '}';
    }
}
