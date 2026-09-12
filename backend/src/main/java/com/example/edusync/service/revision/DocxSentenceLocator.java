package com.example.edusync.service.revision;

import com.example.edusync.model.DocumentLocation;
import com.example.edusync.model.SentenceReviewItem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves sentence provenance within a DOCX document to specific paragraphs,
 * runs, and character offset spans. Prevents naive string replacement and preserves
 * document XML structure.
 */
@Component
public class DocxSentenceLocator {

    private static final Logger log = LoggerFactory.getLogger(DocxSentenceLocator.class);

    public Map<Integer, DocumentLocation> locateAll(XWPFDocument document, List<SentenceReviewItem> allItems) {
        if (document == null) {
            throw new SentenceLocationException("Cannot locate sentences in null XWPFDocument.");
        }
        if (allItems == null || allItems.isEmpty()) {
            return Collections.emptyMap();
        }

        List<XWPFParagraph> paragraphs = document.getParagraphs();
        if (paragraphs.isEmpty()) {
            throw new SentenceLocationException("DOCX document contains no paragraphs.");
        }

        List<ParagraphMap> paragraphMaps = new ArrayList<>();
        for (int pIdx = 0; pIdx < paragraphs.size(); pIdx++) {
            XWPFParagraph p = paragraphs.get(pIdx);
            paragraphMaps.add(buildParagraphMap(pIdx, p));
        }

        Map<Integer, DocumentLocation> locations = new HashMap<>();
        int pCursor = 0;

        for (SentenceReviewItem item : allItems) {
            if (item == null || item.getOriginalSentence() == null || item.getOriginalSentence().isBlank()) {
                continue;
            }

            String normTarget = normalizeWhitespace(item.getOriginalSentence());
            if (normTarget.isEmpty()) {
                continue;
            }

            boolean found = false;
            // Search starting from current paragraph cursor
            for (int pIdx = pCursor; pIdx < paragraphMaps.size(); pIdx++) {
                ParagraphMap pMap = paragraphMaps.get(pIdx);
                int matchPos = pMap.normText.indexOf(normTarget, pMap.charCursor);
                if (matchPos != -1) {
                    pMap.charCursor = matchPos + normTarget.length();
                    pCursor = pIdx; // sentences are sequential; next sentence is in this paragraph or later

                    int rawStart = pMap.normToRaw.get(matchPos);
                    int rawEnd = pMap.normToRaw.get(matchPos + normTarget.length() - 1);

                    RunCharRef startRef = pMap.charRefs.get(rawStart);
                    RunCharRef endRef = pMap.charRefs.get(rawEnd);

                    DocumentLocation loc = DocumentLocation.forDocx(
                            pIdx,
                            startRef.runIndex,
                            startRef.offsetInRun,
                            endRef.runIndex,
                            endRef.offsetInRun + 1
                    );
                    locations.put(item.getSentenceId(), loc);
                    found = true;
                    break;
                }
            }

            // Fallback search across all paragraphs if paragraph structure reordered
            if (!found) {
                for (int pIdx = 0; pIdx < paragraphMaps.size(); pIdx++) {
                    ParagraphMap pMap = paragraphMaps.get(pIdx);
                    int matchPos = pMap.normText.indexOf(normTarget);
                    if (matchPos != -1) {
                        int rawStart = pMap.normToRaw.get(matchPos);
                        int rawEnd = pMap.normToRaw.get(matchPos + normTarget.length() - 1);

                        RunCharRef startRef = pMap.charRefs.get(rawStart);
                        RunCharRef endRef = pMap.charRefs.get(rawEnd);

                        DocumentLocation loc = DocumentLocation.forDocx(
                                pIdx,
                                startRef.runIndex,
                                startRef.offsetInRun,
                                endRef.runIndex,
                                endRef.offsetInRun + 1
                        );
                        locations.put(item.getSentenceId(), loc);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                throw new SentenceLocationException(String.format(
                        "Could not locate sentence ID %d in DOCX paragraphs: '%s'",
                        item.getSentenceId(), item.getOriginalSentence()));
            }
        }

        return locations;
    }

    private ParagraphMap buildParagraphMap(int pIdx, XWPFParagraph paragraph) {
        ParagraphMap pMap = new ParagraphMap();
        pMap.paragraphIndex = pIdx;

        StringBuilder fullRaw = new StringBuilder();
        List<RunCharRef> charRefs = new ArrayList<>();

        List<XWPFRun> runs = paragraph.getRuns();
        for (int rIdx = 0; rIdx < runs.size(); rIdx++) {
            XWPFRun run = runs.get(rIdx);
            String text = run.getText(0);
            if (text != null && !text.isEmpty()) {
                for (int cIdx = 0; cIdx < text.length(); cIdx++) {
                    fullRaw.append(text.charAt(cIdx));
                    charRefs.add(new RunCharRef(rIdx, cIdx));
                }
            }
        }

        pMap.rawText = fullRaw.toString();
        pMap.charRefs = charRefs;

        // Build whitespace-normalized text
        StringBuilder norm = new StringBuilder();
        List<Integer> normToRaw = new ArrayList<>();
        boolean prevWhitespace = false;
        for (int i = 0; i < fullRaw.length(); i++) {
            char c = fullRaw.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!prevWhitespace) {
                    norm.append(' ');
                    normToRaw.add(i);
                    prevWhitespace = true;
                }
            } else {
                norm.append(c);
                normToRaw.add(i);
                prevWhitespace = false;
            }
        }

        pMap.normText = norm.toString();
        pMap.normToRaw = normToRaw;
        pMap.charCursor = 0;

        return pMap;
    }

    private String normalizeWhitespace(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ");
    }

    private static class ParagraphMap {
        int paragraphIndex;
        String rawText;
        String normText;
        List<RunCharRef> charRefs;
        List<Integer> normToRaw;
        int charCursor;
    }

    private static class RunCharRef {
        final int runIndex;
        final int offsetInRun;

        RunCharRef(int runIndex, int offsetInRun) {
            this.runIndex = runIndex;
            this.offsetInRun = offsetInRun;
        }
    }
}
