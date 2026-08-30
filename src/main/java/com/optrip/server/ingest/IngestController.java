package com.optrip.server.ingest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Tag(name = "ingest", description = "TourAPI → DB 수집 (X-Admin-Token 필요)")
@RestController
@RequestMapping("/admin/ingest")
public class IngestController {

    private static final List<String> DEFAULT_CONTENT_TYPES = List.of("12", "14", "28", "38", "39");

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Operation(summary = "시도/시군구 + 신분류체계 코드 수집")
    @PostMapping("/codes")
    public Map<String, Object> codes() {
        return Map.of("runId", ingestService.ingestCodes());
    }

    @Operation(summary = "지역 장소 목록 수집 — areaCode(구코드) 또는 lDongRegnCd(법정동) 중 하나 필수")
    @PostMapping("/area-list")
    public Map<String, Object> areaList(
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) String sigunguCode,
            @RequestParam(required = false) String lDongRegnCd,
            @RequestParam(required = false) String contentTypeIds) {
        if ((areaCode == null || areaCode.isBlank()) && (lDongRegnCd == null || lDongRegnCd.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "areaCode 또는 lDongRegnCd 가 필요합니다");
        }
        List<String> types = contentTypeIds == null || contentTypeIds.isBlank()
                ? DEFAULT_CONTENT_TYPES
                : Arrays.stream(contentTypeIds.split(",")).map(String::trim).toList();
        return Map.of("runId", ingestService.ingestAreaList(areaCode, sigunguCode, lDongRegnCd, types));
    }

    @Operation(summary = "장소 상세 수집 (detailCommon2 + detailIntro2)")
    @PostMapping("/details")
    public Map<String, Object> details(
            @RequestParam String areaCode,
            @RequestParam(defaultValue = "300") int limit) {
        return Map.of("runId", ingestService.ingestDetails(areaCode, limit));
    }

    @Operation(summary = "무장애 정보 수집 (detailWithTour2)")
    @PostMapping("/accessibility")
    public Map<String, Object> accessibility(
            @RequestParam String areaCode,
            @RequestParam(defaultValue = "300") int limit) {
        return Map.of("runId", ingestService.ingestAccessibility(areaCode, limit));
    }

    @Operation(summary = "수집 실행 조회")
    @GetMapping("/runs/{runId}")
    public Map<String, Object> run(@PathVariable long runId) {
        return ingestService.run(runId);
    }

    @Operation(summary = "최근 수집 실행 목록")
    @GetMapping("/runs")
    public List<Map<String, Object>> runs(@RequestParam(defaultValue = "20") int limit) {
        return ingestService.runs(limit);
    }

    @Operation(summary = "실측 통계 (후보 수·결측률·호출 성능·무장애 커버리지)")
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return ingestService.stats();
    }
}
