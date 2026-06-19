package com.optrip.server.dto;

import java.util.List;

// 지역 추천 화면(Image #1)에 쓰이는 단일 지역 응답.
// Gemini 구조화 응답을 그대로 역직렬화한다.
public record RegionResponse(
        String regionName,   // 지역명: "경주"
        String description,  // 한 줄 설명
        String reason,       // 추천 이유
        List<String> tags    // 해시태그
) {
}
