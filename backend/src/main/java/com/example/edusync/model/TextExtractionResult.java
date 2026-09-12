package com.example.edusync.model;

public class TextExtractionResult {

    private String filename;
    private DocumentType documentType;
    private String extractedText;
    private int pageCount;

    public TextExtractionResult() {
    }

    public TextExtractionResult(String filename, DocumentType documentType, String extractedText) {
        this.filename = filename;
        this.documentType = documentType;
        this.extractedText = extractedText;
    }

    public TextExtractionResult(String filename, DocumentType documentType, String extractedText, int pageCount) {
        this.filename = filename;
        this.documentType = documentType;
        this.extractedText = extractedText;
        this.pageCount = pageCount;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }
}
