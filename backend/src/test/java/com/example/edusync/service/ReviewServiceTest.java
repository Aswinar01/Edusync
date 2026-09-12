package com.example.edusync.service;

import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentReviewRequest;
import com.example.edusync.model.DocumentReviewResult;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ProposedUpdate;
import com.example.edusync.model.ReviewDecision;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.SentenceAnalysisResult;
import com.example.edusync.model.SentenceDecisionItem;
import com.example.edusync.model.SentencePipelineResult;
import com.example.edusync.model.SentenceReviewItem;
import com.example.edusync.model.SentenceReviewRequest;
import com.example.edusync.model.SourceType;
import com.example.edusync.model.SourceVerificationResult;
import com.example.edusync.model.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewServiceTest {

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService();
    }

    private SentencePipelineResult createValidProposedSentence(int id, String original, String proposed) {
        SentenceAnalysisResult analysis = SentenceAnalysisResult.success(id, original, true, "Outdated fact");
        SourceVerificationResult verification = SourceVerificationResult.verified(
                id, original, "Java 21 released Sept 2023", "Lifecycle Registry",
                "https://endoflife.date/api/java.json", "https://oracle.com/java", SourceType.REPUTABLE
        );
        ProposedUpdate update = ProposedUpdate.proposed(
                id, original, proposed,
                "Java 21 is latest LTS",
                "Java 21 released Sept 2023",
                "Lifecycle Registry",
                "https://endoflife.date/api/java.json",
                "https://oracle.com/java",
                SourceType.REPUTABLE
        );

        return new SentencePipelineResult(id, original, PipelineStatus.VERIFIED_UPDATE_PROPOSED, analysis, verification, update);
    }

    private void initializeServerSession(String requestId, SentencePipelineResult... sentences) {
        DocumentAnalysisResult analysisResult = new DocumentAnalysisResult(
                requestId, sentences.length, sentences.length, 1, 1, 1, Arrays.asList(sentences)
        );
        reviewService.createReviewSession(analysisResult);
    }

    @Test
    @DisplayName("1. Approve valid proposal successfully sets decision=APPROVE and status=APPROVED")
    void testApproveValidProposalSuccess() {
        SentencePipelineResult sentenceResult = createValidProposedSentence(
                1, "Java 17 is latest LTS.", "Java 21 is latest LTS.");
        initializeServerSession("req-1", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-1", 1, ReviewDecision.APPROVE);

        SentenceReviewItem result = reviewService.reviewSentence(request);

        assertThat(result).isNotNull();
        assertThat(result.getSentenceId()).isEqualTo(1);
        assertThat(result.getDecision()).isEqualTo(ReviewDecision.APPROVE);
        assertThat(result.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(result.getOriginalSentence()).isEqualTo("Java 17 is latest LTS.");
        assertThat(result.getProposedSentence()).isEqualTo("Java 21 is latest LTS.");
        assertThat(result.getReviewedAt()).isNotBlank();
        assertThat(result.getSourceUrl()).isEqualTo("https://endoflife.date/api/java.json");
        assertThat(result.getOfficialReferenceUrl()).isEqualTo("https://oracle.com/java");
    }

    @Test
    @DisplayName("2. Reject valid proposal successfully sets decision=REJECT and status=REJECTED")
    void testRejectValidProposalSuccess() {
        SentencePipelineResult sentenceResult = createValidProposedSentence(
                1, "Java 17 is latest LTS.", "Java 21 is latest LTS.");
        initializeServerSession("req-2", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-2", 1, ReviewDecision.REJECT);

        SentenceReviewItem result = reviewService.reviewSentence(request);

        assertThat(result).isNotNull();
        assertThat(result.getSentenceId()).isEqualTo(1);
        assertThat(result.getDecision()).isEqualTo(ReviewDecision.REJECT);
        assertThat(result.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(result.getOriginalSentence()).isEqualTo("Java 17 is latest LTS.");
        assertThat(result.getReviewedAt()).isNotBlank();
    }

    @Test
    @DisplayName("3. Original sentence remains strictly unchanged and unmutated after approval")
    void testOriginalSentenceRemainsUnchangedAfterApproval() {
        String originalText = "Original sentence about Python 2.7.";
        SentencePipelineResult sentenceResult = createValidProposedSentence(
                2, originalText, "Updated sentence about Python 3.12.");
        initializeServerSession("req-3", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-3", 2, ReviewDecision.APPROVE);

        SentenceReviewItem result = reviewService.reviewSentence(request);

        assertThat(result.getOriginalSentence()).isEqualTo(originalText);
        assertThat(result.getOriginalSentence()).isNotEqualTo(result.getProposedSentence());
    }

    @Test
    @DisplayName("4. Original sentence remains strictly unchanged and unmutated after rejection")
    void testOriginalSentenceRemainsUnchangedAfterRejection() {
        String originalText = "Original sentence about Spring Boot 2.5.";
        SentencePipelineResult sentenceResult = createValidProposedSentence(
                3, originalText, "Updated sentence about Spring Boot 3.2.");
        initializeServerSession("req-4", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-4", 3, ReviewDecision.REJECT);

        SentenceReviewItem result = reviewService.reviewSentence(request);

        assertThat(result.getOriginalSentence()).isEqualTo(originalText);
    }

    @Test
    @DisplayName("5. Cannot approve sentence with NO_UPDATE status")
    void testCannotApproveNoUpdate() {
        SentenceAnalysisResult analysis = SentenceAnalysisResult.success(1, "Current text.", true, "Check latest version");
        SourceVerificationResult verification = SourceVerificationResult.verified(
                1, "Current text.", "Already current", "Registry", "https://source.com", null, SourceType.REPUTABLE);
        ProposedUpdate noUpdate = ProposedUpdate.noUpdate(1, "Current text.", "No update needed", "Already current", "Registry", "https://source.com", null, SourceType.REPUTABLE);

        SentencePipelineResult sentenceResult = new SentencePipelineResult(
                1, "Current text.", PipelineStatus.VERIFIED_NO_UPDATE, analysis, verification, noUpdate);
        initializeServerSession("req-5", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-5", 1, ReviewDecision.APPROVE);

        assertThatThrownBy(() -> reviewService.reviewSentence(request))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("NO_UPDATE");
    }

    @Test
    @DisplayName("6. Cannot approve unverified sentence (POTENTIALLY_OUTDATED_UNVERIFIED)")
    void testCannotApproveUnverifiedSentence() {
        SentenceAnalysisResult analysis = SentenceAnalysisResult.success(1, "Unverified text.", true, "Potentially outdated");
        SourceVerificationResult verification = SourceVerificationResult.notVerified(
                1, "Unverified text.", "Not verified", "Registry", "https://source.com", null, SourceType.REPUTABLE);

        SentencePipelineResult sentenceResult = new SentencePipelineResult(
                1, "Unverified text.", PipelineStatus.POTENTIALLY_OUTDATED_UNVERIFIED, analysis, verification, null);
        initializeServerSession("req-6", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-6", 1, ReviewDecision.APPROVE);

        assertThatThrownBy(() -> reviewService.reviewSentence(request))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("POTENTIALLY_OUTDATED_UNVERIFIED");
    }

    @Test
    @DisplayName("7. Cannot approve sentence with INSUFFICIENT_EVIDENCE")
    void testCannotApproveInsufficientEvidence() {
        SentenceAnalysisResult analysis = SentenceAnalysisResult.success(1, "Vague text.", true, "Outdated");
        SourceVerificationResult verification = SourceVerificationResult.verified(
                1, "Vague text.", "Vague info", "Registry", "https://source.com", null, SourceType.REPUTABLE);
        ProposedUpdate update = ProposedUpdate.insufficientEvidence(1, "Vague text.", "Insufficient evidence", "Vague info", "Registry", "https://source.com", null, SourceType.REPUTABLE);

        SentencePipelineResult sentenceResult = new SentencePipelineResult(
                1, "Vague text.", PipelineStatus.VERIFIED_INSUFFICIENT_EVIDENCE, analysis, verification, update);
        initializeServerSession("req-7", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-7", 1, ReviewDecision.APPROVE);

        assertThatThrownBy(() -> reviewService.reviewSentence(request))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("INSUFFICIENT_EVIDENCE");
    }

    @Test
    @DisplayName("8. Cannot approve sentence with GENERATION_FAILED proposal")
    void testCannotApproveGenerationFailed() {
        SentenceAnalysisResult analysis = SentenceAnalysisResult.success(1, "Text.", true, "Outdated");
        SourceVerificationResult verification = SourceVerificationResult.verified(
                1, "Text.", "Valid evidence", "Registry", "https://source.com", null, SourceType.REPUTABLE);
        ProposedUpdate update = ProposedUpdate.failed(1, "Text.", "Gemini 500 error");

        SentencePipelineResult sentenceResult = new SentencePipelineResult(
                1, "Text.", PipelineStatus.VERIFIED_UPDATE_PROPOSED, analysis, verification, update);
        initializeServerSession("req-8", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-8", 1, ReviewDecision.APPROVE);

        assertThatThrownBy(() -> reviewService.reviewSentence(request))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("GENERATION_FAILED");
    }

    @Test
    @DisplayName("9. Cannot approve UNCHANGED sentence")
    void testCannotApproveUnchangedSentence() {
        SentencePipelineResult sentenceResult = new SentencePipelineResult(
                1, "Binary search is O(log n).", PipelineStatus.UNCHANGED, null, null, null);
        initializeServerSession("req-9", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-9", 1, ReviewDecision.APPROVE);

        assertThatThrownBy(() -> reviewService.reviewSentence(request))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("sentence is unchanged");
    }

    @Test
    @DisplayName("10. Cannot approve sentence when SOURCE_UNAVAILABLE")
    void testCannotApproveSourceUnavailable() {
        SentenceAnalysisResult analysis = SentenceAnalysisResult.success(1, "Text.", true, "Outdated");
        SourceVerificationResult verification = SourceVerificationResult.sourceUnavailable(
                1, "Text.", "Timeout", "https://endoflife.date/api/java.json");

        SentencePipelineResult sentenceResult = new SentencePipelineResult(
                1, "Text.", PipelineStatus.SOURCE_UNAVAILABLE, analysis, verification, null);
        initializeServerSession("req-10", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-10", 1, ReviewDecision.APPROVE);

        assertThatThrownBy(() -> reviewService.reviewSentence(request))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("SOURCE_UNAVAILABLE");
    }

    @Test
    @DisplayName("11. Cannot reject sentence that has no proposed update to reject")
    void testCannotRejectSentenceWithoutProposal() {
        SentencePipelineResult sentenceResult = new SentencePipelineResult(
                1, "Timeless fact.", PipelineStatus.UNCHANGED, null, null, null);
        initializeServerSession("req-11", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-11", 1, ReviewDecision.REJECT);

        assertThatThrownBy(() -> reviewService.reviewSentence(request))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("sentence does not have an active proposal to reject");
    }

    @Test
    @DisplayName("12. Invalid sentence IDs throw ReviewValidationException")
    void testInvalidSentenceIdThrowsException() {
        SentencePipelineResult sentenceResult = createValidProposedSentence(1, "Text.", "Updated text.");
        initializeServerSession("req-12", sentenceResult);

        // Non-positive sentence ID in request
        SentenceReviewRequest nonPositiveRequest = new SentenceReviewRequest("req-12", -5, ReviewDecision.APPROVE);
        assertThatThrownBy(() -> reviewService.reviewSentence(nonPositiveRequest))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("positive integer");

        // Sentence ID not present in session
        SentenceReviewRequest notFoundRequest = new SentenceReviewRequest("req-12", 99, ReviewDecision.APPROVE);
        assertThatThrownBy(() -> reviewService.reviewSentence(notFoundRequest))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("13. Missing decision throws ReviewValidationException")
    void testMissingDecisionThrowsException() {
        SentencePipelineResult sentenceResult = createValidProposedSentence(1, "Text.", "Updated text.");
        initializeServerSession("req-13", sentenceResult);

        SentenceReviewRequest request = new SentenceReviewRequest("req-13", 1, null);

        assertThatThrownBy(() -> reviewService.reviewSentence(request))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("Review decision cannot be null");
    }

    @Test
    @DisplayName("14. Missing request data throws ReviewValidationException")
    void testMissingRequestDataThrowsException() {
        assertThatThrownBy(() -> reviewService.reviewSentence(null))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("cannot be null");

        SentenceReviewRequest nullRequestIdRequest = new SentenceReviewRequest(null, 1, ReviewDecision.APPROVE);
        assertThatThrownBy(() -> reviewService.reviewSentence(nullRequestIdRequest))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("Request ID cannot be null or empty");
    }

    @Test
    @DisplayName("15. Document review batch applies decisions and recalculates metrics")
    void testDocumentReviewBatchSuccess() {
        SentencePipelineResult s1 = createValidProposedSentence(1, "Java 17 is latest.", "Java 21 is latest.");
        SentencePipelineResult s2 = createValidProposedSentence(2, "Python 2.7 is active.", "Python 3.12 is active.");
        SentencePipelineResult s3 = new SentencePipelineResult(3, "Binary search is O(log n).", PipelineStatus.UNCHANGED, null, null, null);

        initializeServerSession("req-doc", s1, s2, s3);

        DocumentReviewRequest request = new DocumentReviewRequest(
                "req-doc",
                List.of(
                        new SentenceDecisionItem(1, ReviewDecision.APPROVE),
                        new SentenceDecisionItem(2, ReviewDecision.REJECT)
                )
        );

        DocumentReviewResult result = reviewService.reviewDocument(request);

        assertThat(result).isNotNull();
        assertThat(result.getRequestId()).isEqualTo("req-doc");
        assertThat(result.getTotalSentences()).isEqualTo(3);
        assertThat(result.getReviewableCount()).isEqualTo(2);
        assertThat(result.getApprovedCount()).isEqualTo(1);
        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(result.getPendingCount()).isEqualTo(0);

        SentenceReviewItem item1 = result.getItems().get(0);
        assertThat(item1.getSentenceId()).isEqualTo(1);
        assertThat(item1.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(item1.getDecision()).isEqualTo(ReviewDecision.APPROVE);
        assertThat(item1.getOriginalSentence()).isEqualTo("Java 17 is latest.");

        SentenceReviewItem item2 = result.getItems().get(1);
        assertThat(item2.getSentenceId()).isEqualTo(2);
        assertThat(item2.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(item2.getDecision()).isEqualTo(ReviewDecision.REJECT);
        assertThat(item2.getOriginalSentence()).isEqualTo("Python 2.7 is active.");

        SentenceReviewItem item3 = result.getItems().get(2);
        assertThat(item3.getSentenceId()).isEqualTo(3);
        assertThat(item3.getReviewStatus()).isEqualTo(ReviewStatus.NOT_APPLICABLE);
        assertThat(item3.getDecision()).isNull();
    }

    @Test
    @DisplayName("16. Document review with sentence not found throws ReviewValidationException")
    void testDocumentReviewSentenceNotFoundThrowsException() {
        SentencePipelineResult s1 = createValidProposedSentence(1, "Text.", "Updated.");
        initializeServerSession("req-doc-err", s1);

        DocumentReviewRequest request = new DocumentReviewRequest(
                "req-doc-err",
                List.of(new SentenceDecisionItem(999, ReviewDecision.APPROVE))
        );

        assertThatThrownBy(() -> reviewService.reviewDocument(request))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("Sentence ID 999 not found in review session: req-doc-err");
    }

    @Test
    @DisplayName("17. In-memory review session lifecycle (create -> fetch -> apply decision)")
    void testInMemorySessionLifecycle() {
        SentencePipelineResult s1 = createValidProposedSentence(1, "Text.", "Updated.");
        DocumentAnalysisResult analysisResult = new DocumentAnalysisResult(
                "session-123", 1, 1, 1, 1, 1, List.of(s1));

        // 1. Create session
        DocumentReviewResult created = reviewService.createReviewSession(analysisResult);
        assertThat(created.getRequestId()).isEqualTo("session-123");
        assertThat(created.getPendingCount()).isEqualTo(1);

        // 2. Fetch session
        DocumentReviewResult fetched = reviewService.getReviewSession("session-123");
        assertThat(fetched).isNotNull();
        assertThat(fetched.getRequestId()).isEqualTo("session-123");

        // 3. Apply decision
        DocumentReviewResult updated = reviewService.applySessionDecision("session-123", 1, ReviewDecision.APPROVE);
        assertThat(updated.getApprovedCount()).isEqualTo(1);
        assertThat(updated.getPendingCount()).isEqualTo(0);

        // 4. Non-existent session throws
        assertThatThrownBy(() -> reviewService.getReviewSession("non-existent"))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("Review session not found");
    }

    @Test
    @DisplayName("18. Architecture Isolation: ReviewService does not declare dependencies on other pipeline services")
    void testArchitectureIsolation() {
        List<Class<?>> declaredFieldTypes = Arrays.stream(ReviewService.class.getDeclaredFields())
                .<Class<?>>map(Field::getType)
                .toList();

        assertThat(declaredFieldTypes)
                .as("ReviewService must not declare direct dependencies on AI, verification, or extraction services")
                .doesNotContain(
                        GeminiAnalysisService.class,
                        SourceVerificationService.class,
                        ProposedUpdateService.class,
                        DocumentProcessingService.class,
                        DocumentAnalysisOrchestrator.class
                );

        boolean hasForbiddenConstructorParam = Arrays.stream(ReviewService.class.getConstructors())
                .flatMap(c -> Arrays.stream(c.getParameterTypes()))
                .anyMatch(paramType -> paramType.equals(GeminiAnalysisService.class)
                        || paramType.equals(SourceVerificationService.class)
                        || paramType.equals(ProposedUpdateService.class)
                        || paramType.equals(DocumentProcessingService.class)
                        || paramType.equals(DocumentAnalysisOrchestrator.class));

        assertThat(hasForbiddenConstructorParam)
                .as("ReviewService constructor must not accept pipeline services")
                .isFalse();
    }

    // =========================================================================
    // SECURITY TESTS
    // =========================================================================

    @Test
    @DisplayName("19. Security: Client cannot fabricate a verified proposal and approve it")
    void testClientCannotFabricateVerifiedProposalAndApproveIt() {
        // Scenario A: Client submits decision with a non-existent requestId
        SentenceReviewRequest fakeRequest = new SentenceReviewRequest("fake-request-id", 1, ReviewDecision.APPROVE);
        assertThatThrownBy(() -> reviewService.reviewSentence(fakeRequest))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("Review session not found for requestId: fake-request-id");

        // Scenario B: Server session has UNVERIFIED sentence; client tries to approve it
        SentenceAnalysisResult analysis = SentenceAnalysisResult.success(1, "Unverified fact.", true, "Check fact");
        SourceVerificationResult unverified = SourceVerificationResult.notVerified(
                1, "Unverified fact.", "No evidence", "Registry", "https://source.com", null, SourceType.REPUTABLE);
        SentencePipelineResult serverSentence = new SentencePipelineResult(
                1, "Unverified fact.", PipelineStatus.POTENTIALLY_OUTDATED_UNVERIFIED, analysis, unverified, null);

        initializeServerSession("req-sec-1", serverSentence);

        SentenceReviewRequest unverifiedApproval = new SentenceReviewRequest("req-sec-1", 1, ReviewDecision.APPROVE);
        assertThatThrownBy(() -> reviewService.reviewSentence(unverifiedApproval))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("POTENTIALLY_OUTDATED_UNVERIFIED");
    }

    @Test
    @DisplayName("20. Security: Client cannot replace original sentence with different content")
    void testClientCannotReplaceOriginalSentenceWithDifferentContent() {
        String trueOriginalText = "Authentic original sentence from document extraction.";
        SentencePipelineResult serverSentence = createValidProposedSentence(
                1, trueOriginalText, "Proposed replacement sentence.");

        initializeServerSession("req-sec-2", serverSentence);

        // Client request only has (requestId, sentenceId, decision) - no field to modify originalSentence
        SentenceReviewRequest request = new SentenceReviewRequest("req-sec-2", 1, ReviewDecision.APPROVE);
        SentenceReviewItem result = reviewService.reviewSentence(request);

        // Verify the original sentence returned and in the session is strictly the authentic text
        assertThat(result.getOriginalSentence()).isEqualTo(trueOriginalText);

        DocumentReviewResult session = reviewService.getReviewSession("req-sec-2");
        assertThat(session.getItems().get(0).getOriginalSentence()).isEqualTo(trueOriginalText);
    }

    @Test
    @DisplayName("21. Security: Client cannot replace verification or source information")
    void testClientCannotReplaceVerificationOrSourceInformation() {
        String authenticSourceUrl = "https://endoflife.date/api/java.json";
        String authenticOfficialUrl = "https://oracle.com/java";
        String authenticVerifiedInfo = "Java 21 released Sept 2023";

        SentencePipelineResult serverSentence = createValidProposedSentence(
                1, "Java 17 is LTS.", "Java 21 is LTS.");

        initializeServerSession("req-sec-3", serverSentence);

        SentenceReviewRequest request = new SentenceReviewRequest("req-sec-3", 1, ReviewDecision.APPROVE);
        SentenceReviewItem result = reviewService.reviewSentence(request);

        // Verification metadata remains authentic and unaltered
        assertThat(result.getSourceUrl()).isEqualTo(authenticSourceUrl);
        assertThat(result.getOfficialReferenceUrl()).isEqualTo(authenticOfficialUrl);
        assertThat(result.getVerifiedInformation()).isEqualTo(authenticVerifiedInfo);
    }

    @Test
    @DisplayName("22. Security: Client cannot approve an already rejected proposal (strict state transition)")
    void testClientCannotApproveAlreadyRejectedProposal() {
        SentencePipelineResult serverSentence = createValidProposedSentence(
                1, "Java 17 is latest.", "Java 21 is latest.");
        initializeServerSession("req-sec-4", serverSentence);

        // Step 1: Reject the proposal (PENDING -> REJECTED)
        SentenceReviewRequest rejectReq = new SentenceReviewRequest("req-sec-4", 1, ReviewDecision.REJECT);
        SentenceReviewItem rejectedItem = reviewService.reviewSentence(rejectReq);
        assertThat(rejectedItem.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);

        // Step 2: Attempt to approve the already rejected proposal
        SentenceReviewRequest approveReq = new SentenceReviewRequest("req-sec-4", 1, ReviewDecision.APPROVE);
        assertThatThrownBy(() -> reviewService.reviewSentence(approveReq))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("sentence is already REJECTED");
    }

    @Test
    @DisplayName("23. Security: Client cannot reject an already approved proposal (strict state transition)")
    void testClientCannotRejectAlreadyApprovedProposal() {
        SentencePipelineResult serverSentence = createValidProposedSentence(
                1, "Java 17 is latest.", "Java 21 is latest.");
        initializeServerSession("req-sec-5", serverSentence);

        // Step 1: Approve the proposal (PENDING -> APPROVED)
        SentenceReviewRequest approveReq = new SentenceReviewRequest("req-sec-5", 1, ReviewDecision.APPROVE);
        SentenceReviewItem approvedItem = reviewService.reviewSentence(approveReq);
        assertThat(approvedItem.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);

        // Step 2: Attempt to reject the already approved proposal
        SentenceReviewRequest rejectReq = new SentenceReviewRequest("req-sec-5", 1, ReviewDecision.REJECT);
        assertThatThrownBy(() -> reviewService.reviewSentence(rejectReq))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("sentence is already APPROVED");
    }

    @Test
    @DisplayName("24. Security: Valid server-created sessions still allow both APPROVE and REJECT")
    void testValidServerCreatedSessionAllowsApproveAndReject() {
        SentencePipelineResult s1 = createValidProposedSentence(1, "Sentence 1.", "Updated 1.");
        SentencePipelineResult s2 = createValidProposedSentence(2, "Sentence 2.", "Updated 2.");

        initializeServerSession("req-sec-6", s1, s2);

        // Approve sentence 1
        SentenceReviewItem item1 = reviewService.reviewSentence(
                new SentenceReviewRequest("req-sec-6", 1, ReviewDecision.APPROVE));
        assertThat(item1.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);

        // Reject sentence 2
        SentenceReviewItem item2 = reviewService.reviewSentence(
                new SentenceReviewRequest("req-sec-6", 2, ReviewDecision.REJECT));
        assertThat(item2.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);

        // Verify session metrics
        DocumentReviewResult session = reviewService.getReviewSession("req-sec-6");
        assertThat(session.getApprovedCount()).isEqualTo(1);
        assertThat(session.getRejectedCount()).isEqualTo(1);
        assertThat(session.getPendingCount()).isEqualTo(0);
    }
}
