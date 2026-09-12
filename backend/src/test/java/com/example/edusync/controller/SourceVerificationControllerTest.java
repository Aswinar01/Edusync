package com.example.edusync.controller;

import com.example.edusync.model.SourceType;
import com.example.edusync.model.SourceVerificationRequest;
import com.example.edusync.model.SourceVerificationResult;
import com.example.edusync.service.SourceVerificationService;
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

@WebMvcTest(SourceVerificationController.class)
@Import(GlobalExceptionHandler.class)
class SourceVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SourceVerificationService sourceVerificationService;

    @Test
    @DisplayName("POST /api/verification/source - Should return 200 with verification result")
    void testVerifySourceSuccess() throws Exception {
        SourceVerificationRequest request = new SourceVerificationRequest(
                1, "Java 17 is the latest LTS version of Java.", "Sentence refers to a current version."
        );

        SourceVerificationResult mockResult = SourceVerificationResult.verified(
                1,
                "Java 17 is the latest LTS version of Java.",
                "Authoritative lifecycle evidence: Latest version is 21.",
                "Java Lifecycle & Releases",
                "https://endoflife.date/api/java.json",
                "https://www.oracle.com/java/technologies/java-se-support-roadmap.html",
                SourceType.REPUTABLE
        );

        when(sourceVerificationService.verifySource(any(SourceVerificationRequest.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/verification/source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentenceId").value(1))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.sourceType").value("REPUTABLE"))
                .andExpect(jsonPath("$.sourceUrl").value("https://endoflife.date/api/java.json"))
                .andExpect(jsonPath("$.officialReferenceUrl").value("https://www.oracle.com/java/technologies/java-se-support-roadmap.html"));
    }

    @Test
    @DisplayName("POST /api/verification/source - Blank originalSentence should return 400")
    void testVerifySourceBlankSentence() throws Exception {
        SourceVerificationRequest request = new SourceVerificationRequest(
                1, "   ", "Valid reason"
        );

        mockMvc.perform(post("/api/verification/source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFICATION_FAILED"))
                .andExpect(jsonPath("$.currentInformation").value("Original sentence cannot be empty."));
    }

    @Test
    @DisplayName("POST /api/verification/source - Blank reason should return 400")
    void testVerifySourceBlankReason() throws Exception {
        SourceVerificationRequest request = new SourceVerificationRequest(
                1, "Java 17 is current.", "   "
        );

        mockMvc.perform(post("/api/verification/source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFICATION_FAILED"))
                .andExpect(jsonPath("$.currentInformation").value("Verification reason cannot be empty."));
    }

    @Test
    @DisplayName("POST /api/verification/source - Invalid sentenceId <= 0 should return 400")
    void testVerifySourceInvalidSentenceId() throws Exception {
        SourceVerificationRequest request = new SourceVerificationRequest(
                -1, "Java 17 is current.", "Valid reason"
        );

        mockMvc.perform(post("/api/verification/source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFICATION_FAILED"))
                .andExpect(jsonPath("$.currentInformation").value("Sentence ID must be a positive integer."));
    }

    @Test
    @DisplayName("POST /api/verification/source - Null body should return 400")
    void testVerifySourceNullBody() throws Exception {
        mockMvc.perform(post("/api/verification/source")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
