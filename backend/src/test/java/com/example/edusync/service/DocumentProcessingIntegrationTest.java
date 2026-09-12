package com.example.edusync.service;

import com.example.edusync.model.DocumentAnalysisRequest;
import com.example.edusync.model.DocumentAnalysisResult;
import com.example.edusync.model.DocumentProcessingResult;
import com.example.edusync.model.DocumentType;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.SentencePipelineResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentProcessingIntegrationTest {

    private DocumentService documentService;
    private TextExtractionService textExtractionService;
    private SentenceSegmentationService segmentationService;
    private DocumentAnalysisOrchestrator orchestrator;
    private DocumentProcessingService processingService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService();
        textExtractionService = new TextExtractionService();
        segmentationService = new SentenceSegmentationService();
        orchestrator = mock(DocumentAnalysisOrchestrator.class);
        processingService = new DocumentProcessingService(
                documentService,
                textExtractionService,
                segmentationService,
                orchestrator
        );
    }

    @Test
    @DisplayName("End-to-End: Real PDF bytes -> Validation -> Extraction -> Segmentation -> Orchestration -> Result")
    void testRealPdfEndToEndProcessing() throws IOException {
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("Java 17 is the latest LTS release.");
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(0, -25);
                cs.showText("Dr. Smith teaches data structures.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Can distributed algorithms achieve consensus?");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "computer_science_syllabus.pdf",
                "application/pdf",
                pdfBytes
        );

        when(orchestrator.analyzeDocument(any(DocumentAnalysisRequest.class)))
                .thenAnswer(invocation -> {
                    DocumentAnalysisRequest req = invocation.getArgument(0);
                    return new DocumentAnalysisResult(
                            req.getRequestId(),
                            req.getSentences().size(),
                            req.getSentences().size(),
                            1,
                            1,
                            1,
                            List.of(
                                    new SentencePipelineResult(1, req.getSentences().get(0).getSentenceText(), PipelineStatus.VERIFIED_UPDATE_PROPOSED, null, null, null),
                                    new SentencePipelineResult(2, req.getSentences().get(1).getSentenceText(), PipelineStatus.UNCHANGED, null, null, null),
                                    new SentencePipelineResult(3, req.getSentences().get(2).getSentenceText(), PipelineStatus.UNCHANGED, null, null, null)
                            )
                    );
                });

        DocumentProcessingResult result = processingService.processDocument(file);

        assertThat(result).isNotNull();
        assertThat(result.getRequestId()).isNotBlank();
        assertThat(result.getOriginalFileName()).isEqualTo("computer_science_syllabus.pdf");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.PDF);
        assertThat(result.getTotalExtractedCharacters()).isGreaterThan(0);
        assertThat(result.getTotalSentences()).isEqualTo(3);
        assertThat(result.getAnalysisResult()).isNotNull();
        assertThat(result.getAnalysisResult().getRequestId()).isEqualTo(result.getRequestId());
        assertThat(result.getAnalysisResult().getPotentiallyOutdatedCount()).isEqualTo(1);
        assertThat(result.getAnalysisResult().getProposalCount()).isEqualTo(1);

        ArgumentCaptor<DocumentAnalysisRequest> captor = ArgumentCaptor.forClass(DocumentAnalysisRequest.class);
        verify(orchestrator).analyzeDocument(captor.capture());

        DocumentAnalysisRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getRequestId()).isEqualTo(result.getRequestId());
        assertThat(capturedRequest.getSentences()).hasSize(3);
        assertThat(capturedRequest.getSentences().get(0).getSentenceText()).isEqualTo("Java 17 is the latest LTS release.");
        assertThat(capturedRequest.getSentences().get(1).getSentenceText()).isEqualTo("Dr. Smith teaches data structures.");
        assertThat(capturedRequest.getSentences().get(2).getSentenceText()).isEqualTo("Can distributed algorithms achieve consensus?");
    }

    @Test
    @DisplayName("End-to-End: Real DOCX bytes -> Validation -> Extraction -> Segmentation -> Orchestration -> Result")
    void testRealDocxEndToEndProcessing() throws IOException {
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("Spring Boot 2.5 is widely used in production. Python 2.7 reached end of life.");

            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Topic");
            table.getRow(0).getCell(1).setText("Status");
            table.getRow(1).getCell(0).setText("Security");
            table.getRow(1).getCell(1).setText("Required");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            docxBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "backend_overview.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        when(orchestrator.analyzeDocument(any(DocumentAnalysisRequest.class)))
                .thenAnswer(invocation -> {
                    DocumentAnalysisRequest req = invocation.getArgument(0);
                    return new DocumentAnalysisResult(
                            req.getRequestId(),
                            req.getSentences().size(),
                            req.getSentences().size(),
                            2,
                            2,
                            2,
                            List.of(
                                    new SentencePipelineResult(1, req.getSentences().get(0).getSentenceText(), PipelineStatus.VERIFIED_UPDATE_PROPOSED, null, null, null),
                                    new SentencePipelineResult(2, req.getSentences().get(1).getSentenceText(), PipelineStatus.VERIFIED_UPDATE_PROPOSED, null, null, null),
                                    new SentencePipelineResult(3, req.getSentences().get(2).getSentenceText(), PipelineStatus.UNCHANGED, null, null, null),
                                    new SentencePipelineResult(4, req.getSentences().get(3).getSentenceText(), PipelineStatus.UNCHANGED, null, null, null)
                            )
                    );
                });

        DocumentProcessingResult result = processingService.processDocument(file);

        assertThat(result).isNotNull();
        assertThat(result.getRequestId()).isNotBlank();
        assertThat(result.getOriginalFileName()).isEqualTo("backend_overview.docx");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.DOCX);
        assertThat(result.getTotalExtractedCharacters()).isGreaterThan(0);
        assertThat(result.getTotalSentences()).isEqualTo(4);
        assertThat(result.getAnalysisResult()).isNotNull();
        assertThat(result.getAnalysisResult().getRequestId()).isEqualTo(result.getRequestId());
        assertThat(result.getAnalysisResult().getPotentiallyOutdatedCount()).isEqualTo(2);

        ArgumentCaptor<DocumentAnalysisRequest> captor = ArgumentCaptor.forClass(DocumentAnalysisRequest.class);
        verify(orchestrator).analyzeDocument(captor.capture());

        DocumentAnalysisRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getRequestId()).isEqualTo(result.getRequestId());
        assertThat(capturedRequest.getSentences()).hasSize(4);
        assertThat(capturedRequest.getSentences().get(0).getSentenceText()).isEqualTo("Spring Boot 2.5 is widely used in production.");
        assertThat(capturedRequest.getSentences().get(1).getSentenceText()).isEqualTo("Python 2.7 reached end of life.");
        assertThat(capturedRequest.getSentences().get(2).getSentenceText()).isEqualTo("Topic | Status");
        assertThat(capturedRequest.getSentences().get(3).getSentenceText()).isEqualTo("Security | Required");
    }

    @Test
    @DisplayName("End-to-End: Blank PDF -> Throws DocumentExtractionException and bypasses AI Orchestrator")
    void testBlankPdfBypassesOrchestrator() throws IOException {
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page); // Empty page with no text content
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "blank.pdf",
                "application/pdf",
                pdfBytes
        );

        assertThatThrownBy(() -> processingService.processDocument(file))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("Document contains no readable text");

        verify(orchestrator, never()).analyzeDocument(any());
    }
}
