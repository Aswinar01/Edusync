package com.example.edusync.controller;

import com.example.edusync.model.DocumentAnalysisRequest;
import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.SentencePipelineResult;
import com.example.edusync.service.DocumentAnalysisOrchestrator;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentAnalysisController.class)
@Import(GlobalExceptionHandler.class)
class DocumentAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentAnalysisOrchestrator orchestrator;

    @Test
    @DisplayName("POST /api/analysis/document - Valid request returns 200 with complete analysis")
    void testAnalyzeDocumentSuccess() throws Exception {
        DocumentSentence s1 = new DocumentSentence(1, "Java 17 is the latest LTS version of Java.");
        DocumentSentence s2 = new DocumentSentence(2, "Binary search has O(log n) complexity.");

        DocumentAnalysisRequest request = new DocumentAnalysisRequest("doc-42", List.of(s1, s2));

        DocumentAnalysisResult mockResult = new DocumentAnalysisResult(
                "doc-42", 2, 2, 1, 1, 1,
                List.of(
                        new SentencePipelineResult(1, s1.getSentenceText(), PipelineStatus.VERIFIED_UPDATE_PROPOSED, null, null, null),
                        new SentencePipelineResult(2, s2.getSentenceText(), PipelineStatus.UNCHANGED, null, null, null)
                )
        );

        when(orchestrator.analyzeDocument(any(DocumentAnalysisRequest.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/analysis/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("doc-42"))
                .andExpect(jsonPath("$.totalSentences").value(2))
                .andExpect(jsonPath("$.analyzedSentences").value(2))
                .andExpect(jsonPath("$.potentiallyOutdatedCount").value(1))
                .andExpect(jsonPath("$.verifiedCount").value(1))
                .andExpect(jsonPath("$.proposalCount").value(1))
                .andExpect(jsonPath("$.results[0].status").value("VERIFIED_UPDATE_PROPOSED"))
                .andExpect(jsonPath("$.results[1].status").value("UNCHANGED"));
    }

    @Test
    @DisplayName("POST /api/analysis/document - Empty sentences list returns 400")
    void testAnalyzeDocumentEmptySentences() throws Exception {
        DocumentAnalysisRequest request = new DocumentAnalysisRequest(List.of());

        mockMvc.perform(post("/api/analysis/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sentence list cannot be null or empty."));
    }

    @Test
    @DisplayName("POST /api/analysis/document - Non-positive sentenceId returns 400")
    void testAnalyzeDocumentNonPositiveSentenceId() throws Exception {
        DocumentSentence invalidSentence = new DocumentSentence(-5, "Valid sentence text.");
        DocumentAnalysisRequest request = new DocumentAnalysisRequest(List.of(invalidSentence));

        mockMvc.perform(post("/api/analysis/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sentence ID must be a positive integer (found: -5)."));
    }

    @Test
    @DisplayName("POST /api/analysis/document - Blank sentence text returns 400")
    void testAnalyzeDocumentBlankSentenceText() throws Exception {
        DocumentSentence invalidSentence = new DocumentSentence(1, "   ");
        DocumentAnalysisRequest request = new DocumentAnalysisRequest(List.of(invalidSentence));

        mockMvc.perform(post("/api/analysis/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sentence text cannot be empty for sentence ID: 1."));
    }

    @Test
    @DisplayName("POST /api/analysis/document - Null body returns 400")
    void testAnalyzeDocumentNullBody() throws Exception {
        mockMvc.perform(post("/api/analysis/document")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
