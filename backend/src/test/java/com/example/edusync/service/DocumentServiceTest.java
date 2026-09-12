package com.example.edusync.service;

import com.example.edusync.model.DocumentUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentServiceTest {

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService();
    }

    @Test
    @DisplayName("Should successfully validate a valid PDF file")
    void validPdfUploadShouldSucceed() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.pdf",
                "application/pdf",
                "%PDF-1.4 sample content".getBytes()
        );

        DocumentUploadResponse response = documentService.validateAndProcess(file);

        assertThat(response).isNotNull();
        assertThat(response.getFilename()).isEqualTo("notes.pdf");
        assertThat(response.getSize()).isEqualTo(file.getSize());
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getMessage()).isEqualTo("File uploaded and validated successfully.");
    }

    @Test
    @DisplayName("Should successfully validate a valid DOCX file")
    void validDocxUploadShouldSucceed() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "syllabus.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "PK\u0003\u0004 sample docx".getBytes()
        );

        DocumentUploadResponse response = documentService.validateAndProcess(file);

        assertThat(response).isNotNull();
        assertThat(response.getFilename()).isEqualTo("syllabus.docx");
        assertThat(response.getSize()).isEqualTo(file.getSize());
        assertThat(response.getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    @DisplayName("Should reject null file")
    void nullFileShouldThrowException() {
        assertThatThrownBy(() -> documentService.validateAndProcess(null))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("File is missing");
    }

    @Test
    @DisplayName("Should reject empty file")
    void emptyFileShouldThrowException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> documentService.validateAndProcess(file))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("File is empty");
    }

    @Test
    @DisplayName("Should reject missing or blank filename")
    void missingFilenameShouldThrowException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "   ",
                "application/pdf",
                "content".getBytes()
        );

        assertThatThrownBy(() -> documentService.validateAndProcess(file))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("Filename is missing");
    }

    @Test
    @DisplayName("Should sanitize directory traversal characters in filename")
    void directoryTraversalFilenameShouldBeSanitized() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../etc/passwd/notes.pdf",
                "application/pdf",
                "content".getBytes()
        );

        DocumentUploadResponse response = documentService.validateAndProcess(file);
        assertThat(response.getFilename()).isEqualTo("notes.pdf");
    }

    @Test
    @DisplayName("Should reject file without extension")
    void fileWithoutExtensionShouldThrowException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document",
                "application/pdf",
                "content".getBytes()
        );

        assertThatThrownBy(() -> documentService.validateAndProcess(file))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("File has no extension");
    }

    @Test
    @DisplayName("Should reject unsupported extension")
    void unsupportedExtensionShouldThrowException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.py",
                "text/x-python",
                "print('hello')".getBytes()
        );

        assertThatThrownBy(() -> documentService.validateAndProcess(file))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("Unsupported file extension");
    }

    @Test
    @DisplayName("Should reject mismatched Content-Type for PDF")
    void mismatchedContentTypeForPdfShouldThrowException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "image/png",
                "image bytes".getBytes()
        );

        assertThatThrownBy(() -> documentService.validateAndProcess(file))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("Invalid Content-Type");
    }

    @Test
    @DisplayName("Should reject mismatched Content-Type for DOCX")
    void mismatchedContentTypeForDocxShouldThrowException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.docx",
                "text/plain",
                "plain text".getBytes()
        );

        assertThatThrownBy(() -> documentService.validateAndProcess(file))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("Invalid Content-Type");
    }
}
