package com.optrip.server.dto;

import lombok.Getter;
import java.util.List;

@Getter
public class RecommendRequest {

    private String budget;      // 예산: "10만 이하", "10~20만", "20만 이상"
    private String duration;    // 기간: "당일치기", "1박2일", "2박3일" 등
    private String startDate;     // 구체적 일정 있을 때 시작일: "2026-05-10"
    private String endDate;       // 구체적 일정 있을 때 종료일: "2026-05-12"
    private String companion;
    private List<String> purpose; // 추구하는 여행 -> 복수 선택 가능
}
