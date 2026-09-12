package com.example.edusync.service;

import com.example.edusync.config.GeminiProperties;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.ProposalStatus;
import com.example.edusync.model.ProposedUpdate;
import com.example.edusync.model.SentenceAnalysisResult;
import com.example.edusync.model.SourceType;
import com.example.edusync.model.SourceVerificationResult;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProposedUpdateServiceTest {

    private ProposedUpdateService service;
    private MockRestServiceServer mockServer;
    private GeminiProperties geminiProperties;

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String EXPECTED_URI = API_URL + "/models/" + MODEL + ":generateContent";

    private DocumentSentence sentence;
    private SentenceAnalysisResult analysis;
    private SourceVerificationResult verification;

    @BeforeEach
    void setUp() {
        geminiProperties = new GeminiProperties();
        geminiProperties.setUrl(API_URL);
        geminiProperties.setModel(MODEL);
        geminiProperties.setKey("dummy-gemini-key");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ObjectMapper objectMapper = new ObjectMapper();
        service = new ProposedUpdateService(restClient, geminiProperties, objectMapper);

        sentence = new DocumentSentence(1, "Java 17 is the latest LTS version of Java.");
        analysis = SentenceAnalysisResult.success(1, "Java 17 is the latest LTS version of Java.", true, "Newer LTS releases exist.");
        verification = SourceVerificationResult.verified(
                1,
                "Java 17 is the latest LTS version of Java.",
                "Latest release cycle: 21 (version 21.0.6, released 2023-09-19). Active LTS: 21, 17.",
                "Java Lifecycle & Releases",
                "https://endoflife.date/api/java.json",
                "https://www.oracle.com/java/technologies/java-se-support-roadmap.html",
                SourceType.REPUTABLE
        );
    }

    private String buildMockGeminiResponse(String innerJson) {
        String escaped = innerJson.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
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
                """.formatted(escaped);
    }

    @Test
    @DisplayName("1. Successful grounded update proposal generated")
    void testSuccessfulGroundedProposal() {
        String mockOutput = buildMockGeminiResponse("""
                {
                  "proposedSentence": "Java 21 is the latest LTS version of Java.",
                  "reason": "Updated LTS version from 17 to 21 based on verified release data.",
                  "insufficientEvidence": false,
                  "noUpdateNeeded": false
                }
                """);

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "dummy-gemini-key"))
                .andRespond(withSuccess(mockOutput, MediaType.APPLICATION_JSON));

        ProposedUpdate update = service.generateProposal(sentence, analysis, verification);

        mockServer.verify();
        assertThat(update).isNotNull();
        assertThat(update.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(update.getSentenceId()).isEqualTo(1);
        assertThat(update.getOriginalSentence()).isEqualTo("Java 17 is the latest LTS version of Java.");
        assertThat(update.getProposedSentence()).isEqualTo("Java 21 is the latest LTS version of Java.");
        assertThat(update.getSourceUrl()).isEqualTo("https://endoflife.date/api/java.json");
        assertThat(update.getOfficialReferenceUrl()).isEqualTo("https://www.oracle.com/java/technologies/java-se-support-roadmap.html");
    }

    @Test
    @DisplayName("2. Original sentence is preserved untouched")
    void testPreservesOriginalSentence() {
        String mockOutput = buildMockGeminiResponse("""
                {
                  "proposedSentence": "Java 21 is the latest LTS version of Java.",
                  "reason": "Updated to 21."
                }
                """);

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockOutput, MediaType.APPLICATION_JSON));

        ProposedUpdate update = service.generateProposal(sentence, analysis, verification);

        mockServer.verify();
        assertThat(update.getOriginalSentence()).isEqualTo(sentence.getSentenceText());
        assertThat(update.getOriginalSentence()).isNotEqualTo(update.getProposedSentence());
    }

    @Test
    @DisplayName("3. Defensive grounding validation catches hallucinated version numbers")
    void testDefensiveGroundingRejectsHallucinatedTokens() {
        // Model attempts to hallucinate Java 27, which does not exist in verified evidence (evidence only has 21, 17)
        String mockOutput = buildMockGeminiResponse("""
                {
                  "proposedSentence": "Java 27 is the latest LTS version of Java.",
                  "reason": "Hallucinated version."
                }
                """);

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockOutput, MediaType.APPLICATION_JSON));

        ProposedUpdate update = service.generateProposal(sentence, analysis, verification);

        mockServer.verify();
        assertThat(update.getStatus()).isEqualTo(ProposalStatus.INSUFFICIENT_EVIDENCE);
        assertThat(update.getReason()).contains("defensive grounding validation");
        assertThat(update.getProposedSentence()).isEmpty();
    }

    @Test
    @DisplayName("4. Model flags insufficient evidence -> returns INSUFFICIENT_EVIDENCE")
    void testInsufficientEvidenceFromModel() {
        String mockOutput = buildMockGeminiResponse("""
                {
                  "proposedSentence": "",
                  "reason": "The source does not provide enough information.",
                  "insufficientEvidence": true
                }
                """);

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockOutput, MediaType.APPLICATION_JSON));

        ProposedUpdate update = service.generateProposal(sentence, analysis, verification);

        mockServer.verify();
        assertThat(update.getStatus()).isEqualTo(ProposalStatus.INSUFFICIENT_EVIDENCE);
    }

    @Test
    @DisplayName("5. Model flags no update needed -> returns NO_UPDATE")
    void testNoUpdateNeededFromModel() {
        String mockOutput = buildMockGeminiResponse("""
                {
                  "proposedSentence": "Java 17 is the latest LTS version of Java.",
                  "reason": "Original statement remains accurate.",
                  "noUpdateNeeded": true
                }
                """);

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockOutput, MediaType.APPLICATION_JSON));

        ProposedUpdate update = service.generateProposal(sentence, analysis, verification);

        mockServer.verify();
        assertThat(update.getStatus()).isEqualTo(ProposalStatus.NO_UPDATE);
        assertThat(update.getProposedSentence()).isEqualTo(sentence.getSentenceText());
    }

    @Test
    @DisplayName("6. Missing API key returns GENERATION_FAILED without network call")
    void testMissingApiKeyReturnsFailed() {
        geminiProperties.setKey("");

        ProposedUpdate update = service.generateProposal(sentence, analysis, verification);

        assertThat(update.getStatus()).isEqualTo(ProposalStatus.GENERATION_FAILED);
        assertThat(update.getReason()).contains("API key is not configured");
    }

    @Test
    @DisplayName("7. Malformed JSON response returns GENERATION_FAILED")
    void testMalformedJsonResponseReturnsFailed() {
        String malformed = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "Not a valid JSON response"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(malformed, MediaType.APPLICATION_JSON));

        ProposedUpdate update = service.generateProposal(sentence, analysis, verification);

        mockServer.verify();
        assertThat(update.getStatus()).isEqualTo(ProposalStatus.GENERATION_FAILED);
    }

    @Test
    @DisplayName("8. Upstream HTTP 500 error returns GENERATION_FAILED")
    void testUpstreamHttp500ReturnsFailed() {
        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        ProposedUpdate update = service.generateProposal(sentence, analysis, verification);

        mockServer.verify();
        assertThat(update.getStatus()).isEqualTo(ProposalStatus.GENERATION_FAILED);
        assertThat(update.getReason()).contains("HTTP 500");
    }

    @Test
    @DisplayName("9. Upstream timeout returns GENERATION_FAILED")
    void testUpstreamTimeoutReturnsFailed() {
        mockServer.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out");
                });

        ProposedUpdate update = service.generateProposal(sentence, analysis, verification);

        mockServer.verify();
        assertThat(update.getStatus()).isEqualTo(ProposalStatus.GENERATION_FAILED);
        assertThat(update.getReason()).contains("timed out");
    }
}
