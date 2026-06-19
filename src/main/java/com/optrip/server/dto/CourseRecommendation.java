package com.optrip.server.dto;

import java.util.List;

// 코스 추천(Image #2, #3)용 응답 모음.
// 한 지역에 대해 선택한 purpose 개수만큼의 코스를 한 번에 생성하며,
// 각 코스는 일자별 방문지 + 방문지 사이의 이동수단/소요시간을 포함한다.
// 방문지에는 위경도가 포함되어 클라이언트가 Kakao Map 위에 마킹할 수 있다.
public final class CourseRecommendation {

    private CourseRecommendation() {}

    // /courses 응답 루트
    public record CourseList(
            String regionName,
            List<Course> courses
    ) {}

    // purpose 1개당 코스 1개
    public record Course(
            String purpose,        // "역사/문화"
            String title,          // "경주 역사/문화 여행 경로"
            String summary,        // "동궁과 월지 · 대릉원 · 경주 월드 · 첨성대 등"
            List<DayPlan> days
    ) {}

    public record DayPlan(
            int day,               // 1, 2, 3 ...
            List<Visit> visits
    ) {}

    public record Visit(
            int order,             // 해당 일차 내 방문 순서 (1부터)
            String name,           // "동궁과 월지"
            String description,    // 방문지 한 줄 소개
            double latitude,       // 위도 (Kakao Map 마킹용)
            double longitude,      // 경도 (Kakao Map 마킹용)
            Transport transportToNext  // 다음 방문지까지 이동수단. 일차의 마지막 방문지는 null
    ) {}

    public record Transport(
            String mode,           // "도보" | "지하철" | "버스" | "자동차" | "택시" 등
            int durationMinutes,   // 예상 소요 시간(분)
            String note            // "1회 환승" 등 부가 설명. 없으면 빈 문자열
    ) {}
}
