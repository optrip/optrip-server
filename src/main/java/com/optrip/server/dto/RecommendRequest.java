package com.optrip.server.dto;

import lombok.Getter;
import java.util.List;

@Getter
public class RecommendRequest {

    private String budget;      // 예산: "10만원 이하", "10만원 ~ 50만원" 등
    private String duration;    // 기간: "당일치기", "1박2일" 등 (최대 7박8일)
    private String startDate;   // 구체적 일정 있을 때 시작일: "2026-05-10"
    private String endDate;     // 구체적 일정 있을 때 종료일: "2026-05-12"
    private String companion;   // 동행: "혼자", "친구와" 등
    private List<String> purpose;        // 추구하는 여행 -> 최소 1개, 최대 3개
    private String transport;            // 이동수단(필수): "자동차" | "대중교통"
    private List<String> excludeRegions; // 지역 다시 추천 시 제외할 지역명 (nullable)
    private String regionName;           // 코스 생성 시 선택된 지역명 (/courses 에서 사용)
}
