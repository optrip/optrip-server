package com.optrip.server.controller;

import com.optrip.server.dto.RecommendRequest;
import com.optrip.server.dto.RecommendResponse;
import com.optrip.server.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RecommendController {

    private final RecommendService recommendService;

    // 생성자 주입(spring이 주입)
    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @PostMapping("/recommend")
    public ResponseEntity<RecommendResponse> recommend(@RequestBody RecommendRequest request) {
        // @RequestBody → 프론트가 보낸 JSON을 RecommendRequest 객체로 자동 변환

        RecommendResponse response = recommendService.recommend(request);
        return ResponseEntity.ok(response); // 200 + 반환 데이터
    }

    @PostMapping("/recommend-ai")
    @Operation(summary = "AI 여행지 추천",
            description = "Gemini AI 기반으로 여행지를 추천합니다")
    public ResponseEntity<RecommendResponse> recommendWithAI(
            @RequestBody RecommendRequest request) {
        return ResponseEntity.ok(recommendService.recommendWithAI(request));
    }
}
