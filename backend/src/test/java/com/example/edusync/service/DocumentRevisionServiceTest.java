package com.example.edusync.service;

import com.example.edusync.model.DocumentRevisionItem;
import com.example.edusync.model.DocumentRevisionResult;
import com.example.edusync.model.DocumentReviewResult;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ReviewDecision;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.RevisionStatus;
import com.example.edusync.model.SentenceReviewItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentRevisionServiceTest {

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private DocumentRevisionService documentRevisionService;

    private SentenceReviewItem approvedItem;
    private SentenceReviewItem rejectedItem;
    private SentenceReviewItem pendingItem;
    private SentenceReviewItem notApplicableItem;

    @BeforeEach
    void setUp() {
        approvedItem = new SentenceReviewItem();
        approvedItem.setSentenceId(1);
        approvedItem.setOriginalSentence("Java 17 is latest LTS.");
        approvedItem.setProposedSentence("Java 21 is latest LTS.");
        approvedItem.setProposalStatus(ProposalStatus.PROPOSED);
        approvedItem.setReviewStatus(ReviewStatus.APPROVED);
        approvedItem.setDecision(ReviewDecision.APPROVE);
        approvedItem.setSourceUrl("https://endoflife.date/api/java.json");
        approvedItem.setOfficialReferenceUrl("https://www.oracle.com/java");
        approvedItem.setVerifiedInformation("Java 21 is the current LTS version.");

        rejectedItem = new SentenceReviewItem();
        rejectedItem.setSentenceId(2);
        rejectedItem.setOriginalSentence("Python 2 is used.");
        rejectedItem.setProposedSentence("Python 3 is used.");
        rejectedItem.setProposalStatus(ProposalStatus.PROPOSED);
        rejectedItem.setReviewStatus(ReviewStatus.REJECTED);
        rejectedItem.setDecision(ReviewDecision.REJECT);
        rejectedItem.setSourceUrl("https://endoflife.date/api/python.json");

        pendingItem = new SentenceReviewItem();
        pendingItem.setSentenceId(3);
        pendingItem.setOriginalSentence("Spring Boot 2.5 is current.");
        pendingItem.setProposedSentence("Spring Boot 3.2 is current.");
        pendingItem.setProposalStatus(ProposalStatus.PROPOSED);
        pendingItem.setReviewStatus(ReviewStatus.PENDING);

        notApplicableItem = new SentenceReviewItem();
        notApplicableItem.setSentenceId(4);
        notApplicableItem.setOriginalSentence("Binary search is O(log n).");
        notApplicableItem.setReviewStatus(ReviewStatus.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("1. Approved proposal becomes a revision item")
    void testApprovedProposalBecomesRevisionItem() {
        DocumentReviewResult session = new DocumentReviewResult();
        session.setRequestId("req-1");
        session.setTotalSentences(1);
        session.setItems(List.of(approvedItem));

        when(reviewService.getReviewSession("req-1")).thenReturn(session);

        DocumentRevisionResult result = documentRevisionService.prepareRevision("req-1");

        assertThat(result).isNotNull();
        assertThat(result.getRequestId()).isEqualTo("req-1");
        assertThat(result.getRevisionStatus()).isEqualTo(RevisionStatus.READY);
        assertThat(result.getTotalSentencesConsidered()).isEqualTo(1);
        assertThat(result.getApprovedUpdateCount()).isEqualTo(1);
        assertThat(result.getRevisionItems()).hasSize(1);

        DocumentRevisionItem revItem = result.getRevisionItems().get(0);
        assertThat(revItem.getSentenceId()).isEqualTo(1);
        assertThat(revItem.getOriginalSentence()).isEqualTo("Java 17 is latest LTS.");
        assertThat(revItem.getApprovedSentence()).isEqualTo("Java 21 is latest LTS.");
        assertThat(revItem.getSourceUrl()).isEqualTo("https://endoflife.date/api/java.json");
        assertThat(revItem.getOfficialReferenceUrl()).isEqualTo("https://www.oracle.com/java");
        assertThat(revItem.getHighlightColor()).isEqualTo("YELLOW");
        assertThat(revItem.getRevisionAction()).isEqualTo("REPLACE");
        assertThat(revItem.getCitationNote()).isEqualTo("Java 21 is the current LTS version.");
    }

    @Test
    @DisplayName("2. Rejected proposal is excluded from revision items")
    void testRejectedProposalIsExcluded() {
        DocumentReviewResult session = new DocumentReviewResult();
        session.setRequestId("req-2");
        session.setTotalSentences(2);
        session.setItems(List.of(approvedItem, rejectedItem));

        when(reviewService.getReviewSession("req-2")).thenReturn(session);

        DocumentRevisionResult result = documentRevisionService.prepareRevision("req-2");

        assertThat(result.getRevisionStatus()).isEqualTo(RevisionStatus.READY);
        assertThat(result.getApprovedUpdateCount()).isEqualTo(1);
        assertThat(result.getRevisionItems())
                .extracting(DocumentRevisionItem::getSentenceId)
                .containsExactly(1);
    }

    @Test
    @DisplayName("3. Pending proposal is excluded from revision items")
    void testPendingProposalIsExcluded() {
        DocumentReviewResult session = new DocumentReviewResult();
        session.setRequestId("req-3");
        session.setTotalSentences(2);
        session.setItems(List.of(approvedItem, pendingItem));

        when(reviewService.getReviewSession("req-3")).thenReturn(session);

        DocumentRevisionResult result = documentRevisionService.prepareRevision("req-3");

        assertThat(result.getRevisionStatus()).isEqualTo(RevisionStatus.READY);
        assertThat(result.getApprovedUpdateCount()).isEqualTo(1);
        assertThat(result.getRevisionItems())
                .extracting(DocumentRevisionItem::getSentenceId)
                .containsExactly(1);
    }

    @Test
    @DisplayName("4. NOT_APPLICABLE sentence is excluded from revision items")
    void testNotApplicableSentenceIsExcluded() {
        DocumentReviewResult session = new DocumentReviewResult();
        session.setRequestId("req-4");
        session.setTotalSentences(2);
        session.setItems(List.of(approvedItem, notApplicableItem));

        when(reviewService.getReviewSession("req-4")).thenReturn(session);

        DocumentRevisionResult result = documentRevisionService.prepareRevision("req-4");

        assertThat(result.getRevisionStatus()).isEqualTo(RevisionStatus.READY);
        assertThat(result.getApprovedUpdateCount()).isEqualTo(1);
        assertThat(result.getRevisionItems())
                .extracting(DocumentRevisionItem::getSentenceId)
                .containsExactly(1);
    }

    @Test
    @DisplayName("5. Original sentence remains unchanged and immutable")
    void testOriginalSentenceRemainsUnchangedAndImmutable() {
        DocumentReviewResult session = new DocumentReviewResult();
        session.setRequestId("req-5");
        session.setTotalSentences(1);
        session.setItems(List.of(approvedItem));

        when(reviewService.getReviewSession("req-5")).thenReturn(session);

        DocumentRevisionResult result = documentRevisionService.prepareRevision("req-5");
        DocumentRevisionItem revItem = result.getRevisionItems().get(0);

        assertThat(revItem.getOriginalSentence()).isEqualTo("Java 17 is latest LTS.");

        // Defensive immutability verification: attempting to mutate original sentence throws exception
        assertThatThrownBy(() -> revItem.setOriginalSentence("Mutated original sentence"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    @DisplayName("6. Approved sentence comes strictly from the server-side proposal")
    void testApprovedSentenceComesFromServerSideProposal() {
        DocumentReviewResult session = new DocumentReviewResult();
        session.setRequestId("req-6");
        session.setTotalSentences(1);
        session.setItems(List.of(approvedItem));

        when(reviewService.getReviewSession("req-6")).thenReturn(session);

        DocumentRevisionResult result = documentRevisionService.prepareRevision("req-6");
        DocumentRevisionItem revItem = result.getRevisionItems().get(0);

        assertThat(revItem.getApprovedSentence()).isEqualTo(approvedItem.getProposedSentence());
        assertThat(revItem.getApprovedSentence()).isEqualTo("Java 21 is latest LTS.");
    }

    @Test
    @DisplayName("7. Source information comes strictly from server-side review data")
    void testSourceInformationComesFromServerSideReviewData() {
        DocumentReviewResult session = new DocumentReviewResult();
        session.setRequestId("req-7");
        session.setTotalSentences(1);
        session.setItems(List.of(approvedItem));

        when(reviewService.getReviewSession("req-7")).thenReturn(session);

        DocumentRevisionResult result = documentRevisionService.prepareRevision("req-7");
        DocumentRevisionItem revItem = result.getRevisionItems().get(0);

        assertThat(revItem.getSourceUrl()).isEqualTo("https://endoflife.date/api/java.json");
        assertThat(revItem.getOfficialReferenceUrl()).isEqualTo("https://www.oracle.com/java");
    }

    @Test
    @DisplayName("8. Nonexistent requestId fails safely with ReviewValidationException")
    void testNonexistentRequestIdFailsSafely() {
        when(reviewService.getReviewSession("unknown-req"))
                .thenThrow(new ReviewValidationException("Review session not found for requestId: unknown-req"));

        assertThatThrownBy(() -> documentRevisionService.prepareRevision("unknown-req"))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("Review session not found");
    }

    @Test
    @DisplayName("9. No approved updates returns NO_APPROVED_UPDATES (valid result, not an error)")
    void testNoApprovedUpdatesReturnsNoApprovedUpdates() {
        DocumentReviewResult session = new DocumentReviewResult();
        session.setRequestId("req-9");
        session.setTotalSentences(3);
        session.setItems(List.of(rejectedItem, pendingItem, notApplicableItem));

        when(reviewService.getReviewSession("req-9")).thenReturn(session);

        DocumentRevisionResult result = documentRevisionService.prepareRevision("req-9");

        assertThat(result).isNotNull();
        assertThat(result.getRequestId()).isEqualTo("req-9");
        assertThat(result.getRevisionStatus()).isEqualTo(RevisionStatus.NO_APPROVED_UPDATES);
        assertThat(result.getTotalSentencesConsidered()).isEqualTo(3);
        assertThat(result.getApprovedUpdateCount()).isEqualTo(0);
        assertThat(result.getRevisionItems()).isEmpty();
        assertThat(result.getStatusMessage()).isEqualTo("No approved updates found for revision.");
    }

    @Test
    @DisplayName("10. Client cannot inject revision content - null or blank requestId is rejected")
    void testClientCannotInjectRevisionContentWithInvalidRequestId() {
        assertThatThrownBy(() -> documentRevisionService.prepareRevision(null))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("Request ID cannot be null or empty");

        assertThatThrownBy(() -> documentRevisionService.prepareRevision("   "))
                .isInstanceOf(ReviewValidationException.class)
                .hasMessageContaining("Request ID cannot be null or empty");
    }

    @Test
    @DisplayName("11. Architecture isolation: DocumentRevisionService must NOT directly depend on forbidden pipeline services")
    void testArchitectureIsolationDocumentRevisionService() {
        // Confirm DocumentRevisionService has only ReviewService declared as a service dependency
        List<Class<?>> declaredFieldTypes = Arrays.stream(DocumentRevisionService.class.getDeclaredFields())
                .<Class<?>>map(Field::getType)
                .toList();

        assertThat(declaredFieldTypes)
                .as("DocumentRevisionService must declare ReviewService and not low-level or orchestrator services")
                .contains(ReviewService.class)
                .doesNotContain(
                        GeminiAnalysisService.class,
                        SourceVerificationService.class,
                        TextExtractionService.class,
                        SentenceSegmentationService.class,
                        DocumentAnalysisOrchestrator.class,
                        DocumentProcessingService.class
                );

        // Confirm constructor parameters only accept ReviewService
        boolean hasForbiddenConstructorParam = Arrays.stream(DocumentRevisionService.class.getConstructors())
                .flatMap(c -> Arrays.stream(c.getParameterTypes()))
                .anyMatch(paramType -> paramType.equals(GeminiAnalysisService.class)
                        || paramType.equals(SourceVerificationService.class)
                        || paramType.equals(TextExtractionService.class)
                        || paramType.equals(SentenceSegmentationService.class)
                        || paramType.equals(DocumentAnalysisOrchestrator.class)
                        || paramType.equals(DocumentProcessingService.class));

        assertThat(hasForbiddenConstructorParam)
                .as("DocumentRevisionService constructor must not accept any forbidden pipeline service")
                .isFalse();
    }
}
