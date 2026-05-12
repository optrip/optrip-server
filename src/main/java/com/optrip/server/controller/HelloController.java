package com.optrip.server.controller;

import com.optrip.server.client.gemini.GeminiClient;
import com.optrip.server.dto.GeminiTestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// @RestController → JSON을 반환하는 컨트롤러. @Controller + @ResponseBody 합친 것
// @RequestMapping → 이 컨트롤러의 모든 경로 앞에 "/api" 붙음
@RestController
@RequestMapping("/api")
@Tag(name = "Hello", description = "테스트 API")
public class HelloController {
    private final GeminiClient geminiClient;

    public HelloController(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    // @GetMapping → GET 방식 HTTP 요청을 받음
    // 전체 경로: GET /api/hello
    @GetMapping("/hello")
    @Operation(summary = "헬스 체크", description = "서버가 살아있는지 확인하는 간단한 핑")
    public ResponseEntity<Map<String, String>> hello() {
        // ResponseEntity → HTTP 상태코드(200, 404 등) + 데이터를 함께 반환
        // Map.of() → 간단한 JSON 만들기. {"message": "..."}
        return ResponseEntity.ok(
                Map.of("message", "optrip server is running!!!")
        );
    }

    // Gemini 호출 테스트용: GET /api/gemini-test?prompt=...
    @GetMapping("/gemini-test")
    @Operation(summary = "Gemini 호출 테스트", description = "prompt 파라미터를 받아 Gemini 응답을 반환")
    public ResponseEntity<Map<String, String>> geminiTest(
            @Parameter(description = "Gemini에 전달할 프롬프트", example = "안녕")
            @RequestParam String prompt) {
        Map<String, Object> responseSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "response", Map.of("type", "STRING")
                ),
                "required", List.of("response")
        );

        GeminiTestResponse result = geminiClient.generateStructured(prompt, responseSchema, GeminiTestResponse.class);
        return ResponseEntity.ok(Map.of("response", result.response()));
    }
}
