package com.optrip.server.controller;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "recommend-v2 (mock)", description = "앱 개발용 목업 API — 경로·스키마는 계약으로 유지되며 내부 구현만 실물로 교체됩니다")
@RestController
@RequestMapping("/api")
public class MockRecommendController {

    private final JdbcTemplate jdbc;

    public MockRecommendController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Operation(summary = "(mock) 자연어 해석 — 문장을 추구미 1~3개와 요약문으로 변환")
    @PostMapping("/interpret")
    public Map<String, Object> interpret(@RequestBody(required = false) Map<String, Object> req) {
        return Map.of(
                "purposes", List.of("자연/풍경", "힐링", "맛집"),
                "summary", "단풍이 아름답고 자연으로 둘러싸인 곳에서 한가롭게 힐링하고, 맛있는 것을 많이 먹으러 다니는 여행"
        );
    }

    @Operation(summary = "(mock) 지역 추천 — source가 user면 사용자가 입력한 목적지, ai면 시스템 추천")
    @PostMapping("/recommend/regions")
    public Map<String, Object> regions(@RequestBody(required = false) Map<String, Object> req) {
        return Map.of("regions", List.of(
                Map.of("name", "경주", "lDongRegnCd", "47", "lDongSignguCd", "130", "source", "user",
                        "reasons", List.of("아름다운 단풍", "다양한 고대 유물들"),
                        "imageUrl", "https://tong.visitkorea.or.kr/cms/resource/71/4056771_image2_1.jpg"),
                Map.of("name", "동해", "lDongRegnCd", "51", "lDongSignguCd", "170", "source", "ai",
                        "reasons", List.of("푸른 동해 바다", "싱싱한 회"),
                        "imageUrl", "http://tong.visitkorea.or.kr/cms/resource/64/3070464_image2_1.jpg"),
                Map.of("name", "속초", "lDongRegnCd", "51", "lDongSignguCd", "210", "source", "ai",
                        "reasons", List.of("풍부한 놀거리", "오죽헌", "짬뽕"),
                        "imageUrl", "http://tong.visitkorea.or.kr/cms/resource/95/3394195_image2_1.JPG")
        ));
    }

    @Operation(summary = "(mock) 장소 추천 — core는 꼭 가봐야할 곳, suggestions의 purpose가 담기 화면의 탭")
    @PostMapping("/recommend/places")
    public Map<String, Object> places(@RequestBody(required = false) Map<String, Object> req) {
        return Map.of(
                "core", List.of(
                        placeCard("128526", "역사/문화", "통일 신라 시대의 왕실 별궁 터이자 뛰어난 조경 예술을 보여주는 인공 연못. 단풍이 매우 아름다운 시기라 우선 추천"),
                        placeCard("1492402", "역사/문화", "신라 왕릉이 모여 있는 고분군. 산책하며 둘러보기 좋아 힐링 취향에 맞음")
                ),
                "suggestions", List.of(
                        placeCard("126207", "역사/문화", "동궁과 월지에서 도보 거리의 대표 유적"),
                        placeCard("133964", "맛집", "경주 교촌마을의 한정식"),
                        placeCard("134159", "맛집", "대릉원 인근의 쌈밥 골목 대표 식당"),
                        placeCard("128811", "힐링", "온천 스파로 여행 피로 회복"),
                        placeCard("3006834", "문화체험", "근대 가옥을 개조한 복합문화공간"),
                        placeCard("126166", "역사/문화", "유네스코 세계유산. 시내에서 차량 20분")
                )
        );
    }

    @Operation(summary = "(mock) 장소 상세 — 기본 정보는 DB 실데이터, 상세 항목은 목업")
    @GetMapping("/places/{contentId}/detail")
    public Map<String, Object> detail(@PathVariable String contentId) {
        Map<String, Object> p = findPlace(contentId);
        Map<String, Object> res = new LinkedHashMap<>(p);
        res.put("overview", "통일 신라 시대의 왕실 별궁 터이자 뛰어난 조경 예술을 보여주는 인공 연못. 야간 조명이 켜지는 저녁 시간대가 특히 아름답다. (mock)");
        res.put("useTime", "09:00 ~ 22:00 (입장 마감 21:30)");
        res.put("restDate", "연중무휴");
        res.put("parking", "가능 (무료)");
        res.put("fee", "성인 3,000원");
        res.put("accessibility", Map.of("status", "UNKNOWN", "wheelchair", "확인 필요", "elevator", "확인 필요"));
        return res;
    }

    @Operation(summary = "(mock) 일정 조립 — 담은 장소를 DAY별 시간표로. transport 바꿔 재호출하면 이동수단 재계산")
    @PostMapping("/itinerary")
    public Map<String, Object> itinerary(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<String> placeIds = (List<String>) req.getOrDefault("placeIds", List.of());
        String transport = String.valueOf(req.getOrDefault("transport", "대중교통"));
        int days = Math.max(1, ((Number) req.getOrDefault("days", 1)).intValue());

        List<Map<String, Object>> resolved = placeIds.stream().map(this::findPlace).toList();
        int perDay = (int) Math.ceil((double) resolved.size() / days);

        List<Map<String, Object>> dayList = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (int i = d * perDay; i < Math.min((d + 1) * perDay, resolved.size()); i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("place", resolved.get(i));
                boolean last = i == Math.min((d + 1) * perDay, resolved.size()) - 1;
                if (!last) {
                    String mode = i % 2 == 0 ? "도보" : transport;
                    String summary = mode.equals("도보") ? "도보 · 약 5분 소요"
                            : mode.equals("자동차") ? "자동차 · 약 10분 소요"
                            : "지하철 이용 · 1회 환승 · 약 15분 소요";
                    item.put("legToNext", Map.of("mode", mode, "summary", summary));
                }
                items.add(item);
            }
            dayList.add(Map.of("day", d + 1, "items", items));
        }
        return Map.of("transport", transport, "days", dayList);
    }

    private Map<String, Object> placeCard(String contentId, String purpose, String reason) {
        Map<String, Object> m = new LinkedHashMap<>(findPlace(contentId));
        m.put("purpose", purpose);
        m.put("reason", reason);
        return m;
    }

    private Map<String, Object> findPlace(String contentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select content_id, title, addr1, mapx, mapy, first_image from place where content_id = ?", contentId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "장소 없음: " + contentId);
        }
        Map<String, Object> r = rows.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contentId", r.get("content_id"));
        m.put("title", r.get("title"));
        m.put("addr1", r.get("addr1"));
        m.put("mapx", r.get("mapx"));
        m.put("mapy", r.get("mapy"));
        m.put("imageUrl", r.get("first_image"));
        return m;
    }
}
