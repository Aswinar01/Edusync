package com.example.edusync.service;

import com.example.edusync.model.DocumentType;
import com.example.edusync.service.revision.RevisionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentStorageServiceTest {

    private DocumentStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new DocumentStorageService();
    }

    @Test
    @DisplayName("Store and retrieve original document with defensive copying")
    void testStoreAndRetrieveOriginalDefensiveCopy() {
        byte[] originalData = "Sample original PDF bytes".getBytes();
        storageService.storeOriginalDocument("req-1", "test.pdf", DocumentType.PDF, originalData);

        assertThat(storageService.hasOriginalDocument("req-1")).isTrue();

        DocumentStorageService.StoredDocument stored = storageService.getOriginalDocument("req-1");
        assertThat(stored.getRequestId()).isEqualTo("req-1");
        assertThat(stored.getFilename()).isEqualTo("test.pdf");
        assertThat(stored.getDocumentType()).isEqualTo(DocumentType.PDF);
        assertThat(stored.getData()).isEqualTo(originalData);

        // Mutating returned array should NOT mutate stored content
        byte[] retrieved = stored.getData();
        retrieved[0] = 'X';
        assertThat(storageService.getOriginalDocument("req-1").getData()).isEqualTo(originalData);

        // Mutating input array after storing should NOT mutate stored content
        originalData[0] = 'Z';
        assertThat(storageService.getOriginalDocument("req-1").getData()).isNotEqualTo(originalData);
    }

    @Test
    @DisplayName("Store and retrieve revised document")
    void testStoreAndRetrieveRevisedDocument() {
        byte[] revisedData = "Sample revised DOCX bytes".getBytes();
        storageService.storeRevisedDocument("req-2", "revised-doc.docx", DocumentType.DOCX, revisedData);

        assertThat(storageService.hasRevisedDocument("req-2")).isTrue();
        DocumentStorageService.StoredDocument stored = storageService.getRevisedDocument("req-2");
        assertThat(stored.getRequestId()).isEqualTo("req-2");
        assertThat(stored.getFilename()).isEqualTo("revised-doc.docx");
        assertThat(stored.getDocumentType()).isEqualTo(DocumentType.DOCX);
        assertThat(stored.getData()).isEqualTo(revisedData);
    }

    @Test
    @DisplayName("Path traversal attempts in requestId are rejected")
    void testPathTraversalRejected() {
        byte[] data = "some data".getBytes();

        assertThatThrownBy(() -> storageService.storeOriginalDocument("../etc/passwd", "file.pdf", DocumentType.PDF, data))
                .isInstanceOf(RevisionException.class)
                .hasMessageContaining("path traversal");

        assertThatThrownBy(() -> storageService.getOriginalDocument("sub/dir/id"))
                .isInstanceOf(RevisionException.class)
                .hasMessageContaining("path traversal");

        assertThatThrownBy(() -> storageService.storeRevisedDocument("..\\windows\\system32", "file.pdf", DocumentType.PDF, data))
                .isInstanceOf(RevisionException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    @DisplayName("Null or blank requestId is rejected")
    void testNullOrBlankRequestIdRejected() {
        byte[] data = "data".getBytes();

        assertThatThrownBy(() -> storageService.storeOriginalDocument(null, "f.pdf", DocumentType.PDF, data))
                .isInstanceOf(RevisionException.class);

        assertThatThrownBy(() -> storageService.storeOriginalDocument("   ", "f.pdf", DocumentType.PDF, data))
                .isInstanceOf(RevisionException.class);

        assertThatThrownBy(() -> storageService.getOriginalDocument(""))
                .isInstanceOf(RevisionException.class);
    }

    @Test
    @DisplayName("Retrieving non-existent document throws RevisionException")
    void testNotFoundThrowsRevisionException() {
        assertThat(storageService.hasOriginalDocument("unknown-id")).isFalse();
        assertThatThrownBy(() -> storageService.getOriginalDocument("unknown-id"))
                .isInstanceOf(RevisionException.class)
                .hasMessageContaining("Original document not found");

        assertThat(storageService.hasRevisedDocument("unknown-id")).isFalse();
        assertThatThrownBy(() -> storageService.getRevisedDocument("unknown-id"))
                .isInstanceOf(RevisionException.class)
                .hasMessageContaining("Revised document not found");
    }
}
