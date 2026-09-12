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
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextExtractionServiceTest {

    private TextExtractionService textExtractionService;

    @BeforeEach
    void setUp() {
        textExtractionService = new TextExtractionService();
    }

    @Test
    @DisplayName("Should extract text from a real single-page PDF")
    void extractSinglePagePdfSuccessfully() throws IOException {
        byte[] pdfBytes = createPdf(new String[]{"Introduction to Computer Networks and Protocols."});
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "networks.pdf",
                "application/pdf",
                pdfBytes
        );

        TextExtractionResult result = textExtractionService.extractText(file);

        assertThat(result).isNotNull();
        assertThat(result.getFilename()).isEqualTo("networks.pdf");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.PDF);
        assertThat(result.getPageCount()).isEqualTo(1);
        assertThat(result.getExtractedText()).contains("Introduction to Computer Networks and Protocols.");
    }

    @Test
    @DisplayName("Should extract text in order from a real multi-page PDF")
    void extractMultiPagePdfSuccessfully() throws IOException {
        byte[] pdfBytes = createPdf(new String[]{
                "Page 1: Fundamental Concepts of Distributed Systems.",
                "Page 2: Consensus Algorithms and Raft Protocol.",
                "Page 3: Fault Tolerance and Byzantine Faults."
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "distributed_systems.pdf",
                "application/pdf",
                pdfBytes
        );

        TextExtractionResult result = textExtractionService.extractText(file);

        assertThat(result).isNotNull();
        assertThat(result.getFilename()).isEqualTo("distributed_systems.pdf");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.PDF);
        assertThat(result.getPageCount()).isEqualTo(3);
        assertThat(result.getExtractedText())
                .contains("Page 1: Fundamental Concepts of Distributed Systems.")
                .contains("Page 2: Consensus Algorithms and Raft Protocol.")
                .contains("Page 3: Fault Tolerance and Byzantine Faults.");

        int posPage1 = result.getExtractedText().indexOf("Page 1");
        int posPage2 = result.getExtractedText().indexOf("Page 2");
        int posPage3 = result.getExtractedText().indexOf("Page 3");
        assertThat(posPage1).isLessThan(posPage2);
        assertThat(posPage2).isLessThan(posPage3);
    }

    @Test
    @DisplayName("Should extract text and preserve paragraph boundaries from a real DOCX")
    void extractDocxSuccessfully() throws IOException {
        byte[] docxBytes = createDocx(new String[]{
                "Database Management Systems Lecture 1.",
                "Relational algebra defines procedural queries.",
                "SQL provides declarative data manipulation."
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "database.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        TextExtractionResult result = textExtractionService.extractText(file);

        assertThat(result).isNotNull();
        assertThat(result.getFilename()).isEqualTo("database.docx");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.DOCX);
        assertThat(result.getPageCount()).isEqualTo(3); // 3 paragraphs
        assertThat(result.getExtractedText())
                .contains("Database Management Systems Lecture 1.")
                .contains("Relational algebra defines procedural queries.")
                .contains("SQL provides declarative data manipulation.");
        assertThat(result.getExtractedText()).contains("\n\n");
    }

    @Test
    @DisplayName("Should extract text from DOCX with tables")
    void extractDocxWithTableSuccessfully() throws IOException {
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText("Table of Course Modules");

            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Module 1");
            table.getRow(0).getCell(1).setText("Artificial Intelligence");
            table.getRow(1).getCell(0).setText("Module 2");
            table.getRow(1).getCell(1).setText("Machine Learning");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            docxBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "modules.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        TextExtractionResult result = textExtractionService.extractText(file);

        assertThat(result.getExtractedText())
                .contains("Table of Course Modules")
                .contains("Module 1")
                .contains("Artificial Intelligence");
    }

    @Test
    @DisplayName("Should reject empty or null file")
    void extractEmptyFileShouldThrowException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> textExtractionService.extractText(emptyFile))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("Cannot extract text from empty or missing file");

        assertThatThrownBy(() -> textExtractionService.extractText(null))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("Cannot extract text from empty or missing file");
    }

    @Test
    @DisplayName("Should reject file without extension or unsupported extension")
    void unsupportedExtensionShouldThrowException() {
        MockMultipartFile invalidExt = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "Hello".getBytes()
        );

        assertThatThrownBy(() -> textExtractionService.extractText(invalidExt))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("Unsupported document extension");
    }

    @Test
    @DisplayName("Should fail gracefully with DocumentExtractionException on corrupt PDF data")
    void corruptPdfShouldThrowDocumentExtractionException() {
        MockMultipartFile corruptFile = new MockMultipartFile(
                "file",
                "corrupt.pdf",
                "application/pdf",
                "Not a valid PDF binary header".getBytes()
        );

        assertThatThrownBy(() -> textExtractionService.extractText(corruptFile))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("Failed to extract text from PDF document");
    }

    @Test
    @DisplayName("Should fail gracefully with DocumentExtractionException on corrupt DOCX data")
    void corruptDocxShouldThrowDocumentExtractionException() {
        MockMultipartFile corruptFile = new MockMultipartFile(
                "file",
                "corrupt.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "Not a valid PK zip archive".getBytes()
        );

        assertThatThrownBy(() -> textExtractionService.extractText(corruptFile))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("Failed to extract text from DOCX document");
    }

    // Helper to generate real PDF documents using Apache PDFBox
    private byte[] createPdf(String[] pagesText) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String pageText : pagesText) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(pageText);
                    cs.endText();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    // Helper to generate real DOCX documents using Apache POI
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
}
