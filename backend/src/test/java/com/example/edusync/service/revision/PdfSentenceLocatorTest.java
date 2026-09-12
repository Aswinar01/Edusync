package com.example.edusync.service.revision;

import com.example.edusync.model.DocumentLocation;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ReviewDecision;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.SentenceReviewItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfSentenceLocatorTest {

    private PdfSentenceLocator locator;

    @BeforeEach
    void setUp() {
        locator = new PdfSentenceLocator();
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

    private SentenceReviewItem createItem(int id, String original) {
        SentenceReviewItem item = new SentenceReviewItem();
        item.setSentenceId(id);
        item.setOriginalSentence(original);
        item.setPipelineStatus(PipelineStatus.VERIFIED_UPDATE_PROPOSED);
        item.setProposalStatus(ProposalStatus.PROPOSED);
        item.setProposedSentence("Proposed update " + id);
        item.setSourceUrl("https://source.org");
        item.setOfficialReferenceUrl("https://official.org");
        item.setVerifiedInformation("Evidence");
        item.setReviewStatus(ReviewStatus.APPROVED);
        item.setDecision(ReviewDecision.APPROVE);
        item.setReviewedAt("2026-09-12T00:00:00Z");
        return item;
    }

    @Test
    @DisplayName("Locate single sentence in PDF and resolve bounding box")
    void testLocateSingleSentence() throws IOException {
        byte[] pdf = createPdf(new String[]{"Java 17 is the latest LTS release."});

        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new org.apache.pdfbox.io.RandomAccessReadBuffer(pdf))) {
            List<SentenceReviewItem> items = List.of(createItem(1, "Java 17 is the latest LTS release."));
            Map<Integer, DocumentLocation> locs = locator.locateAll(doc, items);

            assertThat(locs).containsKey(1);
            DocumentLocation loc = locs.get(1);
            assertThat(loc.getPageNumber()).isEqualTo(1);
            assertThat(loc.getLineBoxes()).isNotEmpty();
            assertThat(loc.getLineBoxes().get(0).getX()).isGreaterThanOrEqualTo(45f);
            assertThat(loc.getLineBoxes().get(0).getWidth()).isGreaterThan(50f);
        }
    }

    @Test
    @DisplayName("Locate multiple sentences sequentially with advancing cursor")
    void testLocateMultipleSentencesSequentially() throws IOException {
        byte[] pdf = createPdf(new String[]{
                "Sentence number one.",
                "Sentence number two follows."
        });

        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new org.apache.pdfbox.io.RandomAccessReadBuffer(pdf))) {
            List<SentenceReviewItem> items = List.of(
                    createItem(1, "Sentence number one."),
                    createItem(2, "Sentence number two follows.")
            );

            Map<Integer, DocumentLocation> locs = locator.locateAll(doc, items);
            assertThat(locs).containsKeys(1, 2);
            assertThat(locs.get(1).getLineBoxes().get(0).getY())
                    .isGreaterThan(locs.get(2).getLineBoxes().get(0).getY()); // line 1 is higher up on page
        }
    }

    @Test
    @DisplayName("Defensively handle duplicate sentences using sequential cursor")
    void testDuplicateSentencesMappedSequentially() throws IOException {
        byte[] pdf = createPdf(new String[]{
                "Duplicate header note.",
                "Some middle content.",
                "Duplicate header note."
        });

        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new org.apache.pdfbox.io.RandomAccessReadBuffer(pdf))) {
            List<SentenceReviewItem> items = List.of(
                    createItem(1, "Duplicate header note."),
                    createItem(2, "Some middle content."),
                    createItem(3, "Duplicate header note.")
            );

            Map<Integer, DocumentLocation> locs = locator.locateAll(doc, items);
            assertThat(locs).containsKeys(1, 2, 3);

            float y1 = locs.get(1).getLineBoxes().get(0).getY();
            float y2 = locs.get(2).getLineBoxes().get(0).getY();
            float y3 = locs.get(3).getLineBoxes().get(0).getY();

            // y1 > y2 > y3 (top to bottom)
            assertThat(y1).isGreaterThan(y2);
            assertThat(y2).isGreaterThan(y3);
        }
    }

    @Test
    @DisplayName("Whitespace normalization matches sentences with varying spaces")
    void testWhitespaceNormalization() throws IOException {
        byte[] pdf = createPdf(new String[]{"WordA    WordB    WordC."});

        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new org.apache.pdfbox.io.RandomAccessReadBuffer(pdf))) {
            List<SentenceReviewItem> items = List.of(createItem(1, "WordA WordB WordC."));
            Map<Integer, DocumentLocation> locs = locator.locateAll(doc, items);
            assertThat(locs).containsKey(1);
        }
    }

    @Test
    @DisplayName("Missing sentence throws SentenceLocationException")
    void testMissingSentenceThrowsException() throws IOException {
        byte[] pdf = createPdf(new String[]{"Real document content."});

        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new org.apache.pdfbox.io.RandomAccessReadBuffer(pdf))) {
            List<SentenceReviewItem> items = List.of(createItem(1, "Completely fictitious sentence."));
            assertThatThrownBy(() -> locator.locateAll(doc, items))
                    .isInstanceOf(SentenceLocationException.class)
                    .hasMessageContaining("Could not locate sentence ID 1 in PDF document text");
        }
    }
}
