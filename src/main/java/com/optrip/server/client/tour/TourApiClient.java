package com.optrip.server.client.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 한국관광공사 TourAPI 4.0 호출 클라이언트.
// KorService2(국문 관광정보), KorWithService2(무장애) 등 B551011 하위 서비스를 공용으로 다룬다.
@Component
@EnableConfigurationProperties(TourApiProperties.class)
public class TourApiClient {

    public static final String KOR_SERVICE = "KorService2";
    public static final String WITH_SERVICE = "KorWithService2";

    private final TourApiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TourApiClient(TourApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    // 호출 결과. 게이트웨이 오류 시 XML 이 내려올 수 있어 resultCode/raw 를 함께 보존한다.
    public record TourApiResult(
            List<JsonNode> items,
            int totalCount,
            String resultCode,
            String resultMsg,
            int httpStatus,
            long durationMs
    ) {
        public boolean ok() {
            return "0000".equals(resultCode);
        }
    }

    // service: KorService2 등 / operation: areaBasedList2 등 / params: 오퍼레이션별 파라미터
    public TourApiResult call(String service, String operation, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.baseUrl())
                .pathSegment(service, operation)
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "optrip")
                .queryParam("_type", "json");
        params.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                builder.queryParam(k, v);
            }
        });
        URI uri = builder.build(true).toUri();

        long start = System.currentTimeMillis();
        int httpStatus = 0;
        String body = null;
        try {
            var response = restClient.get().uri(uri).retrieve().toEntity(String.class);
            httpStatus = response.getStatusCode().value();
            body = response.getBody();
        } catch (Exception e) {
            return new TourApiResult(List.of(), 0, "HTTP_ERROR", e.getMessage(), httpStatus,
                    System.currentTimeMillis() - start);
        }
        long duration = System.currentTimeMillis() - start;

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode header = root.path("response").path("header");
            JsonNode bodyNode = root.path("response").path("body");
            String resultCode = header.path("resultCode").asText("PARSE_ERROR");
            String resultMsg = header.path("resultMsg").asText("");
            int totalCount = bodyNode.path("totalCount").asInt(0);

            // items 는 없으면 "" / 1건이면 객체 / 여러 건이면 배열로 내려온다 → 리스트로 정규화
            List<JsonNode> items = new ArrayList<>();
            JsonNode itemNode = bodyNode.path("items").path("item");
            if (itemNode.isArray()) {
                itemNode.forEach(items::add);
            } else if (itemNode.isObject()) {
                items.add(itemNode);
            }
            return new TourApiResult(items, totalCount, resultCode, resultMsg, httpStatus, duration);
        } catch (Exception e) {
            // 한도 초과 등 게이트웨이 오류는 XML(OpenAPI_ServiceResponse)로 내려온다
            String snippet = body == null ? "" : body.substring(0, Math.min(body.length(), 300));
            return new TourApiResult(List.of(), 0, "PARSE_ERROR", snippet, httpStatus, duration);
        }
    }

    // 일 한도 보호용 호출 간 대기
    public void pause() {
        try {
            Thread.sleep(properties.callPauseMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
