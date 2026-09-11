package com.optrip.server.recommend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "recommend-v2", description = "DB 기반 추천 파이프라인 — 해석·지역·장소·일정")
@RestController
@RequestMapping("/api")
public class RecommendV2Controller {

    private final InterpretService interpretService;
    private final RegionRecommendService regionRecommendService;
    private final PlaceRecommendService placeRecommendService;
    private final ItineraryService itineraryService;
    private final JdbcTemplate jdbc;

    public RecommendV2Controller(InterpretService interpretService, RegionRecommendService regionRecommendService,
                                 PlaceRecommendService placeRecommendService, ItineraryService itineraryService,
                                 JdbcTemplate jdbc) {
        this.interpretService = interpretService;
        this.regionRecommendService = regionRecommendService;
        this.placeRecommendService = placeRecommendService;
        this.itineraryService = itineraryService;
        this.jdbc = jdbc;
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

    @Operation(summary = "(임시) 장소 상세 — 기본 정보는 DB 실데이터, 운영시간·개요는 프록시 연동 전까지 목업")
    @GetMapping("/places/{contentId}/detail")
    public Map<String, Object> detail(@PathVariable String contentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select content_id, title, addr1, mapx, mapy, first_image from place where content_id = ?", contentId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "장소 없음: " + contentId);
        }
        Map<String, Object> r = rows.get(0);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("contentId", r.get("content_id"));
        res.put("title", r.get("title"));
        res.put("addr1", r.get("addr1"));
        res.put("mapx", r.get("mapx"));
        res.put("mapy", r.get("mapy"));
        res.put("imageUrl", r.get("first_image"));
        res.put("overview", "장소 소개가 준비 중이에요. (상세 연동 전 임시 문구)");
        res.put("useTime", null);
        res.put("restDate", null);
        res.put("parking", null);
        res.put("fee", null);
        res.put("accessibility", Map.of("status", "UNKNOWN"));
        return res;
    }
}
