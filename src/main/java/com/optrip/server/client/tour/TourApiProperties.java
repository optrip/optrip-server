package com.optrip.server.client.tour;

import org.springframework.boot.context.properties.ConfigurationProperties;

// application.yml 의 tour.* 를 record 필드에 자동 바인딩
@ConfigurationProperties(prefix = "tour")
public record TourApiProperties(
        String serviceKey,      // 공공데이터포털 일반 인증키 (TOUR_API_KEY 환경변수)
        String baseUrl,         // https://apis.data.go.kr/B551011
        String adminToken,      // /admin/ingest/* 보호용 토큰 (ADMIN_TOKEN 환경변수)
        long callPauseMs        // 호출 간 대기 (일 한도 보호)
) {
}
