package com.optrip.server.service;

import com.optrip.server.client.gemini.GeminiClient;
import com.optrip.server.dto.RecommendRequest;
import com.optrip.server.dto.RecommendResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service  // 이 클래스가 비즈니스 로직 담당: Spring에 알려주는 표시
public class RecommendService {

    private final GeminiClient geminiClient;

    public RecommendService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    // 지역 데이터 구조
    private static final List<Map<String, Object>> REGIONS = List.of(

            // 1. 강원도 강릉
            Map.of(
                    "regionName", "강원도 강릉",
                    "description", "푸른 바다와 커피향이 어우러진 여유로운 도시",
                    "reason", "바다를 보며 힐링하고 감성 카페에서 여유를 즐길 수 있어요.",
                    "tags", List.of("#오션뷰", "#카페투어", "#힐링"),
                    "suitableBudget", List.of("10~20만"),
                    "suitableDuration", List.of("당일치기", "1박2일"),
                    "suitablePurpose", List.of("힐링", "카페투어", "자연/풍경", "감성/사진"),
                    "suitableCompanion", List.of("친구와", "애인과", "혼자")
            ),

            // 2. 전라북도 전주
            Map.of(
                    "regionName", "전라북도 전주",
                    "description", "한옥마을과 전통 먹거리가 살아있는 도시",
                    "reason", "전통문화와 맛집을 동시에 즐길 수 있는 가성비 여행지예요.",
                    "tags", List.of("#한옥마을", "#맛집", "#역사/문화"),
                    "suitableBudget", List.of("10만 이하", "10~20만"),
                    "suitableDuration", List.of("당일치기", "1박2일"),
                    "suitablePurpose", List.of("맛집", "역사/문화", "문화체험", "감성/사진"),
                    "suitableCompanion", List.of("친구와", "애인과", "혼자", "부모님과")
            ),

            // 3. 제주도
            Map.of(
                    "regionName", "제주도",
                    "description", "자연과 힐링이 가득한 한국의 섬",
                    "reason", "웅장한 자연 속에서 힐링과 액티비티를 모두 즐길 수 있어요.",
                    "tags", List.of("#자연/풍경", "#힐링", "#액티비티"),
                    "suitableBudget", List.of("20만 이상"),
                    "suitableDuration", List.of("2박3일", "3박4일", "4박5일"),
                    "suitablePurpose", List.of("힐링", "액티비티", "자연/풍경", "감성/사진"),
                    "suitableCompanion", List.of("애인과", "친구와", "부모님과", "아이와")
            ),

            // 4. 경상북도 경주
            Map.of(
                    "regionName", "경상북도 경주",
                    "description", "천년 역사가 살아숨쉬는 고도",
                    "reason", "한국 역사와 문화를 가장 깊이 느낄 수 있는 도시예요.",
                    "tags", List.of("#역사/문화", "#문화체험", "#감성/사진"),
                    "suitableBudget", List.of("10만 이하", "10~20만"),
                    "suitableDuration", List.of("당일치기", "1박2일", "2박3일"),
                    "suitablePurpose", List.of("역사/문화", "문화체험", "감성/사진", "자연/풍경"),
                    "suitableCompanion", List.of("부모님과", "친구와", "애인과", "혼자")
            ),

            // 5. 전라남도 목포
            Map.of(
                    "regionName", "전라남도 목포",
                    "description", "남도의 맛과 항구 낭만이 가득한 도시",
                    "reason", "신선한 해산물과 남도 맛집을 저렴하게 즐길 수 있어요.",
                    "tags", List.of("#맛집", "#역사/문화", "#감성/사진"),
                    "suitableBudget", List.of("10만 이하", "10~20만"),
                    "suitableDuration", List.of("당일치기", "1박2일"),
                    "suitablePurpose", List.of("맛집", "역사/문화", "자연/풍경", "문화체험"),
                    "suitableCompanion", List.of("부모님과", "친구와", "혼자", "아이와")
            ),

            // 6. 강원도 속초
            Map.of(
                    "regionName", "강원도 속초",
                    "description", "설악산과 동해바다가 공존하는 액티비티 도시",
                    "reason", "바다와 산을 동시에 즐길 수 있는 자연 액티비티 천국이에요.",
                    "tags", List.of("#액티비티", "#자연/풍경", "#오션뷰"),
                    "suitableBudget", List.of("10~20만", "20만 이상"),
                    "suitableDuration", List.of("1박2일", "2박3일"),
                    "suitablePurpose", List.of("액티비티", "자연/풍경", "힐링", "감성/사진"),
                    "suitableCompanion", List.of("친구와", "애인과", "부모님과", "혼자")
            ),

            // 7. 충청남도 공주
            Map.of(
                    "regionName", "충청남도 공주",
                    "description", "백제 문화의 숨결이 느껴지는 고즈넉한 도시",
                    "reason", "붐비지 않고 조용하게 역사와 자연을 즐길 수 있어요.",
                    "tags", List.of("#역사/문화", "#자연/풍경", "#힐링"),
                    "suitableBudget", List.of("10만 이하"),
                    "suitableDuration", List.of("당일치기", "1박2일"),
                    "suitablePurpose", List.of("역사/문화", "자연/풍경", "힐링", "문화체험"),
                    "suitableCompanion", List.of("혼자", "부모님과", "친구와")
            ),

            // 8. 부산
            Map.of(
                    "regionName", "부산",
                    "description", "바다와 감성, 맛집이 모두 있는 대도시",
                    "reason", "다양한 즐길거리가 있어 누구와 가도 만족스러운 여행지예요.",
                    "tags", List.of("#맛집", "#감성/사진", "#액티비티"),
                    "suitableBudget", List.of("10~20만", "20만 이상"),
                    "suitableDuration", List.of("1박2일", "2박3일"),
                    "suitablePurpose", List.of("맛집", "감성/사진", "액티비티", "카페투어", "문화체험"),
                    "suitableCompanion", List.of("친구와", "애인과", "아이와", "부모님과")
            )
    );


    public RecommendResponse recommendWithAI(RecommendRequest request) {
        String durationInfo;
        if (request.getStartDate() != null && request.getEndDate() != null) {
            durationInfo = request.getStartDate() + " ~ " + request.getEndDate();
        } else {
            durationInfo = request.getDuration();
        }

        // Gemini한테 보낼 프롬프트
        String prompt = String.format("""
                당신은 한국 여행지 추천 전문가입니다.
                아래 조건에 맞는 한국 여행지 1곳을 추천해주세요.
                
                예산: %s
                기간: %s
                동행: %s
                추구하는 여행 스타일: %s
                
                [중요 규칙]
                - 너무 뻔한 곳보다 개성 있는 곳을 추천해주세요.
                - description은 반드시 20자 이내 한 줄로만 작성하세요.
                - reason은 반드시 2문장 이내로 작성하세요.
                - tags는 아래 목록에서만 최대 3개 선택하세요:
                  #힐링 #맛집 #감성/사진 #자연/풍경 #역사/문화
                  #액티비티 #문화체험 #카페투어 
                """,
                request.getBudget(),
                durationInfo,
                request.getCompanion(),
                request.getPurpose()
        );

        // Gemini 응답 형식 정의
        // 이 형식으로만 답하라고 강제하는 것
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

        // GeminiClient 호출
        // RecommendResponse 형식으로 바로 받아옴
        return geminiClient.generateStructured(
                prompt,
                responseSchema,
                RecommendResponse.class
        );
    }

    public RecommendResponse recommend(RecommendRequest request) {

        String durationInfo;
        if (request.getStartDate() != null && request.getEndDate() != null) {
            // 날짜 차이 계산
            java.time.LocalDate start = java.time.LocalDate.parse(request.getStartDate());
            java.time.LocalDate end = java.time.LocalDate.parse(request.getEndDate());
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);

            // 날짜 차이를 duration 형식으로 변환
            if (days == 0) {
                durationInfo = "당일치기";
            } else if (days == 1) {
                durationInfo = "1박2일";
            } else if (days == 2) {
                durationInfo = "2박3일";
            } else if (days == 3) {
                durationInfo = "3박4일";
            } else if (days == 4) {
                durationInfo = "4박5일";
            } else {
                durationInfo = "5박6일";
            }
        } else {
            durationInfo = request.getDuration();
        }

        Map<String, Object> best = null;
        int bestScore = -1;

        for (Map<String, Object> region : REGIONS) {

            int score = 0;

            // 1순위: 예산 (40점) - 안 맞으면 바로 탈락
            List<String> suitableBudget =
                    (List<String>) region.get("suitableBudget");
            if (!suitableBudget.contains(request.getBudget())) {
                continue;  // 예산 안 맞으면 이 지역 건너뜀
            }
            score += 40;

            // 2순위: 기간 (30점)
            List<String> suitableDuration =
                    (List<String>) region.get("suitableDuration");
            if (suitableDuration.contains(durationInfo)) {
                score += 30;
            }

            // 3순위: 목적 (10점) - purpose가 겹칠 때마다 10점
            List<String> suitablePurpose =
                    (List<String>) region.get("suitablePurpose");
            for (String p : request.getPurpose()) {
                if (suitablePurpose.contains(p)) {
                    score += 10;
                }
            }

            // 4순위: 동행 (10점)
            List<String> suitableCompanion =
                    (List<String>) region.get("suitableCompanion");
            if (suitableCompanion.contains(request.getCompanion())) {
                score += 10;
            }

            if (score > bestScore) {
                bestScore = score;
                best = region;
            }
        }

        // 매칭 결과 없으면 첫 번째 지역 기본값으로 반환
        if (best == null) {
            best = REGIONS.get(0);
        }

        return new RecommendResponse(
                (String) best.get("regionName"),
                (String) best.get("description"),
                (String) best.get("reason"),
                (List<String>) best.get("tags")
        );
    }
}