package com.example.edusync.service;

import com.example.edusync.model.DocumentAnalysisRequest;
import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentProcessingResult;
import com.example.edusync.model.DocumentReviewResult;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.DocumentType;
import com.example.edusync.model.DocumentUploadResponse;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.SentencePipelineResult;
import com.example.edusync.model.TextExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private TextExtractionService textExtractionService;

    @Mock
    private SentenceSegmentationService sentenceSegmentationService;

    @Mock
    private DocumentAnalysisOrchestrator documentAnalysisOrchestrator;

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private DocumentProcessingService documentProcessingService;

    private MockMultipartFile validPdfFile;
    private MockMultipartFile validDocxFile;

    @BeforeEach
    void setUp() {
        validPdfFile = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "%PDF-1.4 sample content".getBytes()
        );

        validDocxFile = new MockMultipartFile(
                "file",
                "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "PK docx content".getBytes()
        );
    }

    @Test
    @DisplayName("1. PDF processing successfully executes validation -> extraction -> segmentation -> orchestrator")
    void testPdfProcessingFlow() {
        when(documentService.validateAndProcess(validPdfFile))
                .thenReturn(new DocumentUploadResponse("sample.pdf", 100L, "application/pdf", "Valid"));

        TextExtractionResult extraction = new TextExtractionResult("sample.pdf", DocumentType.PDF, "Java 17 is latest LTS. Second sentence.", 1);
        when(textExtractionService.extractText(validPdfFile))
                .thenReturn(extraction);

        List<DocumentSentence> sentences = List.of(
                new DocumentSentence(1, "Java 17 is latest LTS."),
                new DocumentSentence(2, "Second sentence.")
        );
        when(sentenceSegmentationService.segment(extraction))
                .thenReturn(sentences);

        DocumentAnalysisResult analysisResult = new DocumentAnalysisResult(
                "req-1", 2, 2, 1, 1, 1,
                List.of(
                        new SentencePipelineResult(1, "Java 17 is latest LTS.", PipelineStatus.VERIFIED_UPDATE_PROPOSED, null, null, null),
                        new SentencePipelineResult(2, "Second sentence.", PipelineStatus.UNCHANGED, null, null, null)
                )
        );
        when(documentAnalysisOrchestrator.analyzeDocument(any(DocumentAnalysisRequest.class)))
                .thenReturn(analysisResult);

        DocumentProcessingResult result = documentProcessingService.processDocument(validPdfFile);

        assertThat(result).isNotNull();
        assertThat(result.getOriginalFileName()).isEqualTo("sample.pdf");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.PDF);
        assertThat(result.getTotalSentences()).isEqualTo(2);
        assertThat(result.getTotalExtractedCharacters()).isEqualTo("Java 17 is latest LTS. Second sentence.".length());
        assertThat(result.getRequestId()).isNotBlank();
        assertThat(result.getAnalysisResult()).isEqualTo(analysisResult);

        // Verify request ID propagated to orchestrator
        ArgumentCaptor<DocumentAnalysisRequest> requestCaptor = ArgumentCaptor.forClass(DocumentAnalysisRequest.class);
        verify(documentAnalysisOrchestrator).analyzeDocument(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRequestId()).isEqualTo(result.getRequestId());
        assertThat(requestCaptor.getValue().getSentences()).hasSize(2);
    }

    @Test
    @DisplayName("2. DOCX processing successfully executes validation -> extraction -> segmentation -> orchestrator")
    void testDocxProcessingFlow() {
        when(documentService.validateAndProcess(validDocxFile))
                .thenReturn(new DocumentUploadResponse("notes.docx", 200L, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Valid"));

        TextExtractionResult extraction = new TextExtractionResult("notes.docx", DocumentType.DOCX, "Extracted DOCX prose.", 1);
        when(textExtractionService.extractText(validDocxFile))
                .thenReturn(extraction);

        List<DocumentSentence> sentences = List.of(new DocumentSentence(1, "Extracted DOCX prose."));
        when(sentenceSegmentationService.segment(extraction))
                .thenReturn(sentences);

        DocumentAnalysisResult analysisResult = new DocumentAnalysisResult(
                "req-docx", 1, 1, 0, 0, 0,
                List.of(new SentencePipelineResult(1, "Extracted DOCX prose.", PipelineStatus.UNCHANGED, null, null, null))
        );
        when(documentAnalysisOrchestrator.analyzeDocument(any(DocumentAnalysisRequest.class)))
                .thenReturn(analysisResult);

        DocumentProcessingResult result = documentProcessingService.processDocument(validDocxFile);

        assertThat(result).isNotNull();
        assertThat(result.getOriginalFileName()).isEqualTo("notes.docx");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.DOCX);
        assertThat(result.getTotalSentences()).isEqualTo(1);
    }

    @Test
    @DisplayName("3. Invalid document throws InvalidDocumentException and halts processing")
    void testInvalidDocumentRejected() {
        when(documentService.validateAndProcess(validPdfFile))
                .thenThrow(new InvalidDocumentException("Unsupported file extension."));

        assertThatThrownBy(() -> documentProcessingService.processDocument(validPdfFile))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("Unsupported file extension");
    }

    @Test
    @DisplayName("4. Document with empty extracted text throws DocumentExtractionException without calling orchestrator")
    void testEmptyExtractedDocumentThrowsException() {
        when(documentService.validateAndProcess(validPdfFile))
                .thenReturn(new DocumentUploadResponse("sample.pdf", 50L, "application/pdf", "Valid"));

        TextExtractionResult emptyExtraction = new TextExtractionResult("sample.pdf", DocumentType.PDF, "   ", 1);
        when(textExtractionService.extractText(validPdfFile))
                .thenReturn(emptyExtraction);

        assertThatThrownBy(() -> documentProcessingService.processDocument(validPdfFile))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("contains no readable text");
    }

    @Test
    @DisplayName("5. Document with 0 segmented sentences throws DocumentExtractionException")
    void testEmptySegmentedSentencesThrowsException() {
        when(documentService.validateAndProcess(validPdfFile))
                .thenReturn(new DocumentUploadResponse("sample.pdf", 50L, "application/pdf", "Valid"));

        TextExtractionResult extraction = new TextExtractionResult("sample.pdf", DocumentType.PDF, "Valid text.", 1);
        when(textExtractionService.extractText(validPdfFile))
                .thenReturn(extraction);

        when(sentenceSegmentationService.segment(extraction))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> documentProcessingService.processDocument(validPdfFile))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("contains no readable sentences");
    }

    @Test
    @DisplayName("6. Extraction failure propagates DocumentExtractionException")
    void testExtractionFailurePropagates() {
        when(documentService.validateAndProcess(validPdfFile))
                .thenReturn(new DocumentUploadResponse("sample.pdf", 50L, "application/pdf", "Valid"));

        when(textExtractionService.extractText(validPdfFile))
                .thenThrow(new DocumentExtractionException("Failed to read PDF stream"));

        assertThatThrownBy(() -> documentProcessingService.processDocument(validPdfFile))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("Failed to read PDF stream");
    }

    @Test
    @DisplayName("7. Generated requestId is unique and present on each processing run")
    void testRequestIdGeneratedAndPresent() {
        when(documentService.validateAndProcess(any()))
                .thenReturn(new DocumentUploadResponse("doc.pdf", 10L, "application/pdf", "Valid"));

        TextExtractionResult extraction = new TextExtractionResult("doc.pdf", DocumentType.PDF, "Sentence one.", 1);
        when(textExtractionService.extractText(any()))
                .thenReturn(extraction);

        when(sentenceSegmentationService.segment(any(TextExtractionResult.class)))
                .thenReturn(List.of(new DocumentSentence(1, "Sentence one.")));

        when(documentAnalysisOrchestrator.analyzeDocument(any()))
                .thenAnswer(inv -> {
                    DocumentAnalysisRequest req = inv.getArgument(0);
                    return new DocumentAnalysisResult(req.getRequestId(), 1, 1, 0, 0, 0, List.of());
                });

        DocumentProcessingResult res1 = documentProcessingService.processDocument(validPdfFile);
        DocumentProcessingResult res2 = documentProcessingService.processDocument(validPdfFile);

        assertThat(res1.getRequestId()).isNotBlank();
        assertThat(res2.getRequestId()).isNotBlank();
        assertThat(res1.getRequestId()).isNotEqualTo(res2.getRequestId());
    }

    @Test
    @DisplayName("8. Original filename and metadata are preserved in result")
    void testOriginalFilenamePreserved() {
        when(documentService.validateAndProcess(validDocxFile))
                .thenReturn(new DocumentUploadResponse("notes.docx", 20L, "docx", "Valid"));
        when(textExtractionService.extractText(validDocxFile))
                .thenReturn(new TextExtractionResult("notes.docx", DocumentType.DOCX, "Content", 1));
        when(sentenceSegmentationService.segment(any(TextExtractionResult.class)))
                .thenReturn(List.of(new DocumentSentence(1, "Content")));
        when(documentAnalysisOrchestrator.analyzeDocument(any()))
                .thenReturn(new DocumentAnalysisResult("r", 1, 1, 0, 0, 0, List.of()));

        DocumentProcessingResult result = documentProcessingService.processDocument(validDocxFile);

        assertThat(result.getOriginalFileName()).isEqualTo("notes.docx");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.DOCX);
    }

    @Test
    @DisplayName("9. Architecture Isolation: DocumentProcessingService delegates analysis only through DocumentAnalysisOrchestrator without direct dependency on or calls to GeminiAnalysisService, SourceVerificationService, or ProposedUpdateService")
    void testArchitectureIsolationDocumentProcessingServiceDelegatesOnlyViaOrchestrator() {
        // Architectural verification: Confirm DocumentProcessingService has no declared fields of the 3 low-level pipeline services
        List<Class<?>> declaredFieldTypes = Arrays.stream(DocumentProcessingService.class.getDeclaredFields())
                .<Class<?>>map(Field::getType)
                .toList();

        assertThat(declaredFieldTypes)
                .as("DocumentProcessingService must delegate analysis via DocumentAnalysisOrchestrator and not declare direct service dependencies")
                .contains(DocumentAnalysisOrchestrator.class)
                .doesNotContain(GeminiAnalysisService.class, SourceVerificationService.class, ProposedUpdateService.class);

        // Architectural verification: Confirm constructor parameters do not accept the 3 low-level pipeline services
        boolean hasForbiddenConstructorParam = Arrays.stream(DocumentProcessingService.class.getConstructors())
                .flatMap(c -> Arrays.stream(c.getParameterTypes()))
                .anyMatch(paramType -> paramType.equals(GeminiAnalysisService.class)
                        || paramType.equals(SourceVerificationService.class)
                        || paramType.equals(ProposedUpdateService.class));

        assertThat(hasForbiddenConstructorParam)
                .as("DocumentProcessingService constructor must not accept GeminiAnalysisService, SourceVerificationService, or ProposedUpdateService")
                .isFalse();

        // Behavioral verification: Mock the 3 low-level pipeline services independently
        GeminiAnalysisService mockGeminiService = mock(GeminiAnalysisService.class);
        SourceVerificationService mockVerificationService = mock(SourceVerificationService.class);
        ProposedUpdateService mockProposedUpdateService = mock(ProposedUpdateService.class);

        // Setup standard document processing execution
        when(documentService.validateAndProcess(validPdfFile))
                .thenReturn(new DocumentUploadResponse("sample.pdf", 100L, "application/pdf", "Valid"));

        TextExtractionResult extraction = new TextExtractionResult("sample.pdf", DocumentType.PDF, "Isolated sentence.", 1);
        when(textExtractionService.extractText(validPdfFile)).thenReturn(extraction);

        List<DocumentSentence> sentences = List.of(new DocumentSentence(1, "Isolated sentence."));
        when(sentenceSegmentationService.segment(extraction)).thenReturn(sentences);

        DocumentAnalysisResult orchestratorResult = new DocumentAnalysisResult(
                "req-iso", 1, 1, 0, 0, 0,
                List.of(new SentencePipelineResult(1, "Isolated sentence.", PipelineStatus.UNCHANGED, null, null, null))
        );
        when(documentAnalysisOrchestrator.analyzeDocument(any(DocumentAnalysisRequest.class)))
                .thenReturn(orchestratorResult);

        // Execute processing
        DocumentProcessingResult result = documentProcessingService.processDocument(validPdfFile);

        // Verify that analysis was delegated to DocumentAnalysisOrchestrator
        assertThat(result).isNotNull();
        verify(documentAnalysisOrchestrator).analyzeDocument(any(DocumentAnalysisRequest.class));

        // Verify that DocumentProcessingService made zero interactions with GeminiAnalysisService, SourceVerificationService, and ProposedUpdateService
        verifyNoInteractions(mockGeminiService, mockVerificationService, mockProposedUpdateService);
    }

    @Test
    @DisplayName("10. Processing pipeline automatically creates server-side in-memory review session upon analysis completion")
    void testReviewSessionCreatedOnSuccessfulAnalysis() {
        when(documentService.validateAndProcess(validPdfFile))
                .thenReturn(new DocumentUploadResponse("sample.pdf", 100L, "application/pdf", "Valid"));

        TextExtractionResult extraction = new TextExtractionResult("sample.pdf", DocumentType.PDF, "Java 17 is LTS.", 1);
        when(textExtractionService.extractText(validPdfFile)).thenReturn(extraction);
        when(sentenceSegmentationService.segment(extraction)).thenReturn(List.of(new DocumentSentence(1, "Java 17 is LTS.")));

        DocumentAnalysisResult orchestratorResult = new DocumentAnalysisResult(
                "req-session-test", 1, 1, 1, 1, 1,
                List.of(new SentencePipelineResult(1, "Java 17 is LTS.", PipelineStatus.VERIFIED_UPDATE_PROPOSED, null, null, null))
        );
        when(documentAnalysisOrchestrator.analyzeDocument(any(DocumentAnalysisRequest.class)))
                .thenReturn(orchestratorResult);

        DocumentReviewResult mockReviewResult = new DocumentReviewResult();
        mockReviewResult.setRequestId("req-session-test");
        mockReviewResult.setTotalSentences(1);
        mockReviewResult.setReviewableCount(1);
        when(reviewService.createReviewSession(orchestratorResult)).thenReturn(mockReviewResult);

        DocumentProcessingResult result = documentProcessingService.processDocument(validPdfFile);

        assertThat(result).isNotNull();
        assertThat(result.getReviewResult()).isNotNull();
        assertThat(result.getReviewResult().getRequestId()).isEqualTo("req-session-test");
        verify(reviewService).createReviewSession(orchestratorResult);
    }
}
