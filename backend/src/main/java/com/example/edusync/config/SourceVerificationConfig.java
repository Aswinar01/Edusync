package com.example.edusync.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class SourceVerificationConfig {

    private final SourceVerificationProperties properties;

    public SourceVerificationConfig(SourceVerificationProperties properties) {
        this.properties = properties;
    }

    @Bean
    @Qualifier("verificationRestClientBuilder")
    public RestClient.Builder verificationRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    @Bean
    @Qualifier("verificationRestClient")
    public RestClient verificationRestClient(@Qualifier("verificationRestClientBuilder") RestClient.Builder verificationRestClientBuilder) {
        return verificationRestClientBuilder.build();
    }
}
