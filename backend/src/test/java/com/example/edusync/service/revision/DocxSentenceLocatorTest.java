package com.example.edusync.service.revision;

import com.example.edusync.model.DocumentLocation;
import com.example.edusync.model.PipelineStatus;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ReviewDecision;
import com.example.edusync.model.ReviewStatus;
import com.example.edusync.model.SentenceReviewItem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxSentenceLocatorTest {

    private DocxSentenceLocator locator;

    @BeforeEach
    void setUp() {
        locator = new DocxSentenceLocator();
    }

    private byte[] createDocx(List<List<String>> paragraphsWithRuns) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (List<String> runTexts : paragraphsWithRuns) {
                XWPFParagraph p = doc.createParagraph();
                for (String runText : runTexts) {
                    XWPFRun r = p.createRun();
                    r.setText(runText);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
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
    @DisplayName("Locate sentence contained entirely within a single run")
    void testLocateSentenceInSingleRun() throws IOException {
        byte[] docx = createDocx(List.of(
                List.of("Introductory prefix. Java 17 is current. Concluding suffix.")
        ));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<SentenceReviewItem> items = List.of(createItem(1, "Java 17 is current."));
            Map<Integer, DocumentLocation> locs = locator.locateAll(doc, items);

            assertThat(locs).containsKey(1);
            DocumentLocation loc = locs.get(1);
            assertThat(loc.getParagraphIndex()).isEqualTo(0);
            assertThat(loc.getStartRunIndex()).isEqualTo(0);
            assertThat(loc.getEndRunIndex()).isEqualTo(0);
            assertThat(loc.getStartOffsetInRun()).isEqualTo(21);
            assertThat(loc.getEndOffsetInRun()).isEqualTo(40);
        }
    }

    @Test
    @DisplayName("Locate sentence spanning multiple runs in a paragraph")
    void testLocateSentenceSpanningMultipleRuns() throws IOException {
        byte[] docx = createDocx(List.of(
                List.of("The Java ", "version 17 ", "is the LTS release.")
        ));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<SentenceReviewItem> items = List.of(createItem(1, "The Java version 17 is the LTS release."));
            Map<Integer, DocumentLocation> locs = locator.locateAll(doc, items);

            assertThat(locs).containsKey(1);
            DocumentLocation loc = locs.get(1);
            assertThat(loc.getParagraphIndex()).isEqualTo(0);
            assertThat(loc.getStartRunIndex()).isEqualTo(0);
            assertThat(loc.getEndRunIndex()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Locate sentences across multiple paragraphs sequentially")
    void testLocateMultipleSentencesAcrossParagraphs() throws IOException {
        byte[] docx = createDocx(List.of(
                List.of("Paragraph one sentence."),
                List.of("Paragraph two sentence.")
        ));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<SentenceReviewItem> items = List.of(
                    createItem(1, "Paragraph one sentence."),
                    createItem(2, "Paragraph two sentence.")
            );
            Map<Integer, DocumentLocation> locs = locator.locateAll(doc, items);

            assertThat(locs).containsKeys(1, 2);
            assertThat(locs.get(1).getParagraphIndex()).isEqualTo(0);
            assertThat(locs.get(2).getParagraphIndex()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Missing sentence in DOCX throws SentenceLocationException")
    void testMissingSentenceThrowsException() throws IOException {
        byte[] docx = createDocx(List.of(
                List.of("Only paragraph in document.")
        ));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<SentenceReviewItem> items = List.of(createItem(1, "Non-existent sentence."));
            assertThatThrownBy(() -> locator.locateAll(doc, items))
                    .isInstanceOf(SentenceLocationException.class)
                    .hasMessageContaining("Could not locate sentence ID 1 in DOCX paragraphs");
        }
    }
}
