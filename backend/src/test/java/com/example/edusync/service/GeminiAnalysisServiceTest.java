package com.example.edusync.service;

import com.example.edusync.config.GeminiProperties;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.SentenceAnalysisRequest;
import com.example.edusync.model.SentenceAnalysisResult;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiAnalysisServiceTest {

    private GeminiAnalysisService geminiAnalysisService;
    private MockRestServiceServer mockServer;
    private GeminiProperties geminiProperties;
    private ObjectMapper objectMapper;

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String EXPECTED_URI = API_URL + "/models/" + MODEL + ":generateContent";

    @BeforeEach
    void setUp() {
        geminiProperties = new GeminiProperties();
        geminiProperties.setUrl(API_URL);
        geminiProperties.setModel(MODEL);
        geminiProperties.setKey("dummy-test-key");

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        objectMapper = new ObjectMapper();

        geminiAnalysisService = new GeminiAnalysisService(restClient, geminiProperties, objectMapper);
    }

    private String createGeminiResponseJson(int sentenceId, String sentenceText, boolean outdated, String reason) {
        String innerJson = String.format(
                "{\"sentenceId\":%d,\"sentenceText\":\"%s\",\"potentiallyOutdated\":%b,\"reason\":\"%s\"}",
                sentenceId, sentenceText, outdated, reason
        );
        String escapedInnerJson = innerJson.replace("\"", "\\\"");

        return """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "%s"
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """.formatted(escapedInnerJson);
    }

    @Test
    @DisplayName("1. Valid Gemini analysis response - general parsing and structure")
    void testValidGeminiAnalysisResponse() {
        String mockResponse = createGeminiResponseJson(
                1,
                "Python 3.8 is the latest stable release.",
                true,
                "Python 3.8 was released in 2019 and multiple newer versions exist."
        );

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "dummy-test-key"))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(
                new SentenceAnalysisRequest(1, "Python 3.8 is the latest stable release.")
        );

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getSentenceId()).isEqualTo(1);
        assertThat(result.getSentenceText()).isEqualTo("Python 3.8 is the latest stable release.");
        assertThat(result.isPotentiallyOutdated()).isTrue();
        assertThat(result.getReason()).contains("newer versions exist");
    }

    @Test
    @DisplayName("2. Potentially outdated = true detection")
    void testPotentiallyOutdatedTrue() {
        String mockResponse = createGeminiResponseJson(
                2,
                "Current US population is 330 million according to the 2020 census.",
                true,
                "Population numbers change continually and the 2020 census figures are now historical."
        );

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(
                new SentenceAnalysisRequest(2, "Current US population is 330 million according to the 2020 census.")
        );

        mockServer.verify();
        assertThat(result.isPotentiallyOutdated()).isTrue();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getReason()).contains("historical");
    }

    @Test
    @DisplayName("3. Potentially outdated = false detection for timeless fact")
    void testPotentiallyOutdatedFalse() {
        String mockResponse = createGeminiResponseJson(
                3,
                "QuickSort has an average time complexity of O(n log n).",
                false,
                "This is an inherent mathematical property of the algorithm and does not change."
        );

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(
                new SentenceAnalysisRequest(3, "QuickSort has an average time complexity of O(n log n).")
        );

        mockServer.verify();
        assertThat(result.isPotentiallyOutdated()).isFalse();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getReason()).contains("mathematical property");
    }

    @Test
    @DisplayName("4. Malformed Gemini JSON response handling")
    void testMalformedGeminiJson() {
        String malformedResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{ not valid json at all ... "
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(malformedResponse, MediaType.APPLICATION_JSON));

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(
                new SentenceAnalysisRequest(4, "Test sentence.")
        );

        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo("ANALYSIS_FAILED");
        assertThat(result.isPotentiallyOutdated()).isFalse();
        assertThat(result.getReason()).contains("Failed to parse structured model response");
    }

    @Test
    @DisplayName("5. Missing required response fields (missing 'potentiallyOutdated' or 'reason')")
    void testMissingRequiredResponseFields() {
        String incompleteResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"sentenceId\\": 5, \\"sentenceText\\": \\"Test\\"} "
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(incompleteResponse, MediaType.APPLICATION_JSON));

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(
                new SentenceAnalysisRequest(5, "Test sentence.")
        );

        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo("ANALYSIS_FAILED");
        assertThat(result.isPotentiallyOutdated()).isFalse();
        assertThat(result.getReason()).contains("Missing or invalid 'potentiallyOutdated' boolean field");
    }

    @Test
    @DisplayName("6. Gemini HTTP server error (HTTP 500)")
    void testGeminiHttpError() {
        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(
                new SentenceAnalysisRequest(6, "Test sentence.")
        );

        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo("AI_UNAVAILABLE");
        assertThat(result.isPotentiallyOutdated()).isFalse();
        assertThat(result.getReason()).contains("HTTP 500");
    }

    @Test
    @DisplayName("7. HTTP 429 rate limit handling")
    void testHttp429RateLimit() {
        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(
                new SentenceAnalysisRequest(7, "Test sentence.")
        );

        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo("RATE_LIMITED");
        assertThat(result.isPotentiallyOutdated()).isFalse();
        assertThat(result.getReason()).contains("rate limit exceeded");
    }

    @Test
    @DisplayName("8. Timeout and connection failure handling")
    void testTimeoutHandling() {
        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out");
                });

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(
                new SentenceAnalysisRequest(8, "Test sentence.")
        );

        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo("AI_UNAVAILABLE");
        assertThat(result.isPotentiallyOutdated()).isFalse();
        assertThat(result.getReason()).contains("timed out");
    }

    @Test
    @DisplayName("9. Missing GEMINI_API_KEY handling")
    void testMissingApiKey() {
        geminiProperties.setKey("");

        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(
                new SentenceAnalysisRequest(9, "Test sentence.")
        );

        // No HTTP request should have been dispatched
        assertThat(result.getStatus()).isEqualTo("AI_UNAVAILABLE");
        assertThat(result.isPotentiallyOutdated()).isFalse();
        assertThat(result.getReason()).contains("Gemini API key is not configured");
    }

    @Test
    @DisplayName("10. Application remains operable and returns safe fallback when Gemini is unavailable")
    void testApplicationRemainsOperableWhenGeminiUnavailable() {
        geminiProperties.setKey(null);

        DocumentSentence sentence = new DocumentSentence(10, "Binary search is efficient.");
        SentenceAnalysisResult result = geminiAnalysisService.analyzeSentence(sentence);

        assertThat(result).isNotNull();
        assertThat(result.getSentenceId()).isEqualTo(10);
        assertThat(result.getSentenceText()).isEqualTo("Binary search is efficient.");
        assertThat(result.isPotentiallyOutdated()).isFalse();
        assertThat(result.getStatus()).isEqualTo("AI_UNAVAILABLE");

        // Batch test also works seamlessly
        List<SentenceAnalysisResult> batchResults = geminiAnalysisService.analyzeSentences(List.of(sentence));
        assertThat(batchResults).hasSize(1);
        assertThat(batchResults.get(0).getStatus()).isEqualTo("AI_UNAVAILABLE");
    }

    @Test
    @DisplayName("11. GeminiProperties.toString() masks key and does not leak it")
    void testGeminiPropertiesToStringMasking() {
        GeminiProperties props = new GeminiProperties();
        props.setKey("AIzaSySECRET_TEST_KEY_12345");

        String str = props.toString();
        assertThat(str).doesNotContain("AIzaSySECRET_TEST_KEY_12345");
        assertThat(str).contains("isKeyConfigured=true");
    }

    @Test
    @DisplayName("12. GeminiProperties loads valid key from .env file and strips quotes")
    void testGeminiPropertiesDotEnvLoading() throws Exception {
        java.io.File tempEnv = new java.io.File(".env");
        try {
            java.nio.file.Files.writeString(tempEnv.toPath(), "# Comment line\nGEMINI_API_KEY=\"AIzaSyCustomKeyFromDotEnv\"\n");

            GeminiProperties props = new GeminiProperties();
            props.init();

            assertThat(props.isKeyConfigured()).isTrue();
            assertThat(props.getKey()).isEqualTo("AIzaSyCustomKeyFromDotEnv");
        } finally {
            if (tempEnv.exists()) {
                tempEnv.delete();
            }
        }
    }

    @Test
    @DisplayName("13. GeminiProperties ignores placeholder in .env file")
    void testGeminiPropertiesIgnoresPlaceholder() throws Exception {
        java.io.File tempEnv = new java.io.File(".env");
        try {
            java.nio.file.Files.writeString(tempEnv.toPath(), "GEMINI_API_KEY=your_gemini_api_key_here\n");

            GeminiProperties props = new GeminiProperties();
            props.init();

            assertThat(props.isKeyConfigured()).isFalse();
        } finally {
            if (tempEnv.exists()) {
                tempEnv.delete();
            }
        }
    }
}
