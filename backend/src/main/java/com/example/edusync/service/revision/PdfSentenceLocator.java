package com.example.edusync.service.revision;

import com.example.edusync.model.DocumentLocation;
import com.example.edusync.model.SentenceReviewItem;
import com.example.edusync.model.TextRectangle;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves sentence provenance to physical PDF page numbers and line bounding boxes.
 * Uses character-level coordinate tracking with defensive whitespace normalization
 * and advancing cursors to prevent false collisions on duplicate sentences.
 */
@Component
public class PdfSentenceLocator {

    private static final Logger log = LoggerFactory.getLogger(PdfSentenceLocator.class);

    public Map<Integer, DocumentLocation> locateAll(PDDocument document, List<SentenceReviewItem> allItems) {
        if (document == null) {
            throw new SentenceLocationException("Cannot locate sentences in null PDDocument.");
        }
        if (allItems == null || allItems.isEmpty()) {
            return Collections.emptyMap();
        }

        TrackingPdfStripper stripper;
        try {
            stripper = new TrackingPdfStripper();
            stripper.setSortByPosition(true);
            stripper.getText(document);
        } catch (IOException e) {
            throw new SentenceLocationException("Failed to extract glyph coordinates from PDF: " + e.getMessage(), e);
        }

        List<TrackedChar> trackedChars = stripper.getTrackedChars();
        if (trackedChars.isEmpty()) {
            throw new SentenceLocationException("No readable text positions found in PDF document.");
        }

        // Build normalized text with character mapping back to raw index
        StringBuilder fullRaw = new StringBuilder(trackedChars.size());
        for (TrackedChar tc : trackedChars) {
            fullRaw.append(tc.ch);
        }

        StringBuilder normDoc = new StringBuilder();
        List<Integer> normToRaw = new ArrayList<>();
        boolean prevWhitespace = false;
        for (int i = 0; i < fullRaw.length(); i++) {
            char c = fullRaw.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!prevWhitespace) {
                    normDoc.append(' ');
                    normToRaw.add(i);
                    prevWhitespace = true;
                }
            } else {
                normDoc.append(c);
                normToRaw.add(i);
                prevWhitespace = false;
            }
        }

        String docString = normDoc.toString();
        int cursor = 0;
        Map<Integer, DocumentLocation> locations = new HashMap<>();

        for (SentenceReviewItem item : allItems) {
            if (item == null || item.getOriginalSentence() == null || item.getOriginalSentence().isBlank()) {
                continue;
            }

            String normTarget = normalizeWhitespace(item.getOriginalSentence());
            if (normTarget.isEmpty()) {
                continue;
            }

            int foundAt = docString.indexOf(normTarget, cursor);
            if (foundAt == -1) {
                // Defensive fallback: check from start if document segmentation caused reordering, but warn
                foundAt = docString.indexOf(normTarget);
                if (foundAt == -1) {
                    throw new SentenceLocationException(String.format(
                            "Could not locate sentence ID %d in PDF document text: '%s'",
                            item.getSentenceId(), item.getOriginalSentence()));
                }
            }

            cursor = foundAt + normTarget.length();

            int rawStart = normToRaw.get(foundAt);
            int rawEnd = normToRaw.get(foundAt + normTarget.length() - 1);

            List<TrackedChar> matched = new ArrayList<>();
            int targetPage = -1;
            float targetPageHeight = 792f;

            for (int i = rawStart; i <= rawEnd; i++) {
                TrackedChar tc = trackedChars.get(i);
                if (tc.textPosition != null) {
                    matched.add(tc);
                    if (targetPage == -1) {
                        targetPage = tc.pageNumber;
                        targetPageHeight = tc.pageHeight;
                    }
                }
            }

            if (matched.isEmpty() || targetPage == -1) {
                throw new SentenceLocationException(String.format(
                        "No character coordinates resolved for sentence ID %d: '%s'",
                        item.getSentenceId(), item.getOriginalSentence()));
            }

            List<TextRectangle> lineBoxes = computeLineBoxes(matched, targetPageHeight);
            if (lineBoxes.isEmpty()) {
                throw new SentenceLocationException(String.format(
                        "Computed zero line bounding boxes for sentence ID %d: '%s'",
                        item.getSentenceId(), item.getOriginalSentence()));
            }

            locations.put(item.getSentenceId(), DocumentLocation.forPdf(targetPage, lineBoxes));
        }

        return locations;
    }

    private List<TextRectangle> computeLineBoxes(List<TrackedChar> matched, float pageHeight) {
        List<List<TrackedChar>> lines = new ArrayList<>();
        List<TrackedChar> currentLine = new ArrayList<>();
        float currentY = -1;

        for (TrackedChar tc : matched) {
            float y = tc.textPosition.getYDirAdj();
            if (currentLine.isEmpty()) {
                currentLine.add(tc);
                currentY = y;
            } else if (Math.abs(y - currentY) <= 3.5f) {
                currentLine.add(tc);
            } else {
                lines.add(currentLine);
                currentLine = new ArrayList<>();
                currentLine.add(tc);
                currentY = y;
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }

        List<TextRectangle> boxes = new ArrayList<>();
        for (List<TrackedChar> line : lines) {
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float yDir = line.get(0).textPosition.getYDirAdj();
            float maxFontSize = 0f;
            float maxHeight = 0f;

            for (TrackedChar tc : line) {
                TextPosition tp = tc.textPosition;
                float x = tp.getXDirAdj();
                float w = tp.getWidthDirAdj();
                if (x < minX) minX = x;
                if (x + w > maxX) maxX = x + w;
                if (tp.getFontSizeInPt() > maxFontSize) maxFontSize = tp.getFontSizeInPt();
                if (tp.getHeightDir() > maxHeight) maxHeight = tp.getHeightDir();
            }

            if (maxFontSize <= 0) maxFontSize = 12f;
            if (maxHeight <= 0) maxHeight = maxFontSize;

            float width = Math.max(1f, maxX - minX);
            float height = Math.max(maxFontSize, maxHeight);

            // In user space, bottom coordinate of the text line
            float pdfY = pageHeight - yDir - (height * 0.25f);

            boxes.add(new TextRectangle(minX, pdfY, width, height));
        }

        return boxes;
    }

    private String normalizeWhitespace(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ");
    }

    private static class TrackedChar {
        final char ch;
        final TextPosition textPosition;
        final int pageNumber;
        final float pageHeight;

        TrackedChar(char ch, TextPosition textPosition, int pageNumber, float pageHeight) {
            this.ch = ch;
            this.textPosition = textPosition;
            this.pageNumber = pageNumber;
            this.pageHeight = pageHeight;
        }
    }

    private static class TrackingPdfStripper extends PDFTextStripper {
        private final List<TrackedChar> trackedChars = new ArrayList<>();

        TrackingPdfStripper() throws IOException {
            super();
        }

        List<TrackedChar> getTrackedChars() {
            return trackedChars;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            int pageNo = getCurrentPageNo();
            float pageH = getCurrentPage() != null && getCurrentPage().getMediaBox() != null
                    ? getCurrentPage().getMediaBox().getHeight() : 792f;
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    TextPosition tp = (textPositions != null && i < textPositions.size()) ? textPositions.get(i) : null;
                    trackedChars.add(new TrackedChar(c, tp, pageNo, pageH));
                }
            }
            super.writeString(text, textPositions);
        }

        @Override
        protected void writeWordSeparator() throws IOException {
            int pageNo = getCurrentPageNo();
            trackedChars.add(new TrackedChar(' ', null, pageNo, 792f));
            super.writeWordSeparator();
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            int pageNo = getCurrentPageNo();
            trackedChars.add(new TrackedChar('\n', null, pageNo, 792f));
            super.writeLineSeparator();
        }
    }
}
