package com.optrip.server.controller;

import com.optrip.server.service.PlaceStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "admin-place-stats", description = "추천 알고리즘 검증용 장소·매핑 조회 통계 (X-Admin-Token 필요)")
@RestController
@RequestMapping("/admin/stats")
public class PlaceStatsController {

    private final PlaceStatsService placeStatsService;

    public PlaceStatsController(PlaceStatsService placeStatsService) {
        this.placeStatsService = placeStatsService;
    }

    @Operation(summary = "장소 데이터 전체 현황",
            description = "전체 장소와 v0-draft intent 보유 여부, 이미지·좌표의 존재/결측 건수 및 비율을 조회합니다.")
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return placeStatsService.overview();
    }

    @Operation(summary = "추구미별 장소 분포",
            description = "v0-draft purpose_label별 distinct 장소 수와 필터된 전체 장소 대비 비율을 조회합니다. " +
                    "지역 필터는 areaBasedList2 원문 JSON의 법정동 코드를 사용합니다.")
    @GetMapping("/purposes")
    public Map<String, Object> purposes(
            @Parameter(description = "법정동 시도 코드. 예: 51")
            @RequestParam(required = false) String lDongRegnCd,
            @Parameter(description = "법정동 시군구 코드. lDongRegnCd와 함께 사용")
            @RequestParam(required = false) String lDongSignguCd) {
        return placeStatsService.purposes(lDongRegnCd, lDongSignguCd);
    }

    @Operation(summary = "장소별 다중 추구미 분포",
            description = "v0-draft intent가 0개, 1개, 2개, 3개 이상인 장소 수와 exact purpose_count별 분포를 조회합니다. " +
                    "지역 필터는 areaBasedList2 원문 JSON의 법정동 코드를 사용합니다.")
    @GetMapping("/multilabel")
    public Map<String, Object> multilabel(
            @Parameter(description = "법정동 시도 코드. 예: 51")
            @RequestParam(required = false) String lDongRegnCd,
            @Parameter(description = "법정동 시군구 코드. lDongRegnCd와 함께 사용")
            @RequestParam(required = false) String lDongSignguCd) {
        return placeStatsService.multilabel(lDongRegnCd, lDongSignguCd);
    }

    @Operation(summary = "분류-추구미 매핑 목록",
            description = "v0-draft taxonomy_mapping을 조회합니다. REVIEW 사유를 확인할 수 있도록 note를 포함합니다.")
    @GetMapping("/mappings")
    public List<Map<String, Object>> mappings(
            @Parameter(description = "매핑 상태: MAPPED, REVIEW, EXCLUDED")
            @RequestParam(required = false) String status,
            @Parameter(description = "분류 코드의 부분 일치 검색. 예: NA01")
            @RequestParam(required = false) String sourceCode,
            @Parameter(description = "추구미 라벨의 부분 일치 검색. 예: 자연/풍경")
            @RequestParam(required = false) String purpose) {
        return placeStatsService.mappings(status, sourceCode, purpose);
    }

    @Operation(summary = "매핑 상태별 규모와 영향 장소 수",
            description = "v0-draft의 MAPPED/REVIEW/EXCLUDED별 taxonomy_mapping row 수와 " +
                    "해당 lcls2 분류를 사용하는 distinct 장소 수를 구분하여 조회합니다. " +
                    "한 lcls2에 여러 상태의 매핑이 있으면 같은 장소가 MAPPED, REVIEW 등 여러 상태의 " +
                    "distinctAffectedPlaceCount에 각각 중복 집계될 수 있으므로 상태별 수를 합산하면 안 됩니다.")
    @GetMapping("/mapping-status")
    public List<Map<String, Object>> mappingStatus() {
        return placeStatsService.mappingStatus();
    }

    @Operation(summary = "신분류 코드별 장소·매핑 현황",
            description = "사용 중인 lcls1/lcls2/lcls3 조합, 각 코드명, 장소 수와 v0-draft 중분류 매핑을 조회합니다. " +
                    "NA01·NA02 같은 중분류 아래의 실제 lcls3 사용량 확인에 사용할 수 있습니다.")
    @GetMapping("/lcls")
    public List<Map<String, Object>> lcls(
            @Parameter(description = "대분류 코드 exact match. 예: NA")
            @RequestParam(required = false) String lcls1,
            @Parameter(description = "중분류 코드 exact match. 예: NA01")
            @RequestParam(required = false) String lcls2) {
        return placeStatsService.lcls(lcls1, lcls2);
    }

    @Operation(summary = "콘텐츠 타입별 장소 수",
            description = "contentTypeId별 장소 수를 조회합니다. 지역 필터는 areaBasedList2 원문 JSON의 법정동 코드를 사용합니다.")
    @GetMapping("/content-types")
    public Map<String, Object> contentTypes(
            @Parameter(description = "법정동 시도 코드. 예: 51")
            @RequestParam(required = false) String lDongRegnCd,
            @Parameter(description = "법정동 시군구 코드. lDongRegnCd와 함께 사용")
            @RequestParam(required = false) String lDongSignguCd) {
        return placeStatsService.contentTypes(lDongRegnCd, lDongSignguCd);
    }

    @Operation(summary = "추구미 미분류 장소의 판별 가능한 원인",
            description = "v0-draft intent가 없는 장소를 lcls2 매핑 상태로 구분합니다. " +
                    "MAPPED 규칙 누락, REVIEW, EXCLUDED, 매핑 없음, 기타 순으로 상호 배타적으로 집계하며 " +
                    "분류의 의미가 실제 장소에 적합한지는 판정하지 않습니다.")
    @GetMapping("/unclassified-reasons")
    public Map<String, Object> unclassifiedReasons() {
        return placeStatsService.unclassifiedReasons();
    }
}
