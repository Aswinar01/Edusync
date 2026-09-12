package com.example.edusync.service;

import com.example.edusync.model.DocumentAnalysisRequest;
import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentProcessingResult;
import com.example.edusync.model.DocumentRevisionOutput;
import com.example.edusync.model.DocumentRevisionResult;
import com.example.edusync.model.DocumentReviewResult;
import com.example.edusync.model.DocumentType;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ReviewDecision;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.RevisionStatus;
import com.example.edusync.model.SentencePipelineResult;
import com.example.edusync.service.revision.DocxRevisionProcessor;
import com.example.edusync.service.revision.DocxSentenceLocator;
import com.example.edusync.service.revision.PdfRevisionProcessor;
import com.example.edusync.service.revision.PdfSentenceLocator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentRevisionIntegrationTest {

    private DocumentService documentService;
    private TextExtractionService textExtractionService;
    private SentenceSegmentationService segmentationService;
    private DocumentAnalysisOrchestrator orchestrator;
    private ReviewService reviewService;
    private DocumentStorageService storageService;
    private PdfRevisionProcessor pdfRevisionProcessor;
    private DocxRevisionProcessor docxRevisionProcessor;
    private DocumentProcessingService processingService;
    private DocumentRevisionService revisionService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService();
        textExtractionService = new TextExtractionService();
        segmentationService = new SentenceSegmentationService();
        orchestrator = mock(DocumentAnalysisOrchestrator.class);
        reviewService = new ReviewService();
        storageService = new DocumentStorageService();

        PdfSentenceLocator pdfLocator = new PdfSentenceLocator();
        DocxSentenceLocator docxLocator = new DocxSentenceLocator();

        pdfRevisionProcessor = new PdfRevisionProcessor(pdfLocator);
        docxRevisionProcessor = new DocxRevisionProcessor(docxLocator);

        processingService = new DocumentProcessingService(
                documentService,
                textExtractionService,
                segmentationService,
                orchestrator,
                reviewService,
                storageService
        );

        revisionService = new DocumentRevisionService(
                reviewService,
                storageService,
                pdfRevisionProcessor,
                docxRevisionProcessor
        );
    }

    private byte[] createPdf(String[] lines) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        cs.newLineAtOffset(0, -20);
                    }
                    cs.showText(lines[i]);
                }
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createDocx(String[] paragraphs) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (String pText : paragraphs) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(pText);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("End-to-End PDF: Upload -> Analysis -> Review Approval -> Prepare -> Apply Revision -> Immutability Verified")
    void testEndToEndPdfRevisionFlow() throws IOException {
        String s1 = "Java 17 is the current LTS version.";
        String s2 = "Python 2.7 is active.";
        byte[] originalPdfBytes = createPdf(new String[]{s1, s2});
        byte[] originalSnapshot = Arrays.copyOf(originalPdfBytes, originalPdfBytes.length);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                originalPdfBytes
        );

        when(orchestrator.analyzeDocument(any(DocumentAnalysisRequest.class))).thenAnswer(invocation -> {
            DocumentAnalysisRequest req = invocation.getArgument(0);
            com.example.edusync.model.ProposedUpdate update = com.example.edusync.model.ProposedUpdate.proposed(
                    1, s1, "Java 21 is the current LTS version.",
                    "Outdated", "Java 21 is current LTS", "Java Lifecycle",
                    "https://endoflife.date/java", "https://oracle.com/java",
                    com.example.edusync.model.SourceType.REPUTABLE
            );
            SentencePipelineResult r1 = new SentencePipelineResult(
                    1, s1, PipelineStatus.VERIFIED_UPDATE_PROPOSED,
                    null, null, update
            );
            SentencePipelineResult r2 = new SentencePipelineResult(
                    2, s2, PipelineStatus.UNCHANGED,
                    null, null, null
            );
            return new DocumentAnalysisResult(req.getRequestId(), 2, 2, 1, 1, 1, List.of(r1, r2));
        });

        // 1. Process document
        DocumentProcessingResult processingResult = processingService.processDocument(file);
        String requestId = processingResult.getRequestId();
        assertThat(requestId).isNotNull();

        // 2. Review and approve sentence 1
        DocumentReviewResult reviewedSession = reviewService.applySessionDecision(requestId, 1, ReviewDecision.APPROVE);
        assertThat(reviewedSession.getApprovedCount()).isEqualTo(1);

        // 3. Prepare revision
        DocumentRevisionResult prepared = revisionService.prepareRevision(requestId);
        assertThat(prepared.getRevisionStatus()).isEqualTo(RevisionStatus.READY);
        assertThat(prepared.getApprovedUpdateCount()).isEqualTo(1);

        // 4. Apply revision
        DocumentRevisionOutput output = revisionService.applyRevision(requestId);
        assertThat(output.getRevisionStatus()).isEqualTo(RevisionStatus.READY);
        assertThat(output.getApprovedUpdateCount()).isEqualTo(1);
        assertThat(output.getRevisedFilename()).isEqualTo("revised-sample.pdf");

        // 5. Verify original bytes in storage are 100% untouched
        DocumentStorageService.StoredDocument storedOriginal = storageService.getOriginalDocument(requestId);
        assertThat(storedOriginal.getData()).isEqualTo(originalSnapshot);

        // 6. Verify revised document bytes
        DocumentStorageService.StoredDocument storedRevised = storageService.getRevisedDocument(requestId);
        assertThat(storedRevised.getData()).isNotEqualTo(originalSnapshot);

        try (PDDocument revDoc = Loader.loadPDF(new RandomAccessReadBuffer(storedRevised.getData()))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String revText = stripper.getText(revDoc);
            assertThat(revText).contains("Java 21 is the current LTS version.");
            assertThat(revText).contains("[Update #1]");
            assertThat(revText).contains("https://oracle.com/java");
        }
    }

    @Test
    @DisplayName("End-to-End DOCX: Upload -> Analysis -> Review Approval -> Prepare -> Apply Revision -> Immutability Verified")
    void testEndToEndDocxRevisionFlow() throws IOException {
        String s1 = "PostgreSQL 14 is the latest major release.";
        String s2 = "Relational databases are structured.";
        byte[] originalDocxBytes = createDocx(new String[]{s1, s2});
        byte[] originalSnapshot = Arrays.copyOf(originalDocxBytes, originalDocxBytes.length);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                originalDocxBytes
        );

        when(orchestrator.analyzeDocument(any(DocumentAnalysisRequest.class))).thenAnswer(invocation -> {
            DocumentAnalysisRequest req = invocation.getArgument(0);
            com.example.edusync.model.ProposedUpdate update = com.example.edusync.model.ProposedUpdate.proposed(
                    1, s1, "PostgreSQL 16 is the latest major release.",
                    "Outdated", "PostgreSQL 16 released", "PostgreSQL Docs",
                    "https://postgresql.org", "https://postgresql.org/docs",
                    com.example.edusync.model.SourceType.REPUTABLE
            );
            SentencePipelineResult r1 = new SentencePipelineResult(
                    1, s1, PipelineStatus.VERIFIED_UPDATE_PROPOSED,
                    null, null, update
            );
            SentencePipelineResult r2 = new SentencePipelineResult(
                    2, s2, PipelineStatus.UNCHANGED,
                    null, null, null
            );
            return new DocumentAnalysisResult(req.getRequestId(), 2, 2, 1, 1, 1, List.of(r1, r2));
        });

        // 1. Process document
        DocumentProcessingResult processingResult = processingService.processDocument(file);
        String requestId = processingResult.getRequestId();

        // 2. Approve sentence 1
        reviewService.applySessionDecision(requestId, 1, ReviewDecision.APPROVE);

        // 3. Apply revision
        DocumentRevisionOutput output = revisionService.applyRevision(requestId);
        assertThat(output.getRevisionStatus()).isEqualTo(RevisionStatus.READY);
        assertThat(output.getApprovedUpdateCount()).isEqualTo(1);
        assertThat(output.getRevisedFilename()).isEqualTo("revised-notes.docx");

        // 4. Verify original bytes in storage remain identical
        DocumentStorageService.StoredDocument storedOriginal = storageService.getOriginalDocument(requestId);
        assertThat(storedOriginal.getData()).isEqualTo(originalSnapshot);

        // 5. Verify revised bytes in POI
        DocumentStorageService.StoredDocument storedRevised = storageService.getRevisedDocument(requestId);
        try (XWPFDocument revDoc = new XWPFDocument(new ByteArrayInputStream(storedRevised.getData()))) {
            XWPFParagraph p1 = revDoc.getParagraphs().get(0);
            assertThat(p1.getText()).contains("PostgreSQL 16 is the latest major release.");
            assertThat(p1.getText()).contains("[Source: PostgreSQL 16 released | Ref: https://postgresql.org/docs]");

            boolean highlighted = p1.getRuns().stream()
                    .anyMatch(r -> r.getTextHighlightColor() != null && "yellow".equalsIgnoreCase(r.getTextHighlightColor().toString()));
            assertThat(highlighted).isTrue();

            XWPFParagraph p2 = revDoc.getParagraphs().get(1);
            assertThat(p2.getText()).isEqualTo(s2);
        }
    }
}
