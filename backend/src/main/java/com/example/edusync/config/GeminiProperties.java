package com.example.edusync.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

@Component
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiProperties {

    private String key;
    private String model = "gemini-2.5-flash";
    private String url = "https://generativelanguage.googleapis.com/v1beta";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;

    @PostConstruct
    public void init() {
        if (this.key == null || this.key.isBlank()) {
            this.key = findKeyInDotEnvFiles();
        }
    }

    private String findKeyInDotEnvFiles() {
        String[] possiblePaths = {".env", "backend/.env", "../backend/.env"};
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("GEMINI_API_KEY=") && !line.startsWith("#")) {
                            String value = line.substring("GEMINI_API_KEY=".length()).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                                if (value.length() >= 2) {
                                    value = value.substring(1, value.length() - 1).trim();
                                }
                            }
                            if (!value.isEmpty() && !value.equalsIgnoreCase("your_gemini_api_key_here")) {
                                return value;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Safe fallback: do not log or fail startup
                }
            }
        }
        return "";
    }

    public boolean isKeyConfigured() {
        return key != null && !key.isBlank();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    @Override
    public String toString() {
        return "GeminiProperties{" +
                "model='" + model + '\'' +
                ", url='" + url + '\'' +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", readTimeoutMs=" + readTimeoutMs +
                ", isKeyConfigured=" + isKeyConfigured() +
                '}';
    }
}
