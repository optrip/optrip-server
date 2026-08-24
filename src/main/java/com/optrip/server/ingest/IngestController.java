package com.optrip.server.ingest;

import com.optrip.server.client.tour.TourApiProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

// TourAPI 수집 트리거. 수집은 백그라운드로 돌고 runId 로 진행 상황을 조회한다.
// 공개 서버에 노출되므로 X-Admin-Token 헤더로 보호한다.
@Tag(name = "ingest", description = "TourAPI → DB 수집 (관리자 전용)")
@RestController
@RequestMapping("/admin/ingest")
public class IngestController {

    // 회의·V2.1 기준 기본 수집 타입: 관광지(12), 문화시설(14), 레포츠(28), 음식점(39)
    private static final List<String> DEFAULT_CONTENT_TYPES = List.of("12", "14", "28", "39");

    private final IngestService ingestService;
    private final TourApiProperties properties;

    public IngestController(IngestService ingestService, TourApiProperties properties) {
        this.ingestService = ingestService;
        this.properties = properties;
    }

    private void authorize(String token) {
        String expected = properties.adminToken();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ADMIN_TOKEN 이 설정되지 않았습니다");
        }
        if (!expected.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "잘못된 관리자 토큰");
        }
    }

    @Operation(summary = "시도/시군구 + 신분류체계 코드 수집")
    @PostMapping("/codes")
    public Map<String, Object> codes(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authorize(token);
        return Map.of("runId", ingestService.ingestCodes());
    }

    @Operation(summary = "지역 장소 목록 수집 (areaBasedList2)")
    @PostMapping("/area-list")
    public Map<String, Object> areaList(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam String areaCode,
            @RequestParam(required = false) String sigunguCode,
            @RequestParam(required = false) String contentTypeIds) {
        authorize(token);
        List<String> types = contentTypeIds == null || contentTypeIds.isBlank()
                ? DEFAULT_CONTENT_TYPES
                : Arrays.stream(contentTypeIds.split(",")).map(String::trim).toList();
        return Map.of("runId", ingestService.ingestAreaList(areaCode, sigunguCode, types));
    }

    @Operation(summary = "장소 상세 수집 (detailCommon2 + detailIntro2)")
    @PostMapping("/details")
    public Map<String, Object> details(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam String areaCode,
            @RequestParam(defaultValue = "300") int limit) {
        authorize(token);
        return Map.of("runId", ingestService.ingestDetails(areaCode, limit));
    }

    @Operation(summary = "무장애 정보 수집 (detailWithTour2)")
    @PostMapping("/accessibility")
    public Map<String, Object> accessibility(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam String areaCode,
            @RequestParam(defaultValue = "300") int limit) {
        authorize(token);
        return Map.of("runId", ingestService.ingestAccessibility(areaCode, limit));
    }

    @Operation(summary = "수집 실행 조회")
    @GetMapping("/runs/{runId}")
    public Map<String, Object> run(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable long runId) {
        authorize(token);
        return ingestService.run(runId);
    }

    @Operation(summary = "최근 수집 실행 목록")
    @GetMapping("/runs")
    public List<Map<String, Object>> runs(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "20") int limit) {
        authorize(token);
        return ingestService.runs(limit);
    }

    @Operation(summary = "Phase 3 실측 통계 (후보 수·결측률·호출 성능·무장애 커버리지)")
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authorize(token);
        return ingestService.stats();
    }
}
