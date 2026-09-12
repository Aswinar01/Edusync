package com.example.edusync.model;

import java.time.Instant;

/**
 * Raw or semi-structured evidence obtained from an external source provider.
 */
public class SourceEvidence {

    private String title;
    private String sourceUrl;
    private String officialReferenceUrl;
    private String content;
    private SourceType sourceType;
    private Instant retrievedAt;
    private boolean successful;
    private String errorMessage;
    private int httpStatus;

    public SourceEvidence() {
        this.retrievedAt = Instant.now();
    }

    public static SourceEvidence success(String title,
                                         String sourceUrl,
                                         String officialReferenceUrl,
                                         String content,
                                         SourceType sourceType) {
        SourceEvidence evidence = new SourceEvidence();
        evidence.title = title;
        evidence.sourceUrl = sourceUrl;
        evidence.officialReferenceUrl = officialReferenceUrl;
        evidence.content = content;
        evidence.sourceType = sourceType;
        evidence.retrievedAt = Instant.now();
        evidence.successful = true;
        evidence.httpStatus = 200;
        return evidence;
    }

    public static SourceEvidence failure(String sourceUrl, String errorMessage, int httpStatus) {
        SourceEvidence evidence = new SourceEvidence();
        evidence.sourceUrl = sourceUrl;
        evidence.errorMessage = errorMessage;
        evidence.httpStatus = httpStatus;
        evidence.successful = false;
        evidence.sourceType = SourceType.UNKNOWN;
        evidence.retrievedAt = Instant.now();
        return evidence;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public void setRetrievedAt(Instant retrievedAt) {
        this.retrievedAt = retrievedAt;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    @Override
    public String toString() {
        return "SourceEvidence{" +
                "title='" + title + '\'' +
                ", sourceUrl='" + sourceUrl + '\'' +
                ", officialReferenceUrl='" + officialReferenceUrl + '\'' +
                ", sourceType=" + sourceType +
                ", successful=" + successful +
                ", httpStatus=" + httpStatus +
                '}';
    }
}
