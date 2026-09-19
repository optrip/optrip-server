package com.optrip.server.client.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.directions")
public record KakaoDirectionsProperties(
        String restApiKey,
        String baseUrl
) {
}
