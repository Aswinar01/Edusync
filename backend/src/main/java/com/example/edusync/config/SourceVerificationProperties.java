package com.example.edusync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "verification.source")
public class SourceVerificationProperties {

    private String baseUrl = "https://endoflife.date/api";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private List<String> allowedDomains = new ArrayList<>(List.of("endoflife.date"));
    private String userAgent = "EduSync/1.0 (Study Material Verification Engine)";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public List<String> getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(List<String> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    public void setAllowedDomains(String domains) {
        if (domains != null && !domains.isBlank()) {
            this.allowedDomains = Arrays.stream(domains.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public String toString() {
        return "SourceVerificationProperties{" +
                "baseUrl='" + baseUrl + '\'' +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", readTimeoutMs=" + readTimeoutMs +
                ", allowedDomains=" + allowedDomains +
                '}';
    }
}
