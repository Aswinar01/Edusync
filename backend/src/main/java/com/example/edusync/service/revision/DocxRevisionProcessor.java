package com.example.edusync.service.revision;

import com.example.edusync.model.DocumentLocation;
import com.example.edusync.model.DocumentRevisionItem;
import com.example.edusync.model.SentenceReviewItem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * DOCX revision processor utilizing Apache POI 5.2.5.
 * Performs surgical XML run replacement, sets yellow highlighting,
 * appends compact professional citation runs, and preserves all tables,
 * paragraph styles, and unaffected runs.
 */
@Service
public class DocxRevisionProcessor implements DocumentRevisionProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocxRevisionProcessor.class);

    private final DocxSentenceLocator docxSentenceLocator;

    public DocxRevisionProcessor(DocxSentenceLocator docxSentenceLocator) {
        this.docxSentenceLocator = docxSentenceLocator;
    }

    @Override
    public byte[] revise(byte[] originalBytes,
                         List<DocumentRevisionItem> approvedItems,
                         List<SentenceReviewItem> allSessionItems) throws RevisionException {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new RevisionException("Cannot revise empty or null DOCX bytes.");
        }

        if (approvedItems == null || approvedItems.isEmpty()) {
            log.info("Zero approved updates to apply; returning defensive copy of original DOCX bytes.");
            return originalBytes.clone();
        }

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(originalBytes))) {
            Map<Integer, DocumentLocation> locations = docxSentenceLocator.locateAll(document, allSessionItems);

            for (DocumentRevisionItem item : approvedItems) {
                DocumentLocation loc = locations.get(item.getSentenceId());
                if (loc == null) {
                    throw new SentenceLocationException(String.format(
                            "Cannot apply revision: no location resolved for approved sentence ID %d ('%s')",
                            item.getSentenceId(), item.getOriginalSentence()));
                }

                int pIdx = loc.getParagraphIndex();
                if (pIdx < 0 || pIdx >= document.getParagraphs().size()) {
                    throw new SentenceLocationException(String.format(
                            "Paragraph index %d out of bounds for DOCX with %d paragraphs (sentence ID %d)",
                            pIdx, document.getParagraphs().size(), item.getSentenceId()));
                }

                XWPFParagraph paragraph = document.getParagraphs().get(pIdx);
                applyRevisionToParagraph(paragraph, loc, item);
                log.info("Applied revision for sentence ID {} in DOCX paragraph {}.",
                        item.getSentenceId(), pIdx);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.write(baos);
            return baos.toByteArray();
        } catch (SentenceLocationException sle) {
            throw sle;
        } catch (Exception ex) {
            throw new RevisionException("Failed to generate revised DOCX document: " + ex.getMessage(), ex);
        }
    }

    private void applyRevisionToParagraph(XWPFParagraph paragraph, DocumentLocation loc, DocumentRevisionItem item) {
        int startRunIdx = loc.getStartRunIndex();
        int startOff = loc.getStartOffsetInRun();
        int endRunIdx = loc.getEndRunIndex();
        int endOff = loc.getEndOffsetInRun();

        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            XWPFRun newRun = paragraph.createRun();
            newRun.setText(item.getApprovedSentence());
            newRun.setTextHighlightColor("yellow");
            appendCitationRun(paragraph, item);
            return;
        }

        if (startRunIdx >= runs.size()) {
            startRunIdx = runs.size() - 1;
        }
        if (endRunIdx >= runs.size()) {
            endRunIdx = runs.size() - 1;
        }

        String approvedText = item.getApprovedSentence() != null ? item.getApprovedSentence() : "";

        if (startRunIdx == endRunIdx) {
            XWPFRun run = runs.get(startRunIdx);
            String full = run.getText(0);
            if (full == null) full = "";

            int safeStart = Math.min(startOff, full.length());
            int safeEnd = Math.min(endOff, full.length());
            if (safeStart > safeEnd) safeStart = safeEnd;

            String prefix = full.substring(0, safeStart);
            String suffix = full.substring(safeEnd);

            if (prefix.isEmpty() && suffix.isEmpty()) {
                run.setText(approvedText, 0);
                run.setTextHighlightColor("yellow");
                appendCitationRunAfter(paragraph, startRunIdx, item);
            } else {
                run.setText(prefix, 0);

                int insertIdx = startRunIdx + 1;
                XWPFRun revRun = paragraph.insertNewRun(insertIdx++);
                revRun.setText(approvedText);
                revRun.setTextHighlightColor("yellow");

                XWPFRun citeRun = paragraph.insertNewRun(insertIdx++);
                populateCitationRun(citeRun, item);

                if (!suffix.isEmpty()) {
                    XWPFRun sufRun = paragraph.insertNewRun(insertIdx);
                    sufRun.setText(suffix);
                }
            }
        } else {
            // Sentence spans multiple runs
            XWPFRun startRun = runs.get(startRunIdx);
            String startText = startRun.getText(0);
            if (startText == null) startText = "";
            int safeStart = Math.min(startOff, startText.length());
            String prefix = startText.substring(0, safeStart);
            startRun.setText(prefix, 0);

            // Clear intermediate runs
            for (int r = startRunIdx + 1; r < endRunIdx; r++) {
                if (r < runs.size()) {
                    runs.get(r).setText("", 0);
                }
            }

            XWPFRun endRun = runs.get(endRunIdx);
            String endText = endRun.getText(0);
            if (endText == null) endText = "";
            int safeEnd = Math.min(endOff, endText.length());
            String suffix = endText.substring(safeEnd);
            endRun.setText(suffix, 0);

            // Insert revised run and citation run
            int insertIdx = startRunIdx + 1;
            XWPFRun revRun = paragraph.insertNewRun(insertIdx++);
            revRun.setText(approvedText);
            revRun.setTextHighlightColor("yellow");

            XWPFRun citeRun = paragraph.insertNewRun(insertIdx);
            populateCitationRun(citeRun, item);
        }
    }

    private void appendCitationRunAfter(XWPFParagraph paragraph, int runIndex, DocumentRevisionItem item) {
        XWPFRun citeRun = paragraph.insertNewRun(runIndex + 1);
        populateCitationRun(citeRun, item);
    }

    private void appendCitationRun(XWPFParagraph paragraph, DocumentRevisionItem item) {
        XWPFRun citeRun = paragraph.createRun();
        populateCitationRun(citeRun, item);
    }

    private void populateCitationRun(XWPFRun run, DocumentRevisionItem item) {
        String note = item.getCitationNote() != null && !item.getCitationNote().isBlank()
                ? item.getCitationNote() : "Verified update";
        String citation = " [Source: " + note;
        if (item.getOfficialReferenceUrl() != null && !item.getOfficialReferenceUrl().isBlank()) {
            citation += " | Ref: " + item.getOfficialReferenceUrl();
        }
        citation += "]";

        run.setText(citation);
        run.setItalic(true);
        run.setFontSize(9);
        run.setColor("555555");
    }
}
