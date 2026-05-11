package com.optrip.server.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

// GeminiProperties 를 빈으로 등록 + Gemini 전용 RestClient 생성
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiClientConfig {

    // 다른 외부 API 와 헤더/baseUrl 이 섞이지 않도록 Gemini 전용 RestClient 를 별도 생성
    @Bean
    public RestClient geminiHttpClient(GeminiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("x-goog-api-key", properties.apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
