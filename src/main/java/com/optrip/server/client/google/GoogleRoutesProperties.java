package com.optrip.server.client.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.routes")
public record GoogleRoutesProperties(
        String apiKey,
        String baseUrl
) {
}
