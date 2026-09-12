package com.example.edusync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class GeminiConfig {

    private final GeminiProperties geminiProperties;

    public GeminiConfig(GeminiProperties geminiProperties) {
        this.geminiProperties = geminiProperties;
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public RestClient.Builder geminiRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(geminiProperties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(geminiProperties.getReadTimeoutMs()));

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public RestClient geminiRestClient(RestClient.Builder geminiRestClientBuilder) {
        return geminiRestClientBuilder.build();
    }
}
