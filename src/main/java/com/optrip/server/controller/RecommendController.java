package com.optrip.server.controller;

import com.optrip.server.dto.RecommendRequest;
import com.optrip.server.dto.RecommendResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RecommendController {

    @PostMapping("/recommend")
    public ResponseEntity<RecommendResponse> recommend(@RequestBody RecommendRequest request) {
        // @RequestBody → 프론트가 보낸 JSON을 RecommendRequest 객체로 자동 변환

        // 하드 코딩
        RecommendResponse response = new RecommendResponse(
                "제주도",
                "자연과 힐링의 섬",
                "예산과 일정에 딱 맞는 여행지예요",
                java.util.List.of("#오션뷰", "#맛집", "#힐링")
        );

        return ResponseEntity.ok(response); // 200 + 반환 데이터
    }
}
