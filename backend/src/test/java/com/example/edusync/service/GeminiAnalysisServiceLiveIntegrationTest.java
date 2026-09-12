package com.example.edusync.service;

import com.example.edusync.config.GeminiConfig;
import com.example.edusync.config.GeminiProperties;
import com.example.edusync.model.SentenceAnalysisRequest;
import com.example.edusync.model.SentenceAnalysisResult;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiAnalysisServiceLiveIntegrationTest {

    @Test
    @DisplayName("Live Integration: Run analysis against actual Gemini API when GEMINI_API_KEY is present")
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = "^(?!your_gemini_api_key_here$).+")
    void testLiveGeminiAnalysis() {
        GeminiProperties properties = new GeminiProperties();
        properties.init(); // reads from env or .env if present

        assertThat(properties.isKeyConfigured())
                .as("GEMINI_API_KEY must be configured for live integration test")
                .isTrue();

        GeminiConfig config = new GeminiConfig(properties);
        RestClient restClient = config.geminiRestClient(config.geminiRestClientBuilder());
        ObjectMapper objectMapper = new ObjectMapper();

        GeminiAnalysisService service = new GeminiAnalysisService(restClient, properties, objectMapper);

        // Test 1: Outdated claim
        SentenceAnalysisResult resultOutdated = service.analyzeSentence(
                new SentenceAnalysisRequest(1, "Python 2.7 is the currently supported standard release of Python.")
        );

        System.out.println("\n========== LIVE GEMINI TEST (Outdated claim) ==========");
        System.out.println("Sentence: " + resultOutdated.getSentenceText());
        System.out.println("Potentially Outdated: " + resultOutdated.isPotentiallyOutdated());
        System.out.println("Reason: " + resultOutdated.getReason());
        System.out.println("Status: " + resultOutdated.getStatus());
        System.out.println("=======================================================\n");

        assertThat(resultOutdated.getStatus()).isEqualTo("SUCCESS");
        assertThat(resultOutdated.isPotentiallyOutdated()).isTrue();
        assertThat(resultOutdated.getReason()).isNotBlank();

        // Test 2: Timeless claim
        SentenceAnalysisResult resultTimeless = service.analyzeSentence(
                new SentenceAnalysisRequest(2, "In a right-angled triangle, the square of the hypotenuse is equal to the sum of the squares of the other two sides.")
        );

        System.out.println("========== LIVE GEMINI TEST (Timeless claim) ==========");
        System.out.println("Sentence: " + resultTimeless.getSentenceText());
        System.out.println("Potentially Outdated: " + resultTimeless.isPotentiallyOutdated());
        System.out.println("Reason: " + resultTimeless.getReason());
        System.out.println("Status: " + resultTimeless.getStatus());
        System.out.println("=======================================================\n");

        assertThat(resultTimeless.getStatus()).isEqualTo("SUCCESS");
        assertThat(resultTimeless.isPotentiallyOutdated()).isFalse();
        assertThat(resultTimeless.getReason()).isNotBlank();
    }
}
