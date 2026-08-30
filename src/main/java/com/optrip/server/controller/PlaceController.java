package com.optrip.server.controller;

import com.optrip.server.service.PlaceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "place", description = "수집된 관광 장소 조회")
@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceQueryService placeQueryService;

    public PlaceController(PlaceQueryService placeQueryService) {
        this.placeQueryService = placeQueryService;
    }

    @Operation(summary = "장소 검색",
            description = "법정동 코드(lDongRegnCd)·타입·추구미·이름으로 장소를 검색합니다. 예: /api/places?lDongRegnCd=51&purpose=맛집")
    @GetMapping
    public Map<String, Object> search(
            @RequestParam(required = false) String lDongRegnCd,
            @RequestParam(required = false) String lDongSignguCd,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return placeQueryService.search(lDongRegnCd, lDongSignguCd, contentTypeId, purpose, q,
                Math.clamp(limit, 1, 100), Math.max(offset, 0));
    }
}
