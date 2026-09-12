package com.example.edusync.service.revision;

import com.example.edusync.model.DocumentRevisionItem;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ReviewDecision;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.SentenceReviewItem;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfRevisionProcessorTest {

    private PdfRevisionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PdfRevisionProcessor(new PdfSentenceLocator());
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

    private SentenceReviewItem createSessionItem(int id, String original, String proposed, ReviewStatus status) {
        SentenceReviewItem item = new SentenceReviewItem();
        item.setSentenceId(id);
        item.setOriginalSentence(original);
        item.setPipelineStatus(PipelineStatus.VERIFIED_UPDATE_PROPOSED);
        item.setProposalStatus(ProposalStatus.PROPOSED);
        item.setProposedSentence(proposed);
        item.setSourceUrl("https://endoflife.date/java");
        item.setOfficialReferenceUrl("https://oracle.com/java");
        item.setVerifiedInformation("Verified evidence");
        item.setReviewStatus(status);
        item.setDecision(status == ReviewStatus.APPROVED ? ReviewDecision.APPROVE : ReviewDecision.REJECT);
        item.setReviewedAt("2026-09-12T00:00:00Z");
        return item;
    }

    @Test
    @DisplayName("Revise real PDF: produces valid revised PDF with approved text and footnote while preserving original")
    void testReviseRealPdf() throws IOException {
        String originalLine1 = "Java 17 is the current LTS version.";
        String originalLine2 = "Python 2.7 is widely supported.";
        byte[] originalBytes = createPdf(new String[]{originalLine1, originalLine2});
        byte[] originalBytesSnapshot = Arrays.copyOf(originalBytes, originalBytes.length);

        SentenceReviewItem item1 = createSessionItem(1, originalLine1, "Java 21 is the current LTS version.", ReviewStatus.APPROVED);
        SentenceReviewItem item2 = createSessionItem(2, originalLine2, "Python 3 is widely supported.", ReviewStatus.REJECTED);

        DocumentRevisionItem revItem1 = DocumentRevisionItem.fromApprovedSentence(item1);

        byte[] revisedBytes = processor.revise(originalBytes, List.of(revItem1), List.of(item1, item2));

        // 1. Original bytes must remain byte-for-byte untouched
        assertThat(originalBytes).isEqualTo(originalBytesSnapshot);

        // 2. Revised bytes must not be identical to original bytes
        assertThat(revisedBytes).isNotEqualTo(originalBytes);

        // 3. Revised bytes load cleanly in PDFBox
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(revisedBytes))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            PDFTextStripper stripper = new PDFTextStripper();
            String extracted = stripper.getText(doc);

            // Revised text and footnote are present
            assertThat(extracted).contains("Java 21 is the current LTS version.");
            assertThat(extracted).contains("[Update #1]");
            assertThat(extracted).contains("https://oracle.com/java");
        }
    }

    @Test
    @DisplayName("Zero approved updates returns clone of original PDF bytes without mutation")
    void testZeroApprovedUpdatesReturnsClone() throws IOException {
        byte[] originalBytes = createPdf(new String[]{"Unchanged content."});
        byte[] originalSnapshot = Arrays.copyOf(originalBytes, originalBytes.length);

        byte[] revised = processor.revise(originalBytes, List.of(), List.of());

        assertThat(revised).isEqualTo(originalSnapshot);
        assertThat(originalBytes).isEqualTo(originalSnapshot);
    }

    @Test
    @DisplayName("Unlocatable sentence fails with SentenceLocationException")
    void testUnlocatableSentenceFails() throws IOException {
        byte[] originalBytes = createPdf(new String[]{"Document text."});
        SentenceReviewItem item = createSessionItem(1, "Missing text.", "Proposed.", ReviewStatus.APPROVED);
        DocumentRevisionItem revItem = DocumentRevisionItem.fromApprovedSentence(item);

        assertThatThrownBy(() -> processor.revise(originalBytes, List.of(revItem), List.of(item)))
                .isInstanceOf(SentenceLocationException.class);
    }
}
