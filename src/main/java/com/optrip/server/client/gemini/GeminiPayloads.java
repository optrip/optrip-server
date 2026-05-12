package com.optrip.server.client.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

// Gemini generateContent API 의 request/response 매핑용 DTO 모음.
// 한 파일에 묶어둔다 — 외부에 노출할 필요 없는 내부 표현이라 응집도 우선.
public final class GeminiPayloads {

    private GeminiPayloads() {}

    public record GenerateContentRequest(
            List<Content> contents,
            GenerationConfig generationConfig
    ) {}

    public record Content(String role, List<Part> parts) {}

    public record Part(String text) {}

    public record GenerationConfig(
            String responseMimeType,
            Map<String, Object> responseSchema
    ) {}

    // Gemini 응답에는 usageMetadata 등 우리가 안 쓰는 필드가 많아서 unknown 무시
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateContentResponse(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {}
}
