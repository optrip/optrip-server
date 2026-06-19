package com.optrip.server.controller;

import com.optrip.server.dto.CourseRecommendation.CourseList;
import com.optrip.server.dto.RecommendRequest;
import com.optrip.server.dto.RegionResponse;
import com.optrip.server.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @PostMapping("/region")
    @Operation(summary = "여행 지역 추천",
            description = "조건에 맞는 지역 1곳을 추천합니다. excludeRegions 로 이미 본 지역을 제외하면 다른 지역을 다시 추천합니다.")
    public ResponseEntity<RegionResponse> region(@RequestBody RecommendRequest request) {
        return ResponseEntity.ok(recommendService.recommendRegion(request));
    }

    @PostMapping("/courses")
    @Operation(summary = "지역별 코스 추천",
            description = "선택한 지역에 대해 purpose 별 코스와 일자별 방문지/이동수단을 한 번에 생성합니다.")
    public ResponseEntity<CourseList> courses(@RequestBody RecommendRequest request) {
        return ResponseEntity.ok(recommendService.recommendCourses(request));
    }
}
