package com.example.edusync.controller;

import com.example.edusync.model.DocumentRevisionItem;
import com.example.edusync.model.DocumentRevisionResult;
import com.example.edusync.model.RevisionStatus;
import com.example.edusync.service.DocumentRevisionService;
import com.example.edusync.service.ReviewValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RevisionController.class)
@Import(GlobalExceptionHandler.class)
class RevisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentRevisionService documentRevisionService;

    @Test
    @DisplayName("POST /api/revisions/{requestId}/prepare - Valid requestId with approved updates returns 200 OK with READY status")
    void testPrepareRevisionReadyReturns200() throws Exception {
        DocumentRevisionItem revItem = new DocumentRevisionItem(
                1,
                "Java 17 is latest LTS.",
                "Java 21 is latest LTS.",
                "https://endoflife.date/api/java.json",
                "https://www.oracle.com/java",
                "YELLOW",
                "REPLACE",
                "Java 21 is the current LTS version."
        );

        DocumentRevisionResult mockResult = DocumentRevisionResult.ready("req-123", 1, List.of(revItem));

        when(documentRevisionService.prepareRevision(eq("req-123"))).thenReturn(mockResult);

        mockMvc.perform(post("/api/revisions/req-123/prepare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-123"))
                .andExpect(jsonPath("$.revisionStatus").value("READY"))
                .andExpect(jsonPath("$.totalSentencesConsidered").value(1))
                .andExpect(jsonPath("$.approvedUpdateCount").value(1))
                .andExpect(jsonPath("$.revisionItems[0].sentenceId").value(1))
                .andExpect(jsonPath("$.revisionItems[0].originalSentence").value("Java 17 is latest LTS."))
                .andExpect(jsonPath("$.revisionItems[0].approvedSentence").value("Java 21 is latest LTS."))
                .andExpect(jsonPath("$.revisionItems[0].highlightColor").value("YELLOW"))
                .andExpect(jsonPath("$.revisionItems[0].revisionAction").value("REPLACE"))
                .andExpect(jsonPath("$.revisionItems[0].sourceUrl").value("https://endoflife.date/api/java.json"))
                .andExpect(jsonPath("$.revisionItems[0].officialReferenceUrl").value("https://www.oracle.com/java"));

        verify(documentRevisionService).prepareRevision("req-123");
    }

    @Test
    @DisplayName("POST /api/revisions/{requestId}/prepare - No approved updates returns 200 OK with NO_APPROVED_UPDATES")
    void testPrepareRevisionNoApprovedUpdatesReturns200() throws Exception {
        DocumentRevisionResult mockResult = DocumentRevisionResult.noApprovedUpdates("req-no-updates", 3);

        when(documentRevisionService.prepareRevision(eq("req-no-updates"))).thenReturn(mockResult);

        mockMvc.perform(post("/api/revisions/req-no-updates/prepare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-no-updates"))
                .andExpect(jsonPath("$.revisionStatus").value("NO_APPROVED_UPDATES"))
                .andExpect(jsonPath("$.totalSentencesConsidered").value(3))
                .andExpect(jsonPath("$.approvedUpdateCount").value(0))
                .andExpect(jsonPath("$.revisionItems").isEmpty())
                .andExpect(jsonPath("$.statusMessage").value("No approved updates found for revision."));

        verify(documentRevisionService).prepareRevision("req-no-updates");
    }

    @Test
    @DisplayName("POST /api/revisions/{requestId}/prepare - Nonexistent requestId returns 400 Bad Request")
    void testPrepareRevisionUnknownRequestIdReturns400() throws Exception {
        when(documentRevisionService.prepareRevision(eq("unknown-id")))
                .thenThrow(new ReviewValidationException("Review session not found for requestId: unknown-id"));

        mockMvc.perform(post("/api/revisions/unknown-id/prepare"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Review session not found for requestId: unknown-id"));
    }

    @Test
    @DisplayName("Security: Endpoint accepts only requestId path variable and ignores/does not bind any client-supplied body")
    void testSecurityIgnoresClientSuppliedBodyAndUsesOnlyServerSession() throws Exception {
        DocumentRevisionResult mockResult = DocumentRevisionResult.noApprovedUpdates("req-sec", 1);
        when(documentRevisionService.prepareRevision(eq("req-sec"))).thenReturn(mockResult);

        // Attempting to send fabricated revision/analysis data in the request body
        String fakePayload = "{\"originalSentence\":\"fake\",\"approvedSentence\":\"fake injection\"}";

        mockMvc.perform(post("/api/revisions/req-sec/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fakePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-sec"));

        // Confirm that the service was called ONLY with the path variable requestId
        verify(documentRevisionService).prepareRevision("req-sec");
    }
}
