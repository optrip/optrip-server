package com.optrip.server.recommend;

import com.optrip.server.client.gemini.GeminiClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class InterpretService {

    public record InterpretRequest(String text, List<String> dates, String companion, List<String> destinations) {
    }

    public record PhraseMapping(String phrase, List<String> purposes) {
    }

    public record InterpretResult(List<String> purposes, String summary, List<PhraseMapping> mappings) {
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

                사용자 문장: %s
                """.formatted(String.join(", ", Purposes.ACTIVE), text);

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
}
