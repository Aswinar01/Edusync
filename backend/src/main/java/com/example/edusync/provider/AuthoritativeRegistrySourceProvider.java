package com.example.edusync.provider;

import com.example.edusync.config.SourceVerificationProperties;
import com.example.edusync.model.SourceEvidence;
import com.example.edusync.model.SourceType;
import com.example.edusync.model.SourceVerificationRequest;
import com.example.edusync.security.UrlSafetyValidator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Source provider that queries the authoritative product lifecycle registry (endoflife.date API).
 * Source trust classification is REPUTABLE (lifecycle aggregator). Official vendor URLs are preserved
 * separately in officialReferenceUrl.
 */
@Component
public class AuthoritativeRegistrySourceProvider implements SourceProvider {

    private static final Logger log = LoggerFactory.getLogger(AuthoritativeRegistrySourceProvider.class);

    private final RestClient restClient;
    private final SourceVerificationProperties properties;
    private final UrlSafetyValidator urlSafetyValidator;
    private final ObjectMapper objectMapper;

    // Controlled, explicit mapping for supported technologies
    private static final Map<String, Pattern> SUPPORTED_PRODUCTS = new LinkedHashMap<>();

    static {
        SUPPORTED_PRODUCTS.put("spring-boot", Pattern.compile("(?i)\\b(spring\\s*boot|springboot)\\b"));
        SUPPORTED_PRODUCTS.put("nodejs", Pattern.compile("(?i)\\b(node\\.js|nodejs|node)\\b"));
        SUPPORTED_PRODUCTS.put("kubernetes", Pattern.compile("(?i)\\b(kubernetes|k8s)\\b"));
        SUPPORTED_PRODUCTS.put("java", Pattern.compile("(?i)\\b(java|openjdk)\\b"));
        SUPPORTED_PRODUCTS.put("python", Pattern.compile("(?i)\\bpython\\b"));
    }

    public AuthoritativeRegistrySourceProvider(@Qualifier("verificationRestClient") RestClient restClient,
                                                SourceVerificationProperties properties,
                                                UrlSafetyValidator urlSafetyValidator,
                                                ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.urlSafetyValidator = urlSafetyValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "AuthoritativeRegistrySourceProvider (endoflife.date)";
    }

    @Override
    public boolean supports(SourceVerificationRequest request) {
        if (request == null) {
            return false;
        }
        return identifyProduct(request.getOriginalSentence(), request.getReason()) != null;
    }

    /**
     * Identifies a supported product using explicit pattern rules.
     * Returns null if no supported product is confidently identified.
     */
    public String identifyProduct(String sentence, String reason) {
        String combined = (sentence != null ? sentence : "") + " " + (reason != null ? reason : "");
        if (combined.isBlank()) {
            return null;
        }

        for (Map.Entry<String, Pattern> entry : SUPPORTED_PRODUCTS.entrySet()) {
            if (entry.getValue().matcher(combined).find()) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public SourceEvidence fetchEvidence(SourceVerificationRequest request) {
        if (request == null) {
            return SourceEvidence.failure("", "Request cannot be null.", 400);
        }

        String productId = identifyProduct(request.getOriginalSentence(), request.getReason());
        if (productId == null) {
            return SourceEvidence.failure("", "No supported product could be confidently identified.", 400);
        }

        String targetUrl = String.format("%s/%s.json", properties.getBaseUrl(), productId);

        // SSRF & URL safety verification
        try {
            urlSafetyValidator.validateUrl(targetUrl);
        } catch (SecurityException ex) {
            log.warn("Blocked unsafe or unapproved URL: {} ({})", targetUrl, ex.getMessage());
            return SourceEvidence.failure(targetUrl, "Security validation blocked URL: " + ex.getMessage(), 403);
        } catch (Exception ex) {
            log.warn("Invalid target URL: {} ({})", targetUrl, ex.getMessage());
            return SourceEvidence.failure(targetUrl, "Invalid URL: " + ex.getMessage(), 400);
        }

        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(targetUrl)
                    .retrieve()
                    .toEntity(String.class);

            return parseLifecycleResponse(response.getBody(), productId, targetUrl);

        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("Lifecycle data for product '{}' not found (HTTP 404)", productId);
                return SourceEvidence.failure(targetUrl, "Product lifecycle data not found (HTTP 404)", 404);
            }
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Rate limit exceeded while querying {} (HTTP 429)", targetUrl);
                return SourceEvidence.failure(targetUrl, "Rate limit exceeded (HTTP 429)", 429);
            }
            log.error("External source HTTP error {}: {}", statusCode, ex.getMessage());
            return SourceEvidence.failure(targetUrl, "External source returned HTTP " + statusCode, statusCode);

        } catch (ResourceAccessException ex) {
            log.error("Timeout or connection failure connecting to {}: {}", targetUrl, ex.getMessage());
            return SourceEvidence.failure(targetUrl, "Request timed out or connection failed.", 504);

        } catch (Exception ex) {
            log.error("Unexpected error retrieving evidence from {}: {}", targetUrl, ex.getMessage());
            return SourceEvidence.failure(targetUrl, "Unexpected retrieval failure.", 500);
        }
    }

    private SourceEvidence parseLifecycleResponse(String responseBody, String productId, String targetUrl) {
        if (responseBody == null || responseBody.isBlank()) {
            return SourceEvidence.failure(targetUrl, "Empty response received from source.", 502);
        }

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            if (!rootNode.isArray() || rootNode.isEmpty()) {
                return SourceEvidence.failure(targetUrl, "Malformed lifecycle response: expected array of cycles.", 502);
            }

            JsonNode newestCycle = rootNode.get(0);
            String latestCycle = newestCycle.path("cycle").asText("");
            String latestVersion = newestCycle.path("latest").asText(latestCycle);
            String releaseDate = newestCycle.path("releaseDate").asText("");
            String officialLink = newestCycle.path("link").asText(null);

            // Collect LTS cycles
            List<String> ltsCycles = new ArrayList<>();
            for (JsonNode cycleNode : rootNode) {
                JsonNode ltsNode = cycleNode.path("lts");
                boolean isLts = (ltsNode.isBoolean() && ltsNode.asBoolean()) ||
                        (ltsNode.isTextual() && !ltsNode.asText().equalsIgnoreCase("false"));
                if (isLts) {
                    ltsCycles.add(cycleNode.path("cycle").asText(""));
                }
                if (officialLink == null && cycleNode.hasNonNull("link")) {
                    officialLink = cycleNode.path("link").asText();
                }
            }

            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("Product: ").append(capitalize(productId))
                    .append(". Latest release cycle: ").append(latestCycle)
                    .append(" (version ").append(latestVersion)
                    .append(", released ").append(releaseDate).append(").");

            if (!ltsCycles.isEmpty()) {
                contentBuilder.append(" LTS release cycles: ").append(String.join(", ", ltsCycles)).append(".");
            }

            String title = capitalize(productId) + " Lifecycle & Releases";

            return SourceEvidence.success(
                    title,
                    targetUrl,
                    officialLink,
                    contentBuilder.toString(),
                    SourceType.REPUTABLE // endoflife.date is a reputable aggregator, official vendor URL is stored in officialReferenceUrl
            );

        } catch (Exception ex) {
            log.error("Failed to parse lifecycle JSON: {}", ex.getMessage());
            return SourceEvidence.failure(targetUrl, "Failed to parse structured lifecycle response.", 502);
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.equalsIgnoreCase("nodejs")) return "Node.js";
        if (text.equalsIgnoreCase("spring-boot")) return "Spring Boot";
        if (text.equalsIgnoreCase("k8s") || text.equalsIgnoreCase("kubernetes")) return "Kubernetes";
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
