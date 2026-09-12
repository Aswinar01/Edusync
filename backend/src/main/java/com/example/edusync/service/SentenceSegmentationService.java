package com.example.edusync.service;

import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.TextExtractionResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class SentenceSegmentationService {

    // Titles and honorifics that precede proper names and never terminate a sentence
    private static final Set<String> TITLES = Set.of(
            "dr", "mr", "mrs", "ms", "prof", "sr", "jr", "rev", "gen", "gov", "sen", "rep", "st", "col", "capt", "lt"
    );

    // Common abbreviations that should not trigger false sentence boundaries
    private static final Set<String> ABBREVIATIONS = Set.of(
            "e.g", "eg", "i.e", "ie", "etc", "al", "vs", "cf", "viz", "ibid", "fig", "figs",
            "no", "nos", "vol", "vols", "ed", "eds", "pp", "co", "corp", "inc", "ltd", "dept", "univ"
    );

    /**
     * Segments the extracted text from a TextExtractionResult into an ordered list of DocumentSentence objects.
     */
    public List<DocumentSentence> segment(TextExtractionResult extractionResult) {
        if (extractionResult == null) {
            return Collections.emptyList();
        }
        return segment(extractionResult.getExtractedText());
    }

    /**
     * Segments raw prose text into an ordered list of DocumentSentence objects with sequential IDs.
     */
    public List<DocumentSentence> segment(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<DocumentSentence> sentences = new ArrayList<>();
        int currentSentenceId = 1;

        // Split text across paragraph boundaries (two or more newlines)
        String[] paragraphs = text.split("(?:\r?\n\\s*){2,}");

        for (String paragraph : paragraphs) {
            String trimmedPara = paragraph.trim();
            if (trimmedPara.isEmpty()) {
                continue;
            }

            List<String> rawSentences = segmentParagraph(trimmedPara);
            for (String rawSentence : rawSentences) {
                sentences.add(new DocumentSentence(currentSentenceId++, rawSentence));
            }
        }

        return sentences;
    }

    /**
     * Segments a single paragraph into sentence strings.
     */
    private List<String> segmentParagraph(String paragraph) {
        List<String> result = new ArrayList<>();
        int length = paragraph.length();
        int sentenceStart = 0;

        for (int i = 0; i < length; i++) {
            char c = paragraph.charAt(i);

            if (c == '.' || c == '?' || c == '!') {
                // Include trailing quotes or brackets (e.g. ." or .') or !)
                int endOfPunctuation = i;
                while (endOfPunctuation + 1 < length && isClosingPunctuation(paragraph.charAt(endOfPunctuation + 1))) {
                    endOfPunctuation++;
                }

                // Look ahead past any whitespace
                boolean atEnd = (endOfPunctuation + 1 >= length);
                int nextCharIndex = endOfPunctuation + 1;
                while (nextCharIndex < length && Character.isWhitespace(paragraph.charAt(nextCharIndex))) {
                    nextCharIndex++;
                }

                boolean followedByWhitespace = (nextCharIndex > endOfPunctuation + 1);

                if (atEnd || followedByWhitespace) {
                    if (c == '.' && isFalseBoundary(paragraph, sentenceStart, i, nextCharIndex)) {
                        continue;
                    }

                    String sentence = paragraph.substring(sentenceStart, endOfPunctuation + 1).trim();
                    if (!sentence.isEmpty()) {
                        result.add(sentence);
                    }

                    sentenceStart = nextCharIndex;
                    i = sentenceStart - 1;
                }
            }
        }

        if (sentenceStart < length) {
            String remainder = paragraph.substring(sentenceStart).trim();
            if (!remainder.isEmpty()) {
                result.add(remainder);
            }
        }

        return result;
    }

    /**
     * Identifies closing quotes, brackets, and parenthesis that belong with the preceding punctuation.
     */
    private boolean isClosingPunctuation(char c) {
        return c == '"' || c == '\'' || c == ')' || c == ']' || c == '}' || c == '”' || c == '’' || c == '»';
    }

    /**
     * Checks whether a period (.) is part of an abbreviation, decimal number, list marker, or ellipsis.
     */
    private boolean isFalseBoundary(String text, int sentenceStart, int dotIndex, int nextCharIndex) {
        // 1. Numbered list prefix (e.g. "1.", "2.", "A.") at the start of a sentence candidate
        String prefixCandidate = text.substring(sentenceStart, dotIndex).trim();
        if (prefixCandidate.matches("^\\(?\\d+$") || prefixCandidate.matches("^\\(?[A-Za-z]$")) {
            return true;
        }

        // 2. Decimals or embedded version numbers (e.g. 3.14, 4.1.0)
        if (dotIndex > 0 && Character.isDigit(text.charAt(dotIndex - 1))) {
            if (dotIndex + 1 < text.length() && Character.isDigit(text.charAt(dotIndex + 1))) {
                return true;
            }
        }

        // 3. Ellipsis (... or ..)
        if (dotIndex + 1 < text.length() && text.charAt(dotIndex + 1) == '.') {
            return true;
        }
        if (dotIndex > 0 && text.charAt(dotIndex - 1) == '.') {
            if (nextCharIndex < text.length() && Character.isLowerCase(text.charAt(nextCharIndex))) {
                return true;
            }
        }

        // 4. Preceding token analysis (abbreviations, titles, single initials)
        int wordStart = dotIndex - 1;
        while (wordStart >= 0 && (Character.isLetterOrDigit(text.charAt(wordStart)) || text.charAt(wordStart) == '.')) {
            wordStart--;
        }
        wordStart++;

        String token = text.substring(wordStart, dotIndex).toLowerCase();
        String tokenNoDots = token.replace(".", "");

        // Titles (Dr., Mr., Mrs., Prof.) never terminate a sentence
        if (TITLES.contains(token) || TITLES.contains(tokenNoDots)) {
            return true;
        }

        // Single capital letter initial (e.g. "A. Smith", "J. K. Rowling")
        if (token.length() == 1 && Character.isUpperCase(text.charAt(wordStart))) {
            return true;
        }

        // Common abbreviations (e.g., i.e., etc., al.)
        if (ABBREVIATIONS.contains(token) || ABBREVIATIONS.contains(tokenNoDots)) {
            if (nextCharIndex >= text.length()) {
                return false;
            }
            char nextChar = text.charAt(nextCharIndex);
            if (Character.isLowerCase(nextChar) || nextChar == ',' || nextChar == ';') {
                return true;
            }
            if (tokenNoDots.equals("eg") || tokenNoDots.equals("ie")) {
                return true;
            }
            if (tokenNoDots.equals("etc") && Character.isUpperCase(nextChar)) {
                return false;
            }
            return true;
        }

        // If the following non-whitespace character is lowercase, it is generally not a sentence boundary
        if (nextCharIndex < text.length() && Character.isLowerCase(text.charAt(nextCharIndex))) {
            return true;
        }

        return false;
    }
}
