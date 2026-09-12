package com.example.edusync.model;

/**
 * Encapsulates the complete end-to-end document processing outcome for user review.
 * Preserves document metadata, extraction statistics, and the full analysis/proposal plan.
 */
public class DocumentProcessingResult {

    private String requestId;
    private String originalFileName;
    private DocumentType documentType;
    private int totalExtractedCharacters;
    private int totalSentences;
    private DocumentAnalysisResult analysisResult;

    public DocumentProcessingResult() {
    }

    public DocumentProcessingResult(String requestId,
                                    String originalFileName,
                                    DocumentType documentType,
                                    int totalExtractedCharacters,
                                    int totalSentences,
                                    DocumentAnalysisResult analysisResult) {
        this.requestId = requestId;
        this.originalFileName = originalFileName;
        this.documentType = documentType;
        this.totalExtractedCharacters = totalExtractedCharacters;
        this.totalSentences = totalSentences;
        this.analysisResult = analysisResult;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public int getTotalExtractedCharacters() {
        return totalExtractedCharacters;
    }

    public void setTotalExtractedCharacters(int totalExtractedCharacters) {
        this.totalExtractedCharacters = totalExtractedCharacters;
    }

    public int getTotalSentences() {
        return totalSentences;
    }

    public void setTotalSentences(int totalSentences) {
        this.totalSentences = totalSentences;
    }

    public DocumentAnalysisResult getAnalysisResult() {
        return analysisResult;
    }

    public void setAnalysisResult(DocumentAnalysisResult analysisResult) {
        this.analysisResult = analysisResult;
    }

    @Override
    public String toString() {
        return "DocumentProcessingResult{" +
                "requestId='" + requestId + '\'' +
                ", originalFileName='" + originalFileName + '\'' +
                ", documentType=" + documentType +
                ", totalExtractedCharacters=" + totalExtractedCharacters +
                ", totalSentences=" + totalSentences +
                '}';
    }
}
