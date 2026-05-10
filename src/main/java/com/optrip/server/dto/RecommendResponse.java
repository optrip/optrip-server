package com.optrip.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor  // 모든 필드를 받는 생성자를 자동으로 만들어줌
// 나중에 Service에서 new RecommendResponse(...) 할 때 씀 (생성자 이용)

public class RecommendResponse {
    private String regionName;    // 지역명: "제주도"
    private String description;   // 한 줄 설명: "자연과 힐링의 섬"
    private String reason;        // 추천 이유: "예산과 일정에 딱 맞는 여행지예요"
    private List<String> tags;    // 해시태그 -> qhrtn
}
