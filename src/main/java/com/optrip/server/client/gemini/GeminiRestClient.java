package com.optrip.server.client.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optrip.server.client.gemini.GeminiPayloads.Candidate;
import com.optrip.server.client.gemini.GeminiPayloads.Content;
import com.optrip.server.client.gemini.GeminiPayloads.GenerateContentRequest;
import com.optrip.server.client.gemini.GeminiPayloads.GenerateContentResponse;
import com.optrip.server.client.gemini.GeminiPayloads.GenerationConfig;
import com.optrip.server.client.gemini.GeminiPayloads.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GeminiRestClient implements GeminiClient {

    private static final String GENERATE_CONTENT_PATH = "/v1beta/models/%s:generateContent";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiRestClient(RestClient geminiHttpClient, ObjectMapper objectMapper, GeminiProperties properties) {
        this.restClient = geminiHttpClient;
        this.objectMapper = objectMapper;
        this.model = properties.model();
    }

    @Override
    public <T> T generateStructured(String prompt, Map<String, Object> responseSchema, Class<T> responseType) {
        GenerateContentRequest body = new GenerateContentRequest(
                List.of(new Content("user", List.of(new Part(prompt)))),
                new GenerationConfig("application/json", responseSchema)
        );

        GenerateContentResponse response = restClient.post()
                .uri(GENERATE_CONTENT_PATH.formatted(model))
                .body(body)
                .retrieve()
                .body(GenerateContentResponse.class);

        String json = extractText(response);
        try {
            return objectMapper.readValue(json, responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to parse Gemini response as " + responseType.getSimpleName() + ": " + json, e);
        }
    }

    // responseMimeType=application/json 이면 candidates[0].content.parts[0].text 가 JSON 문자열
    private static String extractText(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates");
        }
        Candidate candidate = response.candidates().get(0);
        if (candidate.content() == null
                || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            throw new IllegalStateException("Gemini candidate has no parts");
        }
        return candidate.content().parts().get(0).text();
    }
}
