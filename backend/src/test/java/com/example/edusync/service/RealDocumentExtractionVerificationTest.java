package com.example.edusync.service;

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

import static org.assertj.core.api.Assertions.assertThat;

class RealDocumentExtractionVerificationTest {

    private TextExtractionService textExtractionService;

    @BeforeEach
    void setUp() {
        textExtractionService = new TextExtractionService();
    }

    @Test
    @DisplayName("Verification: Extract from real multi-page PDF document")
    void verifyRealMultiPagePdfExtraction() throws IOException {
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            // Page 1
            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs1 = new PDPageContentStream(doc, page1)) {
                cs1.beginText();
                cs1.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs1.newLineAtOffset(50, 750);
                cs1.showText("EduSync Chapter 1: Introduction to Cloud Computing");
                cs1.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs1.newLineAtOffset(0, -30);
                cs1.showText("Cloud computing provides on-demand availability of system resources.");
                cs1.endText();
            }

            // Page 2
            PDPage page2 = new PDPage();
            doc.addPage(page2);
            try (PDPageContentStream cs2 = new PDPageContentStream(doc, page2)) {
                cs2.beginText();
                cs2.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs2.newLineAtOffset(50, 750);
                cs2.showText("EduSync Chapter 2: Virtualization & Containers");
                cs2.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs2.newLineAtOffset(0, -30);
                cs2.showText("Containers offer lightweight operating-system-level virtualization.");
                cs2.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cloud_computing_lecture.pdf",
                "application/pdf",
                pdfBytes
        );

        TextExtractionResult result = textExtractionService.extractText(file);

        System.out.println("\n================ REAL PDF EXTRACTION RESULT ================");
        System.out.println("Filename: " + result.getFilename());
        System.out.println("Document Type: " + result.getDocumentType());
        System.out.println("Page Count: " + result.getPageCount());
        System.out.println("Extracted Text:\n" + result.getExtractedText());
        System.out.println("============================================================\n");

        assertThat(result.getFilename()).isEqualTo("cloud_computing_lecture.pdf");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.PDF);
        assertThat(result.getPageCount()).isEqualTo(2);
        assertThat(result.getExtractedText())
                .contains("EduSync Chapter 1: Introduction to Cloud Computing")
                .contains("Cloud computing provides on-demand availability of system resources.")
                .contains("EduSync Chapter 2: Virtualization & Containers")
                .contains("Containers offer lightweight operating-system-level virtualization.");
    }

    @Test
    @DisplayName("Verification: Extract from real DOCX document with paragraphs and tables")
    void verifyRealDocxExtraction() throws IOException {
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph title = doc.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setText("EduSync Syllabus: Software Engineering Methodologies");

            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun p1Run = p1.createRun();
            p1Run.setText("Agile methodology emphasizes iterative development and customer feedback.");

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun p2Run = p2.createRun();
            p2Run.setText("Continuous Integration ensures automated testing on code check-ins.");

            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Phase");
            table.getRow(0).getCell(1).setText("Deliverable");
            table.getRow(1).getCell(0).setText("Sprint 1");
            table.getRow(1).getCell(1).setText("Core Backend APIs");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            docxBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "software_engineering_syllabus.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        TextExtractionResult result = textExtractionService.extractText(file);

        System.out.println("\n================ REAL DOCX EXTRACTION RESULT ================");
        System.out.println("Filename: " + result.getFilename());
        System.out.println("Document Type: " + result.getDocumentType());
        System.out.println("Extracted Text:\n" + result.getExtractedText());
        System.out.println("=============================================================\n");

        assertThat(result.getFilename()).isEqualTo("software_engineering_syllabus.docx");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.DOCX);
        assertThat(result.getExtractedText())
                .contains("EduSync Syllabus: Software Engineering Methodologies")
                .contains("Agile methodology emphasizes iterative development and customer feedback.")
                .contains("Continuous Integration ensures automated testing on code check-ins.")
                .contains("Phase | Deliverable")
                .contains("Sprint 1 | Core Backend APIs");
    }
}
