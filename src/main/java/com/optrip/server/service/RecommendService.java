package com.optrip.server.service;

import com.optrip.server.client.gemini.GeminiClient;
import com.optrip.server.dto.CourseRecommendation.CourseList;
import com.optrip.server.dto.RecommendRequest;
import com.optrip.server.dto.RegionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service  // 비즈니스 로직 담당
public class RecommendService {

    // 여행 기간 상한: 최대 7박 8일
    private static final int MAX_NIGHTS = 7;

    // 이동수단 허용 값
    private static final List<String> ALLOWED_TRANSPORT = List.of("자동차", "대중교통");

    // 추구하는 여행(purpose) 선택 개수 제한
    private static final int MIN_PURPOSE = 1;
    private static final int MAX_PURPOSE = 3;

    private final GeminiClient geminiClient;

    public RecommendService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    // ──────────────────────────────────────────────
    // 1) 지역 추천 (Image #1)
    // ──────────────────────────────────────────────
    public RegionResponse recommendRegion(RecommendRequest request) {
        validate(request);

        Trip trip = resolveTrip(request);

        String excludeText = (request.getExcludeRegions() == null || request.getExcludeRegions().isEmpty())
                ? "(없음)"
                : String.join(", ", request.getExcludeRegions());

        String prompt = String.format("""
                당신은 한국 여행지 추천 전문가입니다.
                아래 조건에 맞는 한국 여행지(시/군 단위) 1곳을 추천해주세요.

                예산: %s
                기간: %s
                동행: %s
                이동수단: %s
                추구하는 여행 스타일: %s

                [이미 추천해서 제외할 지역]
                %s

                [중요 규칙]
                - 위 제외 목록에 있는 지역은 절대 추천하지 마세요.
                - 너무 뻔한 곳보다 조건에 잘 맞는 개성 있는 곳을 추천해주세요.
                - regionName 은 "경주", "강릉" 처럼 도시/지역 이름만 간결하게 작성하세요.
                - description 은 반드시 25자 이내 한 줄로 작성하세요.
                - reason 은 반드시 2문장 이내로 작성하세요.
                - tags 는 아래 목록에서만 최대 3개 선택하세요:
                  #힐링 #맛집 #감성/사진 #자연/풍경 #역사/문화 #액티비티 #문화체험 #카페투어
                """,
                nullSafe(request.getBudget()),
                trip.label(),
                nullSafe(request.getCompanion()),
                request.getTransport(),
                request.getPurpose(),
                excludeText
        );

        Map<String, Object> responseSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "regionName", Map.of("type", "STRING"),
                        "description", Map.of("type", "STRING"),
                        "reason", Map.of("type", "STRING"),
                        "tags", Map.of(
                                "type", "ARRAY",
                                "items", Map.of("type", "STRING")
                        )
                ),
                "required", List.of("regionName", "description", "reason", "tags")
        );

        return geminiClient.generateStructured(prompt, responseSchema, RegionResponse.class);
    }

    // ──────────────────────────────────────────────
    // 2) 코스 추천 (Image #2, #3)
    //    선택한 지역에 대해 purpose 별 코스 + 일자별 방문지/이동수단을 한 번에 생성
    // ──────────────────────────────────────────────
    public CourseList recommendCourses(RecommendRequest request) {
        validate(request);

        if (request.getRegionName() == null || request.getRegionName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regionName 은 필수입니다.");
        }

        Trip trip = resolveTrip(request);

        String transportRule = "대중교통".equals(request.getTransport())
                ? "이동수단은 대중교통 기준입니다. mode 는 \"지하철\", \"버스\", \"도보\", \"택시\" 중에서 사용하고, 환승이 있으면 note 에 \"N회 환승\"을 적으세요."
                : "이동수단은 자동차 기준입니다. mode 는 \"자동차\" 또는 짧은 거리의 \"도보\"를 사용하세요. note 는 보통 빈 문자열입니다.";

        String prompt = String.format("""
                당신은 한국 여행 코스 설계 전문가입니다.
                "%s" 지역에 대해, 아래 조건에 맞는 여행 코스를 설계해주세요.

                예산: %s
                기간: %s (총 %d일)
                동행: %s
                이동수단: %s

                [코스 구성 규칙]
                - 사용자가 고른 여행 스타일(purpose) 각각에 대해 코스를 1개씩 만드세요.
                  대상 purpose: %s
                - 즉, courses 배열의 길이는 정확히 %d 이어야 하고, 각 course 의 purpose 는 위 목록과 일치해야 합니다.
                - 각 코스는 정확히 %d일(day 1 ~ day %d) 일정으로 구성하세요.
                - 하루(day)에는 2~4개의 방문지를 배치하세요.
                - title 은 "%s %s 여행 경로" 형식으로 작성하세요. (%s 자리에 purpose)
                - summary 는 주요 방문지를 "A · B · C 등" 형식으로 요약하세요.
                - 각 방문지(visit)에는 실제 존재하는 장소의 정확한 위도(latitude)/경도(longitude)를 넣으세요.
                - description 은 방문지를 한 줄(40자 내외)로 소개하세요.

                [이동수단 규칙]
                - %s
                - 각 방문지의 transportToNext 는 "그 방문지 -> 같은 날 다음 방문지"로 가는 이동 정보입니다.
                - 각 day 의 마지막 방문지는 transportToNext 를 넣지 마세요(생략).
                - durationMinutes 는 예상 소요 시간(분)입니다.
                """,
                request.getRegionName(),
                nullSafe(request.getBudget()),
                trip.label(), trip.days(),
                nullSafe(request.getCompanion()),
                request.getTransport(),
                request.getPurpose(),
                request.getPurpose().size(),
                trip.days(), trip.days(),
                request.getRegionName(), "{purpose}", "{purpose}",
                transportRule
        );

        return geminiClient.generateStructured(prompt, courseSchema(), CourseList.class);
    }

    // 코스 추천 응답 스키마 (중첩 구조)
    private static Map<String, Object> courseSchema() {
        Map<String, Object> transport = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "mode", Map.of("type", "STRING"),
                        "durationMinutes", Map.of("type", "INTEGER"),
                        "note", Map.of("type", "STRING")
                ),
                "required", List.of("mode", "durationMinutes", "note")
        );

        Map<String, Object> visit = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "order", Map.of("type", "INTEGER"),
                        "name", Map.of("type", "STRING"),
                        "description", Map.of("type", "STRING"),
                        "latitude", Map.of("type", "NUMBER"),
                        "longitude", Map.of("type", "NUMBER"),
                        "transportToNext", transport
                ),
                // transportToNext 는 마지막 방문지에서 생략될 수 있어 required 에서 제외
                "required", List.of("order", "name", "description", "latitude", "longitude")
        );

        Map<String, Object> dayPlan = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "day", Map.of("type", "INTEGER"),
                        "visits", Map.of("type", "ARRAY", "items", visit)
                ),
                "required", List.of("day", "visits")
        );

        Map<String, Object> course = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "purpose", Map.of("type", "STRING"),
                        "title", Map.of("type", "STRING"),
                        "summary", Map.of("type", "STRING"),
                        "days", Map.of("type", "ARRAY", "items", dayPlan)
                ),
                "required", List.of("purpose", "title", "summary", "days")
        );

        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "regionName", Map.of("type", "STRING"),
                        "courses", Map.of("type", "ARRAY", "items", course)
                ),
                "required", List.of("regionName", "courses")
        );
    }

    // ──────────────────────────────────────────────
    // 공통 검증 / 기간 계산
    // ──────────────────────────────────────────────
    private static void validate(RecommendRequest request) {
        List<String> purpose = request.getPurpose();
        if (purpose == null || purpose.size() < MIN_PURPOSE || purpose.size() > MAX_PURPOSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "purpose 는 최소 " + MIN_PURPOSE + "개, 최대 " + MAX_PURPOSE + "개여야 합니다.");
        }
        if (request.getTransport() == null || !ALLOWED_TRANSPORT.contains(request.getTransport())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "transport 는 필수이며 " + ALLOWED_TRANSPORT + " 중 하나여야 합니다.");
        }
    }

    // 박/일 수 계산. 최대 7박 8일로 고정(상한 clamp).
    private record Trip(int nights, int days, String label) {}

    private static Trip resolveTrip(RecommendRequest request) {
        int nights;
        if (notBlank(request.getStartDate()) && notBlank(request.getEndDate())) {
            try {
                LocalDate start = LocalDate.parse(request.getStartDate());
                LocalDate end = LocalDate.parse(request.getEndDate());
                long between = ChronoUnit.DAYS.between(start, end);
                nights = (int) Math.max(0, between);
            } catch (DateTimeParseException e) {
                nights = nightsFromDurationLabel(request.getDuration());
            }
        } else {
            nights = nightsFromDurationLabel(request.getDuration());
        }

        nights = Math.min(nights, MAX_NIGHTS);  // 7박 상한
        int days = nights + 1;
        String label = nights == 0 ? "당일치기" : nights + "박" + days + "일";
        return new Trip(nights, days, label);
    }

    // "1박2일", "당일치기" 같은 문자열에서 박 수 추출. 알 수 없으면 2박 기본.
    private static int nightsFromDurationLabel(String duration) {
        if (duration == null || duration.isBlank()) return 2;
        if (duration.contains("당일")) return 0;
        for (int i = 0; i < duration.length(); i++) {
            if (duration.charAt(i) == '박' && i > 0 && Character.isDigit(duration.charAt(i - 1))) {
                return duration.charAt(i - 1) - '0';
            }
        }
        return 2;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullSafe(String s) {
        return (s == null || s.isBlank()) ? "(미정)" : s;
    }
}
