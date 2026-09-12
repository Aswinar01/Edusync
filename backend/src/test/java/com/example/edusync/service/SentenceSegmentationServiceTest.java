package com.example.edusync.service;

import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.DocumentType;
import com.example.edusync.model.TextExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceSegmentationServiceTest {

    private SentenceSegmentationService segmentationService;

    @BeforeEach
    void setUp() {
        segmentationService = new SentenceSegmentationService();
    }

    @Test
    @DisplayName("Should segment simple single sentence")
    void testSimpleSentence() {
        String text = "This is a single declarative sentence.";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).getSentenceId()).isEqualTo(1);
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("This is a single declarative sentence.");
    }

    @Test
    @DisplayName("Should segment multiple consecutive sentences")
    void testMultipleSentences() {
        String text = "The quick brown fox jumps over the lazy dog. It then rests under the shade. Birds chirp in the trees.";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(3);
        assertThat(sentences.get(0).getSentenceId()).isEqualTo(1);
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("The quick brown fox jumps over the lazy dog.");
        assertThat(sentences.get(1).getSentenceId()).isEqualTo(2);
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("It then rests under the shade.");
        assertThat(sentences.get(2).getSentenceId()).isEqualTo(3);
        assertThat(sentences.get(2).getSentenceText()).isEqualTo("Birds chirp in the trees.");
    }

    @Test
    @DisplayName("Should correctly segment questions and exclamation sentences")
    void testQuestionAndExclamationSentences() {
        String text = "Did you understand the lecture? Yes, absolutely! Are there any questions? None at all!";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(4);
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("Did you understand the lecture?");
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("Yes, absolutely!");
        assertThat(sentences.get(2).getSentenceText()).isEqualTo("Are there any questions?");
        assertThat(sentences.get(3).getSentenceText()).isEqualTo("None at all!");
    }

    @Test
    @DisplayName("Should segment across paragraph boundaries")
    void testParagraphBoundaries() {
        String text = "First paragraph sentence.\n\nSecond paragraph sentence.\n\nThird paragraph sentence.";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(3);
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("First paragraph sentence.");
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("Second paragraph sentence.");
        assertThat(sentences.get(2).getSentenceText()).isEqualTo("Third paragraph sentence.");
    }

    @Test
    @DisplayName("Should avoid incorrectly splitting common abbreviations")
    void testAbbreviations() {
        String text = "Dr. Smith and Prof. Johnson discussed the syllabus (e.g. grading policies, i.e. rubrics, etc.) with Mr. Davis and Mrs. White.";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).getSentenceText())
                .isEqualTo("Dr. Smith and Prof. Johnson discussed the syllabus (e.g. grading policies, i.e. rubrics, etc.) with Mr. Davis and Mrs. White.");
    }

    @Test
    @DisplayName("Should handle abbreviation at the end of a sentence")
    void testAbbreviationAtSentenceEnd() {
        String text = "Please bring notebooks, pens, etc. Then we will begin.";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(2);
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("Please bring notebooks, pens, etc.");
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("Then we will begin.");
    }

    @Test
    @DisplayName("Should avoid incorrectly splitting decimal numbers")
    void testDecimalNumbers() {
        String text = "The mathematical constant pi is approximately 3.14. Average response time is 10.5 milliseconds.";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(2);
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("The mathematical constant pi is approximately 3.14.");
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("Average response time is 10.5 milliseconds.");
    }

    @Test
    @DisplayName("Should avoid incorrectly splitting version numbers")
    void testVersionNumbers() {
        String text = "The application is built on Spring Boot version 4.1.0 and Java 26. Next release will be version 4.2.0.";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(2);
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("The application is built on Spring Boot version 4.1.0 and Java 26.");
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("Next release will be version 4.2.0.");
    }

    @Test
    @DisplayName("Should handle multiple spaces and newlines between sentences")
    void testMultipleWhitespaceAndNewlines() {
        String text = "First sentence with irregular spaces.       \n\n\n     Second sentence after big gap.   Third sentence.";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(3);
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("First sentence with irregular spaces.");
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("Second sentence after big gap.");
        assertThat(sentences.get(2).getSentenceText()).isEqualTo("Third sentence.");
    }

    @Test
    @DisplayName("Should return empty list for empty or whitespace-only input")
    void testEmptyAndWhitespaceInput() {
        assertThat(segmentationService.segment((String) null)).isEmpty();
        assertThat(segmentationService.segment("")).isEmpty();
        assertThat(segmentationService.segment("   \t  \n  \r\n  ")).isEmpty();
        assertThat(segmentationService.segment((TextExtractionResult) null)).isEmpty();
    }

    @Test
    @DisplayName("Should maintain ordering and sequential sentence IDs")
    void testOrderingAndSequentialIds() {
        String text = "Sentence one. Sentence two. Sentence three! Sentence four? Sentence five.";
        List<DocumentSentence> sentences = segmentationService.segment(text);

        assertThat(sentences).hasSize(5);
        for (int i = 0; i < sentences.size(); i++) {
            assertThat(sentences.get(i).getSentenceId()).isEqualTo(i + 1);
        }
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("Sentence one.");
        assertThat(sentences.get(4).getSentenceText()).isEqualTo("Sentence five.");
    }

    @Test
    @DisplayName("Should segment TextExtractionResult object correctly")
    void testTextExtractionResultInput() {
        TextExtractionResult result = new TextExtractionResult(
                "notes.pdf",
                DocumentType.PDF,
                "Microservices architecture provides loose coupling. Each service scales independently."
        );

        List<DocumentSentence> sentences = segmentationService.segment(result);

        assertThat(sentences).hasSize(2);
        assertThat(sentences.get(0).getSentenceId()).isEqualTo(1);
        assertThat(sentences.get(0).getSentenceText()).isEqualTo("Microservices architecture provides loose coupling.");
        assertThat(sentences.get(1).getSentenceId()).isEqualTo(2);
        assertThat(sentences.get(1).getSentenceText()).isEqualTo("Each service scales independently.");
    }
}
