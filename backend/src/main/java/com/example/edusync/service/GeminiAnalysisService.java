package com.example.edusync.service;

import com.example.edusync.config.GeminiProperties;
import com.example.edusync.model.DocumentSentence;
import com.example.edusync.model.SentenceAnalysisRequest;
import com.example.edusync.model.SentenceAnalysisResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAnalysisService.class);

    private final RestClient restClient;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;

    public GeminiAnalysisService(RestClient restClient, GeminiProperties geminiProperties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Convenience method to analyze a DocumentSentence directly.
     */
    public SentenceAnalysisResult analyzeSentence(DocumentSentence sentence) {
        if (sentence == null) {
            return SentenceAnalysisResult.failed(0, "", "Input sentence cannot be null.");
        }
        return analyzeSentence(new SentenceAnalysisRequest(sentence.getSentenceId(), sentence.getSentenceText()));
    }

    /**
     * Analyzes a single sentence for potentially outdated or time-sensitive factual information.
     */
    public SentenceAnalysisResult analyzeSentence(SentenceAnalysisRequest request) {
        if (request == null) {
            return SentenceAnalysisResult.failed(0, "", "Analysis request cannot be null.");
        }

        int sentenceId = request.getSentenceId();
        String sentenceText = request.getSentenceText() != null ? request.getSentenceText().trim() : "";

        if (sentenceText.isEmpty()) {
            return SentenceAnalysisResult.failed(sentenceId, "", "Sentence text cannot be empty.");
        }

        // Check for presence of API key
        if (!geminiProperties.isKeyConfigured()) {
            return SentenceAnalysisResult.aiUnavailable(
                    sentenceId,
                    sentenceText,
                    "AI analysis is unavailable because the Gemini API key is not configured."
            );
        }

        try {
            String prompt = buildPrompt(sentenceId, sentenceText);
            Map<String, Object> requestPayload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    ),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "temperature", 0.1
                    )
            );

            String requestUrl = String.format("%s/models/%s:generateContent",
                    geminiProperties.getUrl(), geminiProperties.getModel());

            ResponseEntity<String> response = restClient.post()
                    .uri(requestUrl)
                    .header("x-goog-api-key", geminiProperties.getKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .toEntity(String.class);

            return parseAndValidateGeminiResponse(response.getBody(), sentenceId, sentenceText);

        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Gemini API rate limit exceeded for sentence {}", sentenceId);
                return SentenceAnalysisResult.rateLimited(
                        sentenceId,
                        sentenceText,
                        "AI analysis rate limit exceeded. Please try again later."
                );
            }
            log.error("Gemini API returned HTTP status {}", ex.getStatusCode().value());
            return SentenceAnalysisResult.aiUnavailable(
                    sentenceId,
                    sentenceText,
                    "AI analysis service temporarily unavailable (HTTP " + ex.getStatusCode().value() + ")."
            );
        } catch (ResourceAccessException ex) {
            log.error("Gemini API request timed out or network connection failed");
            return SentenceAnalysisResult.aiUnavailable(
                    sentenceId,
                    sentenceText,
                    "AI analysis request timed out or network connection failed."
            );
        } catch (Exception ex) {
            log.error("Unexpected error during Gemini analysis: {}", ex.getMessage());
            return SentenceAnalysisResult.failed(
                    sentenceId,
                    sentenceText,
                    "AI analysis failed due to an internal error."
            );
        }
    }

    /**
     * Batch analysis convenience method for multiple sentences.
     */
    public List<SentenceAnalysisResult> analyzeSentences(List<DocumentSentence> sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return Collections.emptyList();
        }
        List<SentenceAnalysisResult> results = new ArrayList<>(sentences.size());
        for (DocumentSentence sentence : sentences) {
            results.add(analyzeSentence(sentence));
        }
        return results;
    }

    private String buildPrompt(int sentenceId, String sentenceText) {
        return """
                You are EduSync's factual consistency analyzer for educational and academic study materials.
                Analyze the following educational sentence and determine if it potentially contains outdated, obsolete, or time-sensitive factual information.

                Evaluate whether the sentence relies on claims such as:
                1. Software, library, framework, or language versions and release states (e.g. "Java 17 is the latest version").
                2. Specific calendar dates, time periods, or years treated as the present.
                3. Numerical statistics, metrics, population numbers, or survey data.
                4. Contemporary governmental, institutional, or university policies and regulations.
                5. Organizations, office holders, company leaders, or group structures.
                6. Technology or industry standards that evolve over time.
                7. Relative temporal claims using terms like "latest", "current", "newest", "recently", "modern".
                8. Other factual assertions whose truth or validity changes over time.

                Important guidelines:
                - Timeless, foundational, or definition-based concepts (e.g., mathematical formulas, algorithmic definitions, fundamental scientific laws) should NOT be flagged as potentially outdated.
                - Only flag as potentiallyOutdated = true if the claim is genuinely temporal, version-specific, or susceptible to obsolescence.

                Sentence ID: %d
                Sentence Text: "%s"

                Respond with strict JSON adhering to this schema:
                {
                  "sentenceId": %d,
                  "sentenceText": "%s",
                  "potentiallyOutdated": true/false,
                  "reason": "Detailed explanation of why the sentence is or is not potentially outdated."
                }
                """.formatted(sentenceId, escapeJson(sentenceText), sentenceId, escapeJson(sentenceText));
    }

    private SentenceAnalysisResult parseAndValidateGeminiResponse(String responseBody, int expectedSentenceId, String fallbackSentenceText) {
        if (responseBody == null || responseBody.isBlank()) {
            return SentenceAnalysisResult.failed(expectedSentenceId, fallbackSentenceText, "Empty response received from AI service.");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode candidatesNode = rootNode.path("candidates");

            if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
                return SentenceAnalysisResult.failed(expectedSentenceId, fallbackSentenceText, "No candidate output returned by AI service.");
            }

            JsonNode firstCandidate = candidatesNode.get(0);
            JsonNode textNode = firstCandidate.path("content").path("parts");

            if (!textNode.isArray() || textNode.isEmpty()) {
                return SentenceAnalysisResult.failed(expectedSentenceId, fallbackSentenceText, "No text parts returned in model candidate.");
            }

            String rawContent = textNode.get(0).path("text").asText("");
            if (rawContent.isBlank()) {
                return SentenceAnalysisResult.failed(expectedSentenceId, fallbackSentenceText, "Model returned empty text output.");
            }

            String jsonText = extractJsonText(rawContent);
            JsonNode modelOutputNode = objectMapper.readTree(jsonText);

            // Validate required fields
            if (!modelOutputNode.has("potentiallyOutdated") || !modelOutputNode.get("potentiallyOutdated").isBoolean()) {
                return SentenceAnalysisResult.failed(expectedSentenceId, fallbackSentenceText, "Missing or invalid 'potentiallyOutdated' boolean field.");
            }

            if (!modelOutputNode.has("reason") || modelOutputNode.get("reason").asText().isBlank()) {
                return SentenceAnalysisResult.failed(expectedSentenceId, fallbackSentenceText, "Missing or blank 'reason' field.");
            }

            boolean potentiallyOutdated = modelOutputNode.get("potentiallyOutdated").asBoolean();
            String reason = modelOutputNode.get("reason").asText().trim();

            int sentenceId = modelOutputNode.has("sentenceId") && modelOutputNode.get("sentenceId").isInt()
                    ? modelOutputNode.get("sentenceId").asInt()
                    : expectedSentenceId;

            String sentenceText = modelOutputNode.has("sentenceText") && !modelOutputNode.get("sentenceText").asText().isBlank()
                    ? modelOutputNode.get("sentenceText").asText().trim()
                    : fallbackSentenceText;

            return SentenceAnalysisResult.success(sentenceId, sentenceText, potentiallyOutdated, reason);

        } catch (Exception ex) {
            log.error("Failed to parse Gemini output: {}", ex.getMessage());
            return SentenceAnalysisResult.failed(expectedSentenceId, fallbackSentenceText, "Failed to parse structured model response.");
        }
    }

    private String extractJsonText(String rawText) {
        String trimmed = rawText.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring("```json".length()).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring("```".length()).trim();
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - "```".length()).trim();
        }

        return trimmed;
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
