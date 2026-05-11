package com.optrip.server.client.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

// application.yml 의 gemini.* 를 record 필드에 자동 바인딩
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        String baseUrl,
        String model
) {
}
