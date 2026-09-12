package com.example.edusync.controller;

import com.example.edusync.model.SentenceAnalysisRequest;
import com.example.edusync.model.SentenceAnalysisResult;
import com.example.edusync.service.GeminiAnalysisService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SentenceAnalysisController.class)
@Import(GlobalExceptionHandler.class)
class SentenceAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeminiAnalysisService geminiAnalysisService;

    @Test
    @DisplayName("POST /api/analysis/sentence - Should return analysis result")
    void analyzeSentenceShouldReturn200() throws Exception {
        SentenceAnalysisRequest request = new SentenceAnalysisRequest(1, "Java 17 is the latest LTS.");
        SentenceAnalysisResult mockResult = SentenceAnalysisResult.success(
                1,
                "Java 17 is the latest LTS.",
                true,
                "Java 21 is now the latest LTS version."
        );

        when(geminiAnalysisService.analyzeSentence(any(SentenceAnalysisRequest.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/analysis/sentence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentenceId").value(1))
                .andExpect(jsonPath("$.sentenceText").value("Java 17 is the latest LTS."))
                .andExpect(jsonPath("$.potentiallyOutdated").value(true))
                .andExpect(jsonPath("$.reason").value("Java 21 is now the latest LTS version."))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/analysis/sentence - Should return 400 for empty sentence text")
    void analyzeSentenceEmptyTextShouldReturn400() throws Exception {
        SentenceAnalysisRequest request = new SentenceAnalysisRequest(1, "   ");

        mockMvc.perform(post("/api/analysis/sentence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ANALYSIS_FAILED"))
                .andExpect(jsonPath("$.reason").value("Sentence text cannot be empty."));
    }

    @Test
    @DisplayName("POST /api/analysis/sentence - Should return 400 for null body")
    void analyzeSentenceNullBodyShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/analysis/sentence")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
