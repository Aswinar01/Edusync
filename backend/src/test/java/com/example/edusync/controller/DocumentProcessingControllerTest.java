package com.example.edusync.controller;

import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentProcessingResult;
import com.example.edusync.model.DocumentType;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.SentencePipelineResult;
import com.example.edusync.service.DocumentExtractionException;
import com.example.edusync.service.DocumentProcessingService;
import com.example.edusync.service.InvalidDocumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentProcessingController.class)
@Import(GlobalExceptionHandler.class)
class DocumentProcessingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentProcessingService documentProcessingService;

    @Test
    @DisplayName("POST /api/documents/process - Should return 200 with DocumentProcessingResult for valid PDF")
    void processValidPdfShouldReturn200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "%PDF-1.4 sample pdf content".getBytes()
        );

        DocumentAnalysisResult analysisResult = new DocumentAnalysisResult(
                "req-12345",
                1, 1, 0, 0, 0,
                List.of(new SentencePipelineResult(1, "Sample sentence.", PipelineStatus.UNCHANGED, null, null, null))
        );

        DocumentProcessingResult mockProcessingResult = new DocumentProcessingResult(
                "req-12345",
                "test-document.pdf",
                DocumentType.PDF,
                16,
                1,
                analysisResult
        );

        when(documentProcessingService.processDocument(any())).thenReturn(mockProcessingResult);

        mockMvc.perform(multipart("/api/documents/process").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-12345"))
                .andExpect(jsonPath("$.originalFileName").value("test-document.pdf"))
                .andExpect(jsonPath("$.documentType").value("PDF"))
                .andExpect(jsonPath("$.totalExtractedCharacters").value(16))
                .andExpect(jsonPath("$.totalSentences").value(1))
                .andExpect(jsonPath("$.analysisResult.requestId").value("req-12345"))
                .andExpect(jsonPath("$.analysisResult.totalSentences").value(1))
                .andExpect(jsonPath("$.analysisResult.results[0].status").value("UNCHANGED"));
    }

    @Test
    @DisplayName("POST /api/documents/process - Should return 200 with DocumentProcessingResult for valid DOCX")
    void processValidDocxShouldReturn200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "syllabus.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "PK\u0003\u0004 sample docx".getBytes()
        );

        DocumentAnalysisResult analysisResult = new DocumentAnalysisResult(
                "req-docx-6789",
                2, 2, 1, 1, 1,
                List.of(
                        new SentencePipelineResult(1, "Java 17 is latest LTS.", PipelineStatus.VERIFIED_UPDATE_PROPOSED, null, null, null),
                        new SentencePipelineResult(2, "Binary search is O(log n).", PipelineStatus.UNCHANGED, null, null, null)
                )
        );

        DocumentProcessingResult mockProcessingResult = new DocumentProcessingResult(
                "req-docx-6789",
                "syllabus.docx",
                DocumentType.DOCX,
                48,
                2,
                analysisResult
        );

        when(documentProcessingService.processDocument(any())).thenReturn(mockProcessingResult);

        mockMvc.perform(multipart("/api/documents/process").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-docx-6789"))
                .andExpect(jsonPath("$.originalFileName").value("syllabus.docx"))
                .andExpect(jsonPath("$.documentType").value("DOCX"))
                .andExpect(jsonPath("$.totalExtractedCharacters").value(48))
                .andExpect(jsonPath("$.totalSentences").value(2))
                .andExpect(jsonPath("$.analysisResult.potentiallyOutdatedCount").value(1))
                .andExpect(jsonPath("$.analysisResult.proposalCount").value(1));
    }

    @Test
    @DisplayName("POST /api/documents/process - Should return 400 when file part is missing")
    void processMissingFileShouldReturn400() throws Exception {
        mockMvc.perform(multipart("/api/documents/process"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("File is missing. Please provide a file with field name 'file'."));
    }

    @Test
    @DisplayName("POST /api/documents/process - Should return 400 when file validation fails (e.g. empty file)")
    void processEmptyFileShouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        when(documentProcessingService.processDocument(any()))
                .thenThrow(new InvalidDocumentException("File is empty."));

        mockMvc.perform(multipart("/api/documents/process").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("File is empty."));
    }

    @Test
    @DisplayName("POST /api/documents/process - Should return 400 when file extension is unsupported")
    void processUnsupportedExtensionShouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.txt",
                "text/plain",
                "plain text".getBytes()
        );

        when(documentProcessingService.processDocument(any()))
                .thenThrow(new InvalidDocumentException("Unsupported file extension: .txt. Only .pdf and .docx files are supported."));

        mockMvc.perform(multipart("/api/documents/process").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Unsupported file extension: .txt. Only .pdf and .docx files are supported."));
    }

    @Test
    @DisplayName("POST /api/documents/process - Should return 422 when extraction fails (e.g. empty readable text)")
    void processExtractionFailureShouldReturn422() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scanned.pdf",
                "application/pdf",
                "%PDF-1.4 scanned".getBytes()
        );

        when(documentProcessingService.processDocument(any()))
                .thenThrow(new DocumentExtractionException("Document contains no readable text."));

        mockMvc.perform(multipart("/api/documents/process").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message").value("Document contains no readable text."));
    }

    @Test
    @DisplayName("POST /api/documents/process - Should return 400 for non-multipart request")
    void processNonMultipartShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/documents/process"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}
