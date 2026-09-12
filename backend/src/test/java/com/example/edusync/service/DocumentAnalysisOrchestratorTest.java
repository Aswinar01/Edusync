package com.example.edusync.service;

import com.example.edusync.model.DocumentAnalysisRequest;
import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ProposedUpdate;
import com.example.edusync.model.SentenceAnalysisRequest;
import com.example.edusync.model.SentenceAnalysisResult;
import com.example.edusync.model.SentencePipelineResult;
import com.example.edusync.model.SourceType;
import com.example.edusync.model.SourceVerificationRequest;
import com.example.edusync.model.SourceVerificationResult;
import com.example.edusync.model.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAnalysisOrchestratorTest {

    @Mock
    private GeminiAnalysisService geminiAnalysisService;

    @Mock
    private SourceVerificationService sourceVerificationService;

    @Mock
    private ProposedUpdateService proposedUpdateService;

    @InjectMocks
    private DocumentAnalysisOrchestrator orchestrator;

    private DocumentSentence sentence1;
    private DocumentSentence sentence2;
    private DocumentSentence sentence3;

    @BeforeEach
    void setUp() {
        sentence1 = new DocumentSentence(1, "Java 17 is the latest LTS version of Java.");
        sentence2 = new DocumentSentence(2, "QuickSort has average time complexity O(n log n).");
        sentence3 = new DocumentSentence(3, "Python 3.8 is the current stable release.");
    }

    @Test
    @DisplayName("1. Verification Gate 1: Non-outdated sentence does not trigger source verification")
    void testNonOutdatedSentenceBypassesVerification() {
        when(geminiAnalysisService.analyzeSentence(argThat((SentenceAnalysisRequest req) -> req != null && req.getSentenceId() == 2)))
                .thenReturn(SentenceAnalysisResult.success(2, sentence2.getSentenceText(), false, "Timeless algorithmic property."));

        DocumentAnalysisRequest request = new DocumentAnalysisRequest(List.of(sentence2));
        DocumentAnalysisResult result = orchestrator.analyzeDocument(request);

        assertThat(result.getTotalSentences()).isEqualTo(1);
        assertThat(result.getAnalyzedSentences()).isEqualTo(1);
        assertThat(result.getPotentiallyOutdatedCount()).isEqualTo(0);
        assertThat(result.getVerifiedCount()).isEqualTo(0);
        assertThat(result.getProposalCount()).isEqualTo(0);

        SentencePipelineResult itemResult = result.getResults().get(0);
        assertThat(itemResult.getStatus()).isEqualTo(PipelineStatus.UNCHANGED);
        assertThat(itemResult.getVerification()).isNull();
        assertThat(itemResult.getProposedUpdate()).isNull();

        // Source verification and proposal generation must NEVER be called
        verify(sourceVerificationService, never()).verifySource(any());
        verify(proposedUpdateService, never()).generateProposal(any(), any(), any());
    }

    @Test
    @DisplayName("2. Verification Gate 2: Unverified source does not trigger proposal generator")
    void testUnverifiedSourceBypassesProposal() {
        when(geminiAnalysisService.analyzeSentence(argThat((SentenceAnalysisRequest req) -> req != null && req.getSentenceId() == 1)))
                .thenReturn(SentenceAnalysisResult.success(1, sentence1.getSentenceText(), true, "Newer LTS versions exist."));

        when(sourceVerificationService.verifySource(argThat(req -> req != null && req.getSentenceId() == 1)))
                .thenReturn(SourceVerificationResult.notVerified(
                        1, sentence1.getSentenceText(), "Source could not confirm claim.", "Source", "url", null, SourceType.UNKNOWN
                ));

        DocumentAnalysisRequest request = new DocumentAnalysisRequest(List.of(sentence1));
        DocumentAnalysisResult result = orchestrator.analyzeDocument(request);

        assertThat(result.getPotentiallyOutdatedCount()).isEqualTo(1);
        assertThat(result.getVerifiedCount()).isEqualTo(0);
        assertThat(result.getProposalCount()).isEqualTo(0);

        SentencePipelineResult itemResult = result.getResults().get(0);
        assertThat(itemResult.getStatus()).isEqualTo(PipelineStatus.POTENTIALLY_OUTDATED_UNVERIFIED);
        assertThat(itemResult.getVerification()).isNotNull();
        assertThat(itemResult.getProposedUpdate()).isNull();

        // Proposal generation must NEVER be called
        verify(proposedUpdateService, never()).generateProposal(any(), any(), any());
    }

    @Test
    @DisplayName("3. Verified source triggers proposed update generation")
    void testVerifiedSourceTriggersProposal() {
        when(geminiAnalysisService.analyzeSentence(argThat((SentenceAnalysisRequest req) -> req != null && req.getSentenceId() == 1)))
                .thenReturn(SentenceAnalysisResult.success(1, sentence1.getSentenceText(), true, "Newer LTS versions exist."));

        SourceVerificationResult mockVerification = SourceVerificationResult.verified(
                1, sentence1.getSentenceText(), "Latest version is 21.", "Java Lifecycle", "https://endoflife.date/api/java.json",
                "https://oracle.com/java", SourceType.REPUTABLE
        );
        when(sourceVerificationService.verifySource(argThat(req -> req != null && req.getSentenceId() == 1)))
                .thenReturn(mockVerification);

        ProposedUpdate mockProposal = ProposedUpdate.proposed(
                1, sentence1.getSentenceText(), "Java 21 is the latest LTS version of Java.", "Updated to 21.",
                "Latest version is 21.", "Java Lifecycle", "https://endoflife.date/api/java.json", "https://oracle.com/java", SourceType.REPUTABLE
        );
        when(proposedUpdateService.generateProposal(any(), any(), any()))
                .thenReturn(mockProposal);

        DocumentAnalysisRequest request = new DocumentAnalysisRequest(List.of(sentence1));
        DocumentAnalysisResult result = orchestrator.analyzeDocument(request);

        assertThat(result.getPotentiallyOutdatedCount()).isEqualTo(1);
        assertThat(result.getVerifiedCount()).isEqualTo(1);
        assertThat(result.getProposalCount()).isEqualTo(1);

        SentencePipelineResult itemResult = result.getResults().get(0);
        assertThat(itemResult.getStatus()).isEqualTo(PipelineStatus.VERIFIED_UPDATE_PROPOSED);
        assertThat(itemResult.getProposedUpdate()).isNotNull();
        assertThat(itemResult.getProposedUpdate().getProposedSentence()).isEqualTo("Java 21 is the latest LTS version of Java.");
    }

    @Test
    @DisplayName("4. Source unavailable produces SOURCE_UNAVAILABLE and no proposal")
    void testSourceUnavailableProducesNoProposal() {
        when(geminiAnalysisService.analyzeSentence(argThat((SentenceAnalysisRequest req) -> req != null && req.getSentenceId() == 1)))
                .thenReturn(SentenceAnalysisResult.success(1, sentence1.getSentenceText(), true, "Outdated."));

        when(sourceVerificationService.verifySource(argThat(req -> req != null && req.getSentenceId() == 1)))
                .thenReturn(SourceVerificationResult.sourceUnavailable(1, sentence1.getSentenceText(), "Timeout", "url"));

        DocumentAnalysisRequest request = new DocumentAnalysisRequest(List.of(sentence1));
        DocumentAnalysisResult result = orchestrator.analyzeDocument(request);

        SentencePipelineResult itemResult = result.getResults().get(0);
        assertThat(itemResult.getStatus()).isEqualTo(PipelineStatus.SOURCE_UNAVAILABLE);
        assertThat(itemResult.getProposedUpdate()).isNull();
        verify(proposedUpdateService, never()).generateProposal(any(), any(), any());
    }

    @Test
    @DisplayName("5. AI unavailable handled per sentence and error is isolated")
    void testAiUnavailableHandledPerSentence() {
        // Sentence 1 fails with AI_UNAVAILABLE
        when(geminiAnalysisService.analyzeSentence(argThat((SentenceAnalysisRequest req) -> req != null && req.getSentenceId() == 1)))
                .thenReturn(SentenceAnalysisResult.aiUnavailable(1, sentence1.getSentenceText(), "Key not configured"));

        // Sentence 2 succeeds as timeless
        when(geminiAnalysisService.analyzeSentence(argThat((SentenceAnalysisRequest req) -> req != null && req.getSentenceId() == 2)))
                .thenReturn(SentenceAnalysisResult.success(2, sentence2.getSentenceText(), false, "Timeless"));

        DocumentAnalysisRequest request = new DocumentAnalysisRequest(List.of(sentence1, sentence2));
        DocumentAnalysisResult result = orchestrator.analyzeDocument(request);

        assertThat(result.getTotalSentences()).isEqualTo(2);
        assertThat(result.getResults().get(0).getStatus()).isEqualTo(PipelineStatus.AI_UNAVAILABLE);
        assertThat(result.getResults().get(1).getStatus()).isEqualTo(PipelineStatus.UNCHANGED);
    }

    @Test
    @DisplayName("6. Mixed batch: multiple sentences continue processing after a failure")
    void testMixedBatchExecution() {
        // Sentence 1: Outdated -> Verified -> Proposal
        when(geminiAnalysisService.analyzeSentence(argThat((SentenceAnalysisRequest req) -> req != null && req.getSentenceId() == 1)))
                .thenReturn(SentenceAnalysisResult.success(1, sentence1.getSentenceText(), true, "Outdated"));
        when(sourceVerificationService.verifySource(argThat(req -> req != null && req.getSentenceId() == 1)))
                .thenReturn(SourceVerificationResult.verified(1, sentence1.getSentenceText(), "LTS 21", "Title", "url", null, SourceType.REPUTABLE));
        when(proposedUpdateService.generateProposal(any(), any(), any()))
                .thenReturn(ProposedUpdate.proposed(1, sentence1.getSentenceText(), "Java 21 is LTS.", "Update", "LTS 21", "Title", "url", null, SourceType.REPUTABLE));

        // Sentence 2: Timeless -> UNCHANGED
        when(geminiAnalysisService.analyzeSentence(argThat((SentenceAnalysisRequest req) -> req != null && req.getSentenceId() == 2)))
                .thenReturn(SentenceAnalysisResult.success(2, sentence2.getSentenceText(), false, "Timeless"));

        // Sentence 3: Outdated -> Source unavailable
        when(geminiAnalysisService.analyzeSentence(argThat((SentenceAnalysisRequest req) -> req != null && req.getSentenceId() == 3)))
                .thenReturn(SentenceAnalysisResult.success(3, sentence3.getSentenceText(), true, "Outdated"));
        when(sourceVerificationService.verifySource(argThat(req -> req != null && req.getSentenceId() == 3)))
                .thenReturn(SourceVerificationResult.sourceUnavailable(3, sentence3.getSentenceText(), "HTTP 500", "url"));

        DocumentAnalysisRequest request = new DocumentAnalysisRequest("doc-123", List.of(sentence1, sentence2, sentence3));
        DocumentAnalysisResult result = orchestrator.analyzeDocument(request);

        assertThat(result.getRequestId()).isEqualTo("doc-123");
        assertThat(result.getTotalSentences()).isEqualTo(3);
        assertThat(result.getAnalyzedSentences()).isEqualTo(3);
        assertThat(result.getPotentiallyOutdatedCount()).isEqualTo(2);
        assertThat(result.getVerifiedCount()).isEqualTo(1);
        assertThat(result.getProposalCount()).isEqualTo(1);

        assertThat(result.getResults().get(0).getStatus()).isEqualTo(PipelineStatus.VERIFIED_UPDATE_PROPOSED);
        assertThat(result.getResults().get(1).getStatus()).isEqualTo(PipelineStatus.UNCHANGED);
        assertThat(result.getResults().get(2).getStatus()).isEqualTo(PipelineStatus.SOURCE_UNAVAILABLE);
    }

    @Test
    @DisplayName("7. Empty request returns zero counts without error")
    void testEmptyRequest() {
        DocumentAnalysisResult result = orchestrator.analyzeDocument(new DocumentAnalysisRequest(List.of()));

        assertThat(result.getTotalSentences()).isEqualTo(0);
        assertThat(result.getResults()).isEmpty();
    }
}
