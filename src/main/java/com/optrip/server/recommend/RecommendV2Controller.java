package com.optrip.server.recommend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "recommend-v2", description = "DB 기반 추천 파이프라인 — 해석·지역·장소·일정")
@RestController
@RequestMapping("/api")
public class RecommendV2Controller {

    private final InterpretService interpretService;
    private final RegionRecommendService regionRecommendService;
    private final PlaceRecommendService placeRecommendService;
    private final ItineraryService itineraryService;
    private final TourDetailService tourDetailService;

    public RecommendV2Controller(InterpretService interpretService, RegionRecommendService regionRecommendService,
                                 PlaceRecommendService placeRecommendService, ItineraryService itineraryService,
                                 TourDetailService tourDetailService) {
        this.interpretService = interpretService;
        this.regionRecommendService = regionRecommendService;
        this.placeRecommendService = placeRecommendService;
        this.itineraryService = itineraryService;
        this.tourDetailService = tourDetailService;
    }

    @Operation(summary = "자연어 해석 — 추구미 1~3개 + 이해 확인용 summary. 해석 실패 시 purposes가 빈 배열")
    @PostMapping("/interpret")
    public InterpretService.InterpretResult interpret(@RequestBody InterpretService.InterpretRequest request) {
        return interpretService.interpret(request);
    }

    @Operation(summary = "지역 추천 — source가 user면 사용자 목적지, ai면 시스템 추천. excludeRegions로 재추천")
    @PostMapping("/recommend/regions")
    public Map<String, Object> regions(@RequestBody RegionRecommendService.RegionRequest request) {
        return regionRecommendService.recommend(request);
    }

    @Operation(summary = "장소 추천 — core는 꼭 가봐야할 곳, suggestions의 purpose가 담기 화면의 탭")
    @PostMapping("/recommend/places")
    public Map<String, Object> places(@RequestBody PlaceRecommendService.PlaceRequest request) {
        return placeRecommendService.recommend(request);
    }

    @Operation(summary = "일정 조립 — 담은 장소를 DAY별 시간표로. transport(대중교통/자동차) 바꿔 재호출하면 재계산")
    @PostMapping("/itinerary")
    public Map<String, Object> itinerary(@RequestBody ItineraryService.ItineraryRequest request) {
        return itineraryService.build(request);
    }

    @Operation(summary = "장소 상세 — TourAPI 온디맨드 조회 + DB 캐시. 조회 실패 필드는 null, 접근성은 UNKNOWN 유지")
    @GetMapping("/places/{contentId}/detail")
    public Map<String, Object> detail(@PathVariable String contentId) {
        return tourDetailService.detail(contentId);
    }
}
