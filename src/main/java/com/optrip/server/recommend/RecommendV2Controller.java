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

    @Operation(summary = "자연어 해석", description = """
            사용자의 자유 문장을 10개 추구미 안에서 해석합니다. Gemini가 목록 밖 값을 만들 수 없게 제한됩니다.

            요청: {"text": "한식 맛집 찾아다니고 예쁜 카페에서 쉬고 싶어"}

            응답:
            {
              "purposes": ["맛집", "카페투어"],                      // 1~3개. 해석 실패 시 빈 배열 → 앱은 취향 직접 선택 화면으로
              "summary": "한식 맛집을 찾아다니고 예쁜 카페에서 쉬는 여행",   // 이해 확인 화면 문구
              "mappings": [                                        // 표현별 해석 근거 (최대 3개)
                {"phrase": "한식 맛집", "purposes": ["맛집"]}
              ]
            }
            """)
    @PostMapping("/interpret")
    public InterpretService.InterpretResult interpret(@RequestBody InterpretService.InterpretRequest request) {
        return interpretService.interpret(request);
    }

    @Operation(summary = "지역 추천", description = """
            시군구별 추구미 후보 집계로 지역을 추천합니다. 같은 입력이면 같은 결과(재현성).

            요청:
            {
              "purposes": ["바다", "맛집"],
              "destinations": ["경주"],              // 선택. 사용자가 입력한 목적지 → source=user로 항상 포함
              "originMapx": 126.97, "originMapy": 37.56,  // 선택. 현재 위치 좌표
              "originName": "수원",                  // 선택. 출발지 직접 입력 (좌표가 있으면 좌표 우선)
              "excludeRegions": ["51-150"],          // 선택. 다른 지역 다시 추천 시 제외 목록
              "limit": 3
            }

            응답 regions[] 항목:
            {
              "name": "강릉시", "lDongRegnCd": "51", "lDongSignguCd": "150",
              "source": "user | ai",                 // AI 추천 뱃지 분기
              "reasons": ["맛집 명소 348곳"],          // 카드용 짧은 문구
              "reasonsDetail": [{"title": "...", "description": "..."}],  // 상세 화면의 추천 이유 목록
              "matchedPurposes": 2, "totalPurposes": 2,   // "취향 2/2 일치" 표기용
              "candidateCount": 379,                 // 후보 N곳
              "travelFromOrigin": {"mode": "대중교통", "durationMinutes": 128, "summary": "대중교통 약 2시간 10분"},  // 출발지 없으면 null
              "imageUrl": "... 또는 null"
            }
            """)
    @PostMapping("/recommend/regions")
    public Map<String, Object> regions(@RequestBody RegionRecommendService.RegionRequest request) {
        return regionRecommendService.recommend(request);
    }

    @Operation(summary = "장소 추천", description = """
            선택한 지역에서 core(꼭 가봐야할 곳)와 suggestions(추가 선택지)를 추천합니다.

            요청:
            {
              "lDongRegnCd": "47", "lDongSignguCd": "130",
              "purposes": ["역사/문화", "맛집"],
              "coreCount": 2,                        // 기본 2
              "suggestionsPerPurpose": 3             // FOR YOU 화면용: 취향별 N개씩. 미지정 시 suggestionCount(기본 6)로 통합 반환
            }

            응답 core[]/suggestions[] 항목:
            {
              "contentId": "128526", "title": "경주 동궁과 월지", "addr1": "...",
              "mapx": 129.22, "mapy": 35.83,
              "imageUrl": "... 또는 null",             // null이면 placeholder 필요
              "purpose": "역사/문화",                  // suggestions 그룹핑(탭) 기준
              "reason": "역사유적지 · '역사/문화' 취향과 맞는 곳이라 먼저 추천해요"
            }
            """)
    @PostMapping("/recommend/places")
    public Map<String, Object> places(@RequestBody PlaceRecommendService.PlaceRequest request) {
        return placeRecommendService.recommend(request);
    }

    @Operation(summary = "일정 조립", description = """
            담은 장소를 DAY별 일정으로 만듭니다. 사용자가 배치한 순서를 그대로 유지합니다(optimizeOrder=true면 거리 기준 자동 정렬).
            transport를 바꿔 재호출하면 이동수단 토글 재계산이 됩니다.

            요청: {"placeIds": ["128526", "1492402"], "transport": "대중교통", "days": 2, "optimizeOrder": false}
            에러: placeIds 비면 400, 없는 장소 404, 좌표 없는 장소 422

            응답:
            {
              "transport": "대중교통",
              "days": [{"day": 1, "items": [{
                "place": {"contentId": "...", "title": "...", "addr1": "...", "mapx": 0, "mapy": 0, "imageUrl": "..."},
                "legToNext": {                       // 그 날 마지막 장소에는 없음
                  "mode": "도보 | 대중교통 | 자동차",
                  "summary": "버스 11 이용 · 약 15분 소요",   // 대중교통은 Google Routes 실노선
                  "durationMinutes": 15, "distanceMeters": 4200,
                  "encodedPolyline": "..."           // 지도 경로선용. 도보/추정 구간엔 없음
                }
              }]}]
            }
            참고: 자동차는 경로 API 미연동 상태라 거리 기반 추정치입니다.
            """)
    @PostMapping("/itinerary")
    public Map<String, Object> itinerary(@RequestBody ItineraryService.ItineraryRequest request) {
        return itineraryService.build(request);
    }

    @Operation(summary = "장소 상세", description = """
            기본 정보는 DB, 상세 항목(overview·운영시간 등)은 TourAPI 온디맨드 조회 후 캐시합니다.
            첫 조회는 1~3초, 캐시 후엔 즉시. 원천에 없는 항목은 null → 앱에서 해당 항목 숨김 처리.

            응답:
            {
              "contentId": "128526", "title": "경주 동궁과 월지", "addr1": "...",
              "mapx": 129.22, "mapy": 35.83, "imageUrl": "...",
              "purposes": ["역사/문화", "자연/풍경"],           // 이 장소와 매칭된 추구미. "N 취향과 잘 맞는 장소예요" 문구용. 없으면 빈 배열
              "tel": "... 또는 null",
              "overview": "신라 왕궁의 별궁 터... 또는 null",
              "homepage": null, "useTime": "09:00~22:00 또는 null",
              "restDate": null, "parking": null, "fee": null,
              "accessibility": {
                "status": "ACCESSIBLE | LIMITED | UNKNOWN",   // 미확인은 UNKNOWN 유지 (가능/불가로 단정하지 않음)
                "wheelchair": "대여가능(3대) 또는 null", "elevator": null, "restroom": null, "parking": null, "exit": null
              }
            }
            없는 contentId는 404.
            """)
    @GetMapping("/places/{contentId}/detail")
    public Map<String, Object> detail(@PathVariable String contentId) {
        return tourDetailService.detail(contentId);
    }
}
