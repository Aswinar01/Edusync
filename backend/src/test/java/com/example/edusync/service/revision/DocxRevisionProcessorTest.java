package com.example.edusync.service.revision;

import com.example.edusync.model.DocumentRevisionItem;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxRevisionProcessorTest {

    private DocxRevisionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DocxRevisionProcessor(new DocxSentenceLocator());
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

    private SentenceReviewItem createSessionItem(int id, String original, String proposed, ReviewStatus status) {
        SentenceReviewItem item = new SentenceReviewItem();
        item.setSentenceId(id);
        item.setOriginalSentence(original);
        item.setPipelineStatus(PipelineStatus.VERIFIED_UPDATE_PROPOSED);
        item.setProposalStatus(ProposalStatus.PROPOSED);
        item.setProposedSentence(proposed);
        item.setSourceUrl("https://endoflife.date/java");
        item.setOfficialReferenceUrl("https://oracle.com/java");
        item.setVerifiedInformation("Java 21 is latest LTS");
        item.setReviewStatus(status);
        item.setDecision(status == ReviewStatus.APPROVED ? ReviewDecision.APPROVE : ReviewDecision.REJECT);
        item.setReviewedAt("2026-09-12T00:00:00Z");
        return item;
    }

    @Test
    @DisplayName("Revise real DOCX: single run replacement sets yellow highlight, text and citation")
    void testReviseSingleRunDocx() throws IOException {
        String original = "Java 17 is the current LTS version.";
        byte[] originalBytes = createDocx(List.of(List.of(original)));
        byte[] originalSnapshot = Arrays.copyOf(originalBytes, originalBytes.length);

        SentenceReviewItem item = createSessionItem(1, original, "Java 21 is the current LTS version.", ReviewStatus.APPROVED);
        DocumentRevisionItem revItem = DocumentRevisionItem.fromApprovedSentence(item);

        byte[] revisedBytes = processor.revise(originalBytes, List.of(revItem), List.of(item));

        // Original bytes untouched
        assertThat(originalBytes).isEqualTo(originalSnapshot);
        assertThat(revisedBytes).isNotEqualTo(originalBytes);

        // Verify revised DOCX in POI
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(revisedBytes))) {
            XWPFParagraph p = doc.getParagraphs().get(0);
            assertThat(p.getText()).contains("Java 21 is the current LTS version.");
            assertThat(p.getText()).contains("[Source: Java 21 is latest LTS | Ref: https://oracle.com/java]");

            // Verify highlight color
            boolean foundYellowHighlight = false;
            for (XWPFRun r : p.getRuns()) {
                if (r.getTextHighlightColor() != null && "yellow".equalsIgnoreCase(r.getTextHighlightColor().toString())) {
                    foundYellowHighlight = true;
                    assertThat(r.getText(0)).contains("Java 21 is the current LTS version.");
                }
            }
            assertThat(foundYellowHighlight).isTrue();
        }
    }

    @Test
    @DisplayName("Revise real DOCX: multi-run replacement preserves prefix/suffix and highlights replacement")
    void testReviseMultiRunDocx() throws IOException {
        byte[] originalBytes = createDocx(List.of(
                List.of("Intro prefix: ", "Java 17 is the ", "current LTS release. ", "Outro suffix.")
        ));
        byte[] originalSnapshot = Arrays.copyOf(originalBytes, originalBytes.length);

        SentenceReviewItem item = createSessionItem(1, "Java 17 is the current LTS release.", "Java 21 is the current LTS release.", ReviewStatus.APPROVED);
        DocumentRevisionItem revItem = DocumentRevisionItem.fromApprovedSentence(item);

        byte[] revisedBytes = processor.revise(originalBytes, List.of(revItem), List.of(item));

        assertThat(originalBytes).isEqualTo(originalSnapshot);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(revisedBytes))) {
            XWPFParagraph p = doc.getParagraphs().get(0);
            assertThat(p.getText()).contains("Intro prefix: ");
            assertThat(p.getText()).contains("Java 21 is the current LTS release.");
            assertThat(p.getText()).contains("Outro suffix.");
            assertThat(p.getText()).contains("[Source: Java 21 is latest LTS");
        }
    }

    @Test
    @DisplayName("Zero approved updates returns clone of original DOCX bytes")
    void testZeroApprovedUpdatesReturnsClone() throws IOException {
        byte[] originalBytes = createDocx(List.of(List.of("Paragraph content.")));
        byte[] originalSnapshot = Arrays.copyOf(originalBytes, originalBytes.length);

        byte[] revised = processor.revise(originalBytes, List.of(), List.of());

        assertThat(revised).isEqualTo(originalSnapshot);
        assertThat(originalBytes).isEqualTo(originalSnapshot);
    }

    @Test
    @DisplayName("Unlocatable sentence throws SentenceLocationException")
    void testUnlocatableSentenceFails() throws IOException {
        byte[] originalBytes = createDocx(List.of(List.of("Docx text.")));
        SentenceReviewItem item = createSessionItem(1, "Unmatched text.", "Prop.", ReviewStatus.APPROVED);
        DocumentRevisionItem revItem = DocumentRevisionItem.fromApprovedSentence(item);

        assertThatThrownBy(() -> processor.revise(originalBytes, List.of(revItem), List.of(item)))
                .isInstanceOf(SentenceLocationException.class);
    }
}
