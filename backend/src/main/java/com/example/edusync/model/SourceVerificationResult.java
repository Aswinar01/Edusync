package com.example.edusync.model;

import java.time.Instant;

/**
 * Structured response containing the factual verification outcome and source metadata.
 */
public class SourceVerificationResult {

    private int sentenceId;
    private String originalSentence;
    private boolean verified;
    private String currentInformation;
    private String sourceTitle;
    private String sourceUrl;
    private String officialReferenceUrl;
    private SourceType sourceType;
    private VerificationStatus verificationStatus;
    private Instant retrievedAt;

    public SourceVerificationResult() {
        this.retrievedAt = Instant.now();
    }

    public static SourceVerificationResult verified(int sentenceId,
                                                    String originalSentence,
                                                    String currentInformation,
                                                    String sourceTitle,
                                                    String sourceUrl,
                                                    String officialReferenceUrl,
                                                    SourceType sourceType) {
        SourceVerificationResult result = new SourceVerificationResult();
        result.sentenceId = sentenceId;
        result.originalSentence = originalSentence;
        result.verified = true;
        result.currentInformation = currentInformation;
        result.sourceTitle = sourceTitle;
        result.sourceUrl = sourceUrl;
        result.officialReferenceUrl = officialReferenceUrl;
        result.sourceType = sourceType;
        result.verificationStatus = VerificationStatus.VERIFIED;
        result.retrievedAt = Instant.now();
        return result;
    }

    public static SourceVerificationResult notVerified(int sentenceId,
                                                       String originalSentence,
                                                       String reason,
                                                       String sourceTitle,
                                                       String sourceUrl,
                                                       String officialReferenceUrl,
                                                       SourceType sourceType) {
        SourceVerificationResult result = new SourceVerificationResult();
        result.sentenceId = sentenceId;
        result.originalSentence = originalSentence;
        result.verified = false;
        result.currentInformation = reason;
        result.sourceTitle = sourceTitle;
        result.sourceUrl = sourceUrl;
        result.officialReferenceUrl = officialReferenceUrl;
        result.sourceType = sourceType;
        result.verificationStatus = VerificationStatus.NOT_VERIFIED;
        result.retrievedAt = Instant.now();
        return result;
    }

    public static SourceVerificationResult sourceUnavailable(int sentenceId,
                                                             String originalSentence,
                                                             String errorMessage,
                                                             String sourceUrl) {
        SourceVerificationResult result = new SourceVerificationResult();
        result.sentenceId = sentenceId;
        result.originalSentence = originalSentence;
        result.verified = false;
        result.currentInformation = errorMessage;
        result.sourceTitle = "External Source Unavailable";
        result.sourceUrl = sourceUrl != null ? sourceUrl : "";
        result.sourceType = SourceType.UNKNOWN;
        result.verificationStatus = VerificationStatus.SOURCE_UNAVAILABLE;
        result.retrievedAt = Instant.now();
        return result;
    }

    public static SourceVerificationResult failed(int sentenceId,
                                                  String originalSentence,
                                                  String errorMessage) {
        SourceVerificationResult result = new SourceVerificationResult();
        result.sentenceId = sentenceId;
        result.originalSentence = originalSentence;
        result.verified = false;
        result.currentInformation = errorMessage;
        result.sourceTitle = "";
        result.sourceUrl = "";
        result.sourceType = SourceType.UNKNOWN;
        result.verificationStatus = VerificationStatus.VERIFICATION_FAILED;
        result.retrievedAt = Instant.now();
        return result;
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

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getCurrentInformation() {
        return currentInformation;
    }

    public void setCurrentInformation(String currentInformation) {
        this.currentInformation = currentInformation;
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

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public void setRetrievedAt(Instant retrievedAt) {
        this.retrievedAt = retrievedAt;
    }

    @Override
    public String toString() {
        return "SourceVerificationResult{" +
                "sentenceId=" + sentenceId +
                ", verified=" + verified +
                ", verificationStatus=" + verificationStatus +
                ", sourceTitle='" + sourceTitle + '\'' +
                ", sourceUrl='" + sourceUrl + '\'' +
                ", officialReferenceUrl='" + officialReferenceUrl + '\'' +
                ", sourceType=" + sourceType +
                '}';
    }
}
