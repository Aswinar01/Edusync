package com.example.edusync.service;

import com.example.edusync.model.DocumentType;
import com.example.edusync.service.revision.RevisionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory storage service for original uploaded documents and generated revised documents.
 * Guarantees immutability by retaining defensive clones of document byte arrays.
 * Rejects path traversal and malformed request identifiers.
 */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    private final ConcurrentMap<String, StoredDocument> originalDocuments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StoredDocument> revisedDocuments = new ConcurrentHashMap<>();

    public void storeOriginalDocument(String requestId, String filename, DocumentType documentType, byte[] content) {
        validateRequestId(requestId);
        if (content == null) {
            throw new RevisionException("Cannot store null document content.");
        }
        StoredDocument stored = new StoredDocument(requestId, filename, documentType, content.clone(), Instant.now());
        originalDocuments.put(requestId, stored);
        log.info("Stored immutable original document '{}' ({}, {} bytes) under requestId '{}'.",
                filename, documentType, content.length, requestId);
    }

    public StoredDocument getOriginalDocument(String requestId) {
        validateRequestId(requestId);
        StoredDocument doc = originalDocuments.get(requestId);
        if (doc == null) {
            throw new RevisionException("Original document not found for requestId: " + requestId);
        }
        return doc;
    }

    public boolean hasOriginalDocument(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        return originalDocuments.containsKey(requestId);
    }

    public void storeRevisedDocument(String requestId, String filename, DocumentType documentType, byte[] content) {
        validateRequestId(requestId);
        if (content == null) {
            throw new RevisionException("Cannot store null revised document content.");
        }
        StoredDocument stored = new StoredDocument(requestId, filename, documentType, content.clone(), Instant.now());
        revisedDocuments.put(requestId, stored);
        log.info("Stored revised document '{}' ({}, {} bytes) under requestId '{}'.",
                filename, documentType, content.length, requestId);
    }

    public StoredDocument getRevisedDocument(String requestId) {
        validateRequestId(requestId);
        StoredDocument doc = revisedDocuments.get(requestId);
        if (doc == null) {
            throw new RevisionException("Revised document not found for requestId: " + requestId);
        }
        return doc;
    }

    public boolean hasRevisedDocument(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        return revisedDocuments.containsKey(requestId);
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new RevisionException("Request ID cannot be null or blank.");
        }
        if (requestId.contains("..") || requestId.contains("/") || requestId.contains("\\")) {
            log.warn("Path traversal or illegal character detected in requestId: '{}'", requestId);
            throw new RevisionException("Illegal request ID: path traversal or invalid characters detected.");
        }
    }

    /**
     * Immutable container for a stored document.
     */
    public static class StoredDocument {
        private final String requestId;
        private final String filename;
        private final DocumentType documentType;
        private final byte[] data;
        private final Instant createdAt;

        public StoredDocument(String requestId, String filename, DocumentType documentType, byte[] data, Instant createdAt) {
            this.requestId = requestId;
            this.filename = filename;
            this.documentType = documentType;
            this.data = data != null ? data.clone() : new byte[0];
            this.createdAt = createdAt;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getFilename() {
            return filename;
        }

        public DocumentType getDocumentType() {
            return documentType;
        }

        public byte[] getData() {
            return data.clone();
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public int getSize() {
            return data.length;
        }
    }
}
