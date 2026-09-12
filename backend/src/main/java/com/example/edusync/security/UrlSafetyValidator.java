package com.example.edusync.security;

import com.example.edusync.config.SourceVerificationProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

@Component
public class UrlSafetyValidator {

    private final SourceVerificationProperties properties;

    public UrlSafetyValidator(SourceVerificationProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates that a target URL is safe to fetch:
     * 1. Must have valid URI syntax.
     * 2. Must use HTTPS scheme.
     * 3. Must match an explicitly allowed domain in configuration.
     * 4. Must not resolve to localhost, private network, or link-local/cloud metadata IP ranges (SSRF defense).
     */
    public void validateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Target URL cannot be null or blank.");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed URL: " + ex.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new SecurityException("Insecure URL scheme: only HTTPS requests are permitted.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("URL must contain a valid host name.");
        }

        host = host.toLowerCase();

        // 1. Check against allowed domains
        List<String> allowedDomains = properties.getAllowedDomains();
        boolean domainAllowed = false;
        for (String allowed : allowedDomains) {
            String cleanAllowed = allowed.toLowerCase().trim();
            if (host.equals(cleanAllowed) || host.endsWith("." + cleanAllowed)) {
                domainAllowed = true;
                break;
            }
        }

        if (!domainAllowed) {
            throw new SecurityException("Domain '" + host + "' is not in the approved source whitelist.");
        }

        // 2. Resolve host and check against private / loopback / link-local addresses
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateOrLocalAddress(address)) {
                    throw new SecurityException("SSRF blocked: Host '" + host + "' resolves to private/loopback address: " + address.getHostAddress());
                }
            }
        } catch (UnknownHostException ex) {
            // If DNS resolution fails, allow if mock server or let HTTP client throw connection failure
            // But if host is literal "localhost" or an IP literal, reject immediately
            if (host.equals("localhost") || host.startsWith("127.") || host.equals("::1")) {
                throw new SecurityException("SSRF blocked: Host '" + host + "' is a loopback address.");
            }
        }
    }

    public boolean isUrlSafe(String rawUrl) {
        try {
            validateUrl(rawUrl);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isPrivateOrLocalAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }

        if (address.isSiteLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();

        // IPv4 checks
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true;

            // 10.0.0.0/8 (Private)
            if (b0 == 10) return true;

            // 172.16.0.0/12 (Private)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;

            // 192.168.0.0/16 (Private)
            if (b0 == 192 && b1 == 168) return true;

            // 169.254.0.0/16 (Link Local / AWS Metadata 169.254.169.254)
            if (b0 == 169 && b1 == 254) return true;

            // 0.0.0.0/8
            if (b0 == 0) return true;
        }

        return false;
    }
}
