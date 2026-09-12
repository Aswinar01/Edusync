package com.example.edusync.model;

/**
 * Output metadata resulting from applying approved updates to generate a revised document.
 */
public class DocumentRevisionOutput {

    private String requestId;
    private String originalFilename;
    private String revisedFilename;
    private DocumentType documentType;
    private RevisionStatus revisionStatus;
    private int approvedUpdateCount;
    private long outputSize;
    private String generatedAt;
    private String downloadUrl;

    public DocumentRevisionOutput() {
    }

    public DocumentRevisionOutput(String requestId,
                                  String originalFilename,
                                  String revisedFilename,
                                  DocumentType documentType,
                                  RevisionStatus revisionStatus,
                                  int approvedUpdateCount,
                                  long outputSize,
                                  String generatedAt,
                                  String downloadUrl) {
        this.requestId = requestId;
        this.originalFilename = originalFilename;
        this.revisedFilename = revisedFilename;
        this.documentType = documentType;
        this.revisionStatus = revisionStatus;
        this.approvedUpdateCount = approvedUpdateCount;
        this.outputSize = outputSize;
        this.generatedAt = generatedAt;
        this.downloadUrl = downloadUrl;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getRevisedFilename() {
        return revisedFilename;
    }

    public void setRevisedFilename(String revisedFilename) {
        this.revisedFilename = revisedFilename;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public RevisionStatus getRevisionStatus() {
        return revisionStatus;
    }

    public void setRevisionStatus(RevisionStatus revisionStatus) {
        this.revisionStatus = revisionStatus;
    }

    public int getApprovedUpdateCount() {
        return approvedUpdateCount;
    }

    public void setApprovedUpdateCount(int approvedUpdateCount) {
        this.approvedUpdateCount = approvedUpdateCount;
    }

    public long getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(long outputSize) {
        this.outputSize = outputSize;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    @Override
    public String toString() {
        return "DocumentRevisionOutput{" +
                "requestId='" + requestId + '\'' +
                ", originalFilename='" + originalFilename + '\'' +
                ", revisedFilename='" + revisedFilename + '\'' +
                ", documentType=" + documentType +
                ", revisionStatus=" + revisionStatus +
                ", approvedUpdateCount=" + approvedUpdateCount +
                ", outputSize=" + outputSize +
                ", generatedAt='" + generatedAt + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                '}';
    }
}
