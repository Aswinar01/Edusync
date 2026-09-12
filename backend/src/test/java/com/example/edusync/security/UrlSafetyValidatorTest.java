package com.example.edusync.security;

import com.example.edusync.config.SourceVerificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyValidatorTest {

    private UrlSafetyValidator validator;
    private SourceVerificationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SourceVerificationProperties();
        properties.setAllowedDomains(List.of("endoflife.date", "api.example.org"));
        validator = new UrlSafetyValidator(properties);
    }

    @Test
    @DisplayName("Approved HTTPS domain passes validation")
    void testApprovedHttpsDomainPasses() {
        assertThatCode(() -> validator.validateUrl("https://endoflife.date/api/java.json"))
                .doesNotThrowAnyException();
        assertThat(validator.isUrlSafe("https://endoflife.date/api/java.json")).isTrue();
    }

    @Test
    @DisplayName("Subdomain of approved domain passes validation")
    void testSubdomainPasses() {
        assertThatCode(() -> validator.validateUrl("https://sub.endoflife.date/test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Insecure HTTP scheme is blocked")
    void testHttpSchemeBlocked() {
        assertThatThrownBy(() -> validator.validateUrl("http://endoflife.date/api/java.json"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("only HTTPS requests are permitted");
    }

    @Test
    @DisplayName("Unapproved domain is blocked")
    void testUnapprovedDomainBlocked() {
        assertThatThrownBy(() -> validator.validateUrl("https://malicious-site.com/api"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not in the approved source whitelist");
    }

    @Test
    @DisplayName("Localhost and 127.0.0.1 are blocked (SSRF defense)")
    void testLocalhostBlocked() {
        properties.setAllowedDomains(List.of("localhost", "127.0.0.1"));
        assertThatThrownBy(() -> validator.validateUrl("https://localhost:8080/secret"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SSRF blocked");

        assertThatThrownBy(() -> validator.validateUrl("https://127.0.0.1:8080/secret"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SSRF blocked");
    }

    @Test
    @DisplayName("Cloud metadata IP 169.254.169.254 is blocked")
    void testCloudMetadataIpBlocked() {
        properties.setAllowedDomains(List.of("169.254.169.254"));
        assertThatThrownBy(() -> validator.validateUrl("https://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SSRF blocked");
    }

    @Test
    @DisplayName("Null, blank, or invalid URL throws IllegalArgumentException")
    void testNullOrBlankUrl() {
        assertThatThrownBy(() -> validator.validateUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateUrl("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
