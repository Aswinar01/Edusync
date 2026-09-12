package com.example.edusync.controller;

import com.example.edusync.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({DocumentService.class, GlobalExceptionHandler.class})
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/documents/upload - Should succeed for valid PDF")
    void uploadValidPdfShouldReturn200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture_notes.pdf",
                "application/pdf",
                "%PDF-1.4 valid sample pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("lecture_notes.pdf"))
                .andExpect(jsonPath("$.size").value(file.getSize()))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.message").value("File uploaded and validated successfully."));
    }

    @Test
    @DisplayName("POST /api/documents/upload - Should succeed for valid DOCX")
    void uploadValidDocxShouldReturn200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "syllabus.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "PK\u0003\u0004 sample docx content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("syllabus.docx"))
                .andExpect(jsonPath("$.size").value(file.getSize()))
                .andExpect(jsonPath("$.contentType").value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(jsonPath("$.message").value("File uploaded and validated successfully."));
    }

    @Test
    @DisplayName("POST /api/documents/upload - Should return 400 for empty file")
    void uploadEmptyFileShouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("File is empty."));
    }

    @Test
    @DisplayName("POST /api/documents/upload - Should return 400 for unsupported file extension")
    void uploadUnsupportedFileTypeShouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/octet-stream",
                "binary content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Unsupported file extension: .exe. Only .pdf and .docx files are supported."));
    }

    @Test
    @DisplayName("POST /api/documents/upload - Should return 400 for missing file part")
    void uploadMissingFileShouldReturn400() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("File is missing. Please provide a file with field name 'file'."));
    }

    @Test
    @DisplayName("POST /api/documents/upload - Should return 400 for missing filename")
    void uploadMissingFilenameShouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                "application/pdf",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Filename is missing."));
    }

    @Test
    @DisplayName("POST /api/documents/upload - Should return 400 for mismatched MIME type")
    void uploadMismatchedMimeTypeShouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "image/png",
                "image data".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid Content-Type 'image/png' for PDF document. Expected 'application/pdf'."));
    }

    @Test
    @DisplayName("POST /api/documents/upload - Should return 400 for non-multipart request")
    void uploadNonMultipartShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/documents/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}
