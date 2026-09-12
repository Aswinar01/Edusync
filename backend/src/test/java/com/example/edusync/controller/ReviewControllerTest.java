package com.example.edusync.controller;

import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentReviewRequest;
import com.example.edusync.model.DocumentReviewResult;
import com.example.edusync.model.ReviewDecision;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.SentenceDecisionItem;
import com.example.edusync.model.SentenceReviewItem;
import com.example.edusync.model.SentenceReviewRequest;
import com.example.edusync.service.ReviewService;
import com.example.edusync.service.ReviewValidationException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import(GlobalExceptionHandler.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    @DisplayName("POST /api/reviews/decision - Valid APPROVE returns 200 with approved review item")
    void testSubmitSentenceDecisionApprove() throws Exception {
        SentenceReviewRequest request = new SentenceReviewRequest("req-1", 1, ReviewDecision.APPROVE);

        SentenceReviewItem mockItem = new SentenceReviewItem();
        mockItem.setSentenceId(1);
        mockItem.setOriginalSentence("Original sentence.");
        mockItem.setProposedSentence("Updated sentence.");
        mockItem.setDecision(ReviewDecision.APPROVE);
        mockItem.setReviewStatus(ReviewStatus.APPROVED);
        mockItem.setSourceUrl("https://endoflife.date/api/java.json");

        when(reviewService.reviewSentence(any(SentenceReviewRequest.class))).thenReturn(mockItem);

        mockMvc.perform(post("/api/reviews/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentenceId").value(1))
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.originalSentence").value("Original sentence."))
                .andExpect(jsonPath("$.proposedSentence").value("Updated sentence."))
                .andExpect(jsonPath("$.sourceUrl").value("https://endoflife.date/api/java.json"));
    }

    @Test
    @DisplayName("POST /api/reviews/decision - Valid REJECT returns 200 with rejected review item")
    void testSubmitSentenceDecisionReject() throws Exception {
        SentenceReviewRequest request = new SentenceReviewRequest("req-1", 1, ReviewDecision.REJECT);

        SentenceReviewItem mockItem = new SentenceReviewItem();
        mockItem.setSentenceId(1);
        mockItem.setOriginalSentence("Original sentence.");
        mockItem.setProposedSentence("Updated sentence.");
        mockItem.setDecision(ReviewDecision.REJECT);
        mockItem.setReviewStatus(ReviewStatus.REJECTED);

        when(reviewService.reviewSentence(any(SentenceReviewRequest.class))).thenReturn(mockItem);

        mockMvc.perform(post("/api/reviews/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentenceId").value(1))
                .andExpect(jsonPath("$.decision").value("REJECT"))
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"))
                .andExpect(jsonPath("$.originalSentence").value("Original sentence."));
    }

    @Test
    @DisplayName("POST /api/reviews/decision - Missing body returns 400")
    void testSubmitSentenceDecisionMissingBody() throws Exception {
        mockMvc.perform(post("/api/reviews/decision")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/reviews/decision - Unapprovable sentence throws ReviewValidationException and returns 400")
    void testSubmitSentenceDecisionUnapprovableThrows400() throws Exception {
        SentenceReviewRequest request = new SentenceReviewRequest("req-1", 1, ReviewDecision.APPROVE);

        when(reviewService.reviewSentence(any(SentenceReviewRequest.class)))
                .thenThrow(new ReviewValidationException("Cannot approve sentence ID 1: sentence does not have an active proposal to review."));

        mockMvc.perform(post("/api/reviews/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Cannot approve sentence ID 1: sentence does not have an active proposal to review."));
    }

    @Test
    @DisplayName("POST /api/reviews/decision - Attempting to approve an already rejected proposal returns 400")
    void testSubmitSentenceDecisionAlreadyRejectedReturns400() throws Exception {
        SentenceReviewRequest request = new SentenceReviewRequest("req-1", 1, ReviewDecision.APPROVE);

        when(reviewService.reviewSentence(any(SentenceReviewRequest.class)))
                .thenThrow(new ReviewValidationException("Cannot modify decision for sentence ID 1: sentence is already REJECTED."));

        mockMvc.perform(post("/api/reviews/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Cannot modify decision for sentence ID 1: sentence is already REJECTED."));
    }

    @Test
    @DisplayName("POST /api/reviews/document - Valid batch decisions returns 200 with DocumentReviewResult")
    void testSubmitDocumentDecisionsSuccess() throws Exception {
        DocumentReviewRequest request = new DocumentReviewRequest(
                "doc-1", List.of(new SentenceDecisionItem(1, ReviewDecision.APPROVE))
        );

        DocumentReviewResult mockResult = new DocumentReviewResult();
        mockResult.setRequestId("doc-1");
        mockResult.setTotalSentences(1);
        mockResult.setApprovedCount(1);
        mockResult.setPendingCount(0);

        when(reviewService.reviewDocument(any(DocumentReviewRequest.class))).thenReturn(mockResult);

        mockMvc.perform(post("/api/reviews/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("doc-1"))
                .andExpect(jsonPath("$.approvedCount").value(1));
    }

    @Test
    @DisplayName("POST /api/reviews/document - Missing body returns 400")
    void testSubmitDocumentDecisionsMissingBody() throws Exception {
        mockMvc.perform(post("/api/reviews/document")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("Security: Client cannot POST fabricated DocumentAnalysisResult to /api/reviews (endpoint rejected)")
    void testClientCannotPostFabricatedReviewSession() throws Exception {
        DocumentAnalysisResult fabricatedResult = new DocumentAnalysisResult(
                "fake-request-id", 1, 1, 1, 1, 1, List.of()
        );

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fabricatedResult)))
                .andExpect(status().is4xxClientError());

        // Verify that reviewService.createReviewSession was NEVER invoked
        verify(reviewService, never()).createReviewSession(any());
    }

    @Test
    @DisplayName("GET /api/reviews/{requestId} - Existing session returns 200")
    void testGetReviewSessionExistingReturns200() throws Exception {
        DocumentReviewResult mockResult = new DocumentReviewResult();
        mockResult.setRequestId("session-1");
        mockResult.setTotalSentences(1);

        when(reviewService.getReviewSession("session-1")).thenReturn(mockResult);

        mockMvc.perform(get("/api/reviews/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("session-1"));
    }

    @Test
    @DisplayName("GET /api/reviews/{requestId} - Unknown session returns 400")
    void testGetReviewSessionUnknownReturns400() throws Exception {
        when(reviewService.getReviewSession("unknown-id"))
                .thenThrow(new ReviewValidationException("Review session not found for requestId: unknown-id"));

        mockMvc.perform(get("/api/reviews/unknown-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Review session not found for requestId: unknown-id"));
    }

    @Test
    @DisplayName("POST /api/reviews/{requestId}/decisions - Valid session decision returns 200")
    void testApplySessionDecisionReturns200() throws Exception {
        SentenceDecisionItem decisionItem = new SentenceDecisionItem(1, ReviewDecision.APPROVE);

        DocumentReviewResult mockResult = new DocumentReviewResult();
        mockResult.setRequestId("session-1");
        mockResult.setApprovedCount(1);

        when(reviewService.applySessionDecision(eq("session-1"), eq(1), eq(ReviewDecision.APPROVE)))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/reviews/session-1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decisionItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("session-1"))
                .andExpect(jsonPath("$.approvedCount").value(1));
    }
}
