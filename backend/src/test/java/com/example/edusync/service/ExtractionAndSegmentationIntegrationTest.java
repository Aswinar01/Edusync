package com.example.edusync.service;

import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.DocumentType;
import com.example.edusync.model.TextExtractionResult;
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
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionAndSegmentationIntegrationTest {

    private TextExtractionService textExtractionService;
    private SentenceSegmentationService segmentationService;

    @BeforeEach
    void setUp() {
        textExtractionService = new TextExtractionService();
        segmentationService = new SentenceSegmentationService();
    }

    @Test
    @DisplayName("Integration: PDF Extracted Text -> Sentence Segmentation -> Ordered DocumentSentence List")
    void testPdfExtractionToSentenceSegmentationPipeline() throws IOException {
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            // Page 1
            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs1 = new PDPageContentStream(doc, page1)) {
                cs1.beginText();
                cs1.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs1.newLineAtOffset(50, 750);
                cs1.showText("Cloud Architecture Overview.");
                cs1.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs1.newLineAtOffset(0, -25);
                cs1.showText("Dr. Smith published the seminal paper on virtualization.");
                cs1.newLineAtOffset(0, -20);
                cs1.showText("Can distributed clusters achieve 99.9% uptime?");
                cs1.endText();
            }

            // Page 2
            PDPage page2 = new PDPage();
            doc.addPage(page2);
            try (PDPageContentStream cs2 = new PDPageContentStream(doc, page2)) {
                cs2.beginText();
                cs2.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs2.newLineAtOffset(50, 750);
                cs2.showText("Yes, high availability architecture guarantees fault resilience!");
                cs2.newLineAtOffset(0, -20);
                cs2.showText("The cluster uses Kubernetes version 1.29.0 and Spring Boot version 4.1.0.");
                cs2.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cloud_architecture.pdf",
                "application/pdf",
                pdfBytes
        );

        // Step 1: Text extraction
        TextExtractionResult extractionResult = textExtractionService.extractText(file);
        assertThat(extractionResult).isNotNull();
        assertThat(extractionResult.getDocumentType()).isEqualTo(DocumentType.PDF);
        assertThat(extractionResult.getPageCount()).isEqualTo(2);

        // Step 2: Sentence segmentation
        List<DocumentSentence> sentences = segmentationService.segment(extractionResult);

        System.out.println("\n========== PDF PIPELINE: SENTENCE SEGMENTATION RESULTS ==========");
        for (DocumentSentence s : sentences) {
            System.out.println(s.getSentenceId() + ": " + s.getSentenceText());
        }
        System.out.println("=================================================================\n");

        assertThat(sentences).hasSize(5);

        // Verify sequential IDs and ordering
        for (int i = 0; i < sentences.size(); i++) {
            assertThat(sentences.get(i).getSentenceId()).isEqualTo(i + 1);
        }

        assertThat(sentences.get(0).getSentenceText()).isEqualTo("Cloud Architecture Overview.");
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("Dr. Smith published the seminal paper on virtualization.");
        assertThat(sentences.get(2).getSentenceText()).isEqualTo("Can distributed clusters achieve 99.9% uptime?");
        assertThat(sentences.get(3).getSentenceText()).isEqualTo("Yes, high availability architecture guarantees fault resilience!");
        assertThat(sentences.get(4).getSentenceText()).isEqualTo("The cluster uses Kubernetes version 1.29.0 and Spring Boot version 4.1.0.");
    }

    @Test
    @DisplayName("Integration: DOCX Extracted Text -> Sentence Segmentation -> Ordered DocumentSentence List")
    void testDocxExtractionToSentenceSegmentationPipeline() throws IOException {
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("Prof. Anderson introduced the syllabus. Students will use modern dev tools (e.g. Git, Docker, etc.) throughout the semester.");

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun r2 = p2.createRun();
            r2.setText("Is late homework accepted? No, submissions close at 11.59 PM sharp!");

            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Unit");
            table.getRow(0).getCell(1).setText("Topic");
            table.getRow(1).getCell(0).setText("Unit 1");
            table.getRow(1).getCell(1).setText("Advanced Algorithms");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            docxBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "course_syllabus.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        // Step 1: Text extraction
        TextExtractionResult extractionResult = textExtractionService.extractText(file);
        assertThat(extractionResult).isNotNull();
        assertThat(extractionResult.getDocumentType()).isEqualTo(DocumentType.DOCX);

        // Step 2: Sentence segmentation
        List<DocumentSentence> sentences = segmentationService.segment(extractionResult);

        System.out.println("\n========== DOCX PIPELINE: SENTENCE SEGMENTATION RESULTS ==========");
        for (DocumentSentence s : sentences) {
            System.out.println(s.getSentenceId() + ": " + s.getSentenceText());
        }
        System.out.println("==================================================================\n");

        assertThat(sentences).hasSize(6);

        // Verify sequential IDs and ordering
        for (int i = 0; i < sentences.size(); i++) {
            assertThat(sentences.get(i).getSentenceId()).isEqualTo(i + 1);
        }

        assertThat(sentences.get(0).getSentenceText()).isEqualTo("Prof. Anderson introduced the syllabus.");
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("Students will use modern dev tools (e.g. Git, Docker, etc.) throughout the semester.");
        assertThat(sentences.get(2).getSentenceText()).isEqualTo("Is late homework accepted?");
        assertThat(sentences.get(3).getSentenceText()).isEqualTo("No, submissions close at 11.59 PM sharp!");
        assertThat(sentences.get(4).getSentenceText()).isEqualTo("Unit | Topic");
        assertThat(sentences.get(5).getSentenceText()).isEqualTo("Unit 1 | Advanced Algorithms");
    }
}
