package com.example.edusync.service;

import com.example.edusync.config.SourceVerificationProperties;
import com.example.edusync.model.SourceType;
import com.example.edusync.model.SourceVerificationRequest;
import com.example.edusync.model.SourceVerificationResult;
import com.example.edusync.model.VerificationStatus;
import com.example.edusync.provider.AuthoritativeRegistrySourceProvider;
import com.example.edusync.security.UrlSafetyValidator;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SourceVerificationServiceTest {

    private SourceVerificationService service;
    private MockRestServiceServer mockServer;
    private SourceVerificationProperties properties;

    private static final String BASE_URL = "https://endoflife.date/api";
    private static final String JAVA_URI = BASE_URL + "/java.json";

    private static final String JAVA_MOCK_JSON = """
            [
              {
                "cycle": "21",
                "releaseDate": "2023-09-19",
                "eol": "2028-09-30",
                "latest": "21.0.6",
                "latestReleaseDate": "2025-01-21",
                "lts": true,
                "support": "2026-09-30",
                "link": "https://www.oracle.com/java/technologies/java-se-support-roadmap.html"
              },
              {
                "cycle": "17",
                "releaseDate": "2021-09-14",
                "eol": "2026-09-30",
                "latest": "17.0.14",
                "latestReleaseDate": "2025-01-21",
                "lts": true,
                "support": "2024-09-30",
                "link": "https://www.oracle.com/java/technologies/java-se-support-roadmap.html"
              }
            ]
            """;

    @BeforeEach
    void setUp() {
        properties = new SourceVerificationProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setAllowedDomains(List.of("endoflife.date"));

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        UrlSafetyValidator urlSafetyValidator = new UrlSafetyValidator(properties);
        ObjectMapper objectMapper = new ObjectMapper();

        AuthoritativeRegistrySourceProvider provider = new AuthoritativeRegistrySourceProvider(
                restClient, properties, urlSafetyValidator, objectMapper
        );

        service = new SourceVerificationService(List.of(provider));
    }

    @Test
    @DisplayName("1. Valid source retrieval succeeds")
    void testValidSourceRetrieval() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(JAVA_MOCK_JSON, MediaType.APPLICATION_JSON));

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(1, "Java 17 is the latest LTS version of Java.", "Refers to a current version.")
        );

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getSentenceId()).isEqualTo(1);
        assertThat(result.getSourceUrl()).isEqualTo(JAVA_URI);
    }

    @Test
    @DisplayName("2. Authoritative source metadata preserved and classified as REPUTABLE with separate vendor link")
    void testAuthoritativeSourceMetadata() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(JAVA_MOCK_JSON, MediaType.APPLICATION_JSON));

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(2, "Java 17 is current.", "Temporal version.")
        );

        mockServer.verify();
        assertThat(result.getSourceType()).isEqualTo(SourceType.REPUTABLE);
        assertThat(result.getSourceTitle()).contains("Java Lifecycle");
        assertThat(result.getSourceUrl()).isEqualTo(JAVA_URI);
        assertThat(result.getOfficialReferenceUrl()).isEqualTo("https://www.oracle.com/java/technologies/java-se-support-roadmap.html");
        assertThat(result.getRetrievedAt()).isNotNull();
    }

    @Test
    @DisplayName("3. Successful evidence extraction and claim verification")
    void testSuccessfulEvidenceExtraction() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(JAVA_MOCK_JSON, MediaType.APPLICATION_JSON));

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(3, "Java 17 is the latest LTS version of Java.", "Refers to a current version.")
        );

        mockServer.verify();
        assertThat(result.isVerified()).isTrue();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(result.getCurrentInformation()).contains("Latest version is 21");
    }

    @Test
    @DisplayName("4. Source unavailable on network failure")
    void testSourceUnavailableOnNetworkFailure() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection refused");
                });

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(4, "Java 17 is the latest version.", "Temporal.")
        );

        mockServer.verify();
        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.SOURCE_UNAVAILABLE);
    }

    @Test
    @DisplayName("5. HTTP 404 Not Found handling")
    void testHttp404NotFound() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(5, "Java 17 is current.", "Temporal.")
        );

        mockServer.verify();
        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.SOURCE_UNAVAILABLE);
        assertThat(result.getCurrentInformation()).contains("404");
    }

    @Test
    @DisplayName("6. HTTP 429 Rate Limit handling")
    void testHttp429RateLimit() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(6, "Java 17 is current.", "Temporal.")
        );

        mockServer.verify();
        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.SOURCE_UNAVAILABLE);
        assertThat(result.getCurrentInformation()).contains("429");
    }

    @Test
    @DisplayName("7. HTTP 500 Server Error handling")
    void testHttp500ServerError() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(7, "Java 17 is current.", "Temporal.")
        );

        mockServer.verify();
        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.SOURCE_UNAVAILABLE);
        assertThat(result.getCurrentInformation()).contains("500");
    }

    @Test
    @DisplayName("8. Connection / Read Timeout handling")
    void testTimeoutHandling() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out");
                });

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(8, "Java 17 is current.", "Temporal.")
        );

        mockServer.verify();
        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.SOURCE_UNAVAILABLE);
        assertThat(result.getCurrentInformation()).contains("timed out");
    }

    @Test
    @DisplayName("9. Malformed source response handling")
    void testMalformedSourceResponse() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{ not an array: true }", MediaType.APPLICATION_JSON));

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(9, "Java 17 is current.", "Temporal.")
        );

        mockServer.verify();
        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFICATION_FAILED);
        assertThat(result.getCurrentInformation()).contains("parse structured lifecycle response");
    }

    @Test
    @DisplayName("10. Unsupported / unapproved source (SSRF defense blocks unauthorized domain)")
    void testUnsupportedUnapprovedSource() {
        properties.setBaseUrl("https://evil-unapproved-site.com/api");

        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(10, "Java 17 is current.", "Temporal.")
        );

        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFICATION_FAILED);
        assertThat(result.getCurrentInformation()).contains("whitelist");
    }

    @Test
    @DisplayName("11. Verification result when evidence cannot support the claim returns NOT_VERIFIED")
    void testInsufficientEvidenceYieldsNotVerified() {
        mockServer.expect(requestTo(JAVA_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(JAVA_MOCK_JSON, MediaType.APPLICATION_JSON));

        // Sentence has Java, but makes an architectural claim unrelated to release cycles
        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(11, "Java bytecode executes inside the JVM virtual machine.", "Claim about architecture.")
        );

        mockServer.verify();
        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.NOT_VERIFIED);
        assertThat(result.getCurrentInformation()).contains("does not contain sufficient evidence");
    }

    @Test
    @DisplayName("12. Request validation: empty sentence returns VERIFICATION_FAILED")
    void testRequestValidationEmptySentence() {
        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(12, "", "Some reason")
        );

        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFICATION_FAILED);
        assertThat(result.getCurrentInformation()).contains("cannot be empty");
    }

    @Test
    @DisplayName("13. Unknown/unsupported product returns NOT_VERIFIED without guessing")
    void testUnknownProductReturnsNotVerified() {
        // Sentence mentions an unknown fictional product
        SourceVerificationResult result = service.verifySource(
                new SourceVerificationRequest(13, "XylophoneFramework 4.0 was released in 2021.", "Obsolete framework.")
        );

        assertThat(result.isVerified()).isFalse();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.NOT_VERIFIED);
        assertThat(result.getCurrentInformation()).contains("could confidently identify");
    }
}
