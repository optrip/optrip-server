package com.optrip.server.recommend;

import com.optrip.server.client.gemini.GeminiClient;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class InterpretService {

    public record InterpretRequest(
            @Schema(description = "사용자가 입력한 자연어 문장", example = "한식 맛집 찾아다니고 예쁜 카페에서 쉬고 싶어") String text,
            @Schema(description = "여행 날짜 목록 (선택). 추구미 판단에만 참고하고 summary에는 반영하지 않음") List<String> dates,
            @Schema(description = "동행자 (선택). 추구미 판단에만 참고하고 summary에는 반영하지 않음", example = "애인과") String companion,
            @Schema(description = "입력한 목적지 목록 (선택). 추구미 판단에만 참고하고 summary에는 반영하지 않음") List<String> destinations) {
    }

    public record PhraseMapping(
            @Schema(description = "사용자가 실제로 쓴 표현") String phrase,
            @Schema(description = "그 표현이 해석된 추구미") List<String> purposes) {
    }

    public record InterpretResult(
            @Schema(description = "해석된 추구미 1~3개. 해석 실패 시 빈 배열") List<String> purposes,
            @Schema(description = "이해 확인 화면에 보여줄 한 문장 요약") String summary,
            @Schema(description = "표현별 해석 근거 (최대 3개)") List<PhraseMapping> mappings) {
    }

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "purposes", Map.of("type", "ARRAY", "items", Map.of("type", "STRING", "enum", Purposes.ACTIVE)),
                    "summary", Map.of("type", "STRING"),
                    "mappings", Map.of("type", "ARRAY", "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "phrase", Map.of("type", "STRING"),
                                    "purposes", Map.of("type", "ARRAY", "items", Map.of("type", "STRING", "enum", Purposes.ACTIVE))
                            ),
                            "required", List.of("phrase", "purposes")
                    ))
            ),
            "required", List.of("purposes", "summary", "mappings")
    );

    private final GeminiClient geminiClient;

    public InterpretService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    public InterpretResult interpret(InterpretRequest request) {
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isBlank()) {
            return new InterpretResult(List.of(), "", List.of());
        }

        String prompt = """
                당신은 여행 앱의 자연어 해석기다. 사용자의 문장을 아래 여행 취향(추구미) 목록 안에서만 해석한다.

                추구미 목록: %s

                규칙:
                - purposes: 사용자의 문장과 가장 맞는 추구미를 1~3개 고른다. 목록에 없는 값은 절대 만들지 않는다.
                - summary: 사용자가 말한 내용을 한 문장으로 되풀이한다. 사용자가 말하지 않은 장소, 지역, 활동을 추가하지 않는다.
                  선택한 purposes와 어긋나는 내용을 넣지 않는다. 존댓말 없이 "~하는 여행" 형태로 끝낸다.
                - mappings: 사용자 문장에서 실제로 쓴 표현을 짧게 인용하고, 그 표현이 어떤 추구미로 해석됐는지 연결한다.
                  purposes에 넣은 추구미만 사용한다. 표현당 하나씩, 최대 3개.
                %s
                사용자 문장: %s
                """.formatted(String.join(", ", Purposes.ACTIVE), contextBlock(request), text);

        InterpretResult raw = geminiClient.generateStructured(prompt, SCHEMA, InterpretResult.class);

        List<String> purposes = raw.purposes() == null ? List.of() : raw.purposes().stream()
                .filter(Purposes.ACTIVE::contains)
                .distinct()
                .limit(3)
                .toList();
        String summary = raw.summary() == null ? "" : raw.summary().trim();
        List<PhraseMapping> mappings = raw.mappings() == null ? List.of() : raw.mappings().stream()
                .filter(m -> m.phrase() != null && !m.phrase().isBlank())
                .map(m -> new PhraseMapping(m.phrase().trim(),
                        m.purposes() == null ? List.<String>of()
                                : m.purposes().stream().filter(purposes::contains).distinct().toList()))
                .filter(m -> !m.purposes().isEmpty())
                .limit(3)
                .toList();
        return new InterpretResult(purposes, summary, mappings);
    }

    private static String contextBlock(InterpretRequest request) {
        List<String> lines = new ArrayList<>();
        if (request.companion() != null && !request.companion().isBlank()) {
            lines.add("- 동행: " + request.companion().trim());
        }
        if (request.dates() != null && !request.dates().isEmpty()) {
            lines.add("- 여행 날짜: " + String.join(" ~ ", request.dates()));
        }
        if (request.destinations() != null && !request.destinations().isEmpty()) {
            lines.add("- 가고 싶은 곳: " + String.join(", ", request.destinations()));
        }
        if (lines.isEmpty()) {
            return "";
        }
        return """

                참고 조건 (사용자가 앞 화면에서 고른 값이다. purposes를 고를 때만 참고하고, summary와 mappings에는 넣지 않는다):
                %s
                """.formatted(String.join("\n", lines));
    }
}
