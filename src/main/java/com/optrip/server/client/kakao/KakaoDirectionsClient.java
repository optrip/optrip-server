package com.optrip.server.client.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.optrip.server.dto.RouteLegRequest;
import com.optrip.server.dto.RouteLegResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
@EnableConfigurationProperties(KakaoDirectionsProperties.class)
public class KakaoDirectionsClient {

    private final KakaoDirectionsProperties properties;
    private final RestClient restClient;

    public KakaoDirectionsClient(KakaoDirectionsProperties properties) {
        this.properties = properties;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    public RouteLegResponse computeLeg(RouteLegRequest request) {
        if (properties.restApiKey() == null || properties.restApiKey().isBlank()) {
            throw new IllegalStateException("KAKAO_REST_API_KEY가 설정되지 않았습니다.");
        }

        JsonNode root = restClient.get()
                .uri(uri -> uri.path("/v1/directions")
                        .queryParam("origin", coordinate(request.originLongitude(), request.originLatitude()))
                        .queryParam("destination", coordinate(request.destinationLongitude(), request.destinationLatitude()))
                        .queryParam("priority", "RECOMMEND")
                        .queryParam("summary", false)
                        .build())
                .header("Authorization", "KakaoAK " + properties.restApiKey())
                .retrieve()
                .body(JsonNode.class);

        JsonNode route = root == null ? null : root.path("routes").path(0);
        if (route == null || route.isMissingNode() || route.path("result_code").asInt(-1) != 0) {
            String message = route == null ? "" : route.path("result_msg").asText("");
            throw new IllegalStateException(message.isBlank()
                    ? "카카오에서 자동차 경로를 찾지 못했습니다."
                    : "카카오 자동차 경로 조회 실패: " + message);
        }

        JsonNode summary = route.path("summary");
        return new RouteLegResponse(
                "DRIVE",
                summary.path("duration").asLong(0),
                summary.path("distance").asInt(0),
                encodePolyline(readPoints(route.path("sections"))),
                List.of()
        );
    }

    private static String coordinate(double longitude, double latitude) {
        return Double.toString(longitude) + "," + Double.toString(latitude);
    }

    static List<Point> readPoints(JsonNode sections) {
        List<Point> points = new ArrayList<>();
        for (JsonNode section : sections) {
            for (JsonNode road : section.path("roads")) {
                JsonNode vertices = road.path("vertexes");
                for (int i = 0; i + 1 < vertices.size(); i += 2) {
                    Point point = new Point(vertices.get(i + 1).asDouble(), vertices.get(i).asDouble());
                    if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) {
                        points.add(point);
                    }
                }
            }
        }
        return points;
    }

    static String encodePolyline(List<Point> points) {
        StringBuilder encoded = new StringBuilder();
        long previousLatitude = 0;
        long previousLongitude = 0;
        for (Point point : points) {
            long latitude = Math.round(point.latitude() * 100_000);
            long longitude = Math.round(point.longitude() * 100_000);
            appendValue(encoded, latitude - previousLatitude);
            appendValue(encoded, longitude - previousLongitude);
            previousLatitude = latitude;
            previousLongitude = longitude;
        }
        return encoded.toString();
    }

    private static void appendValue(StringBuilder encoded, long value) {
        long shifted = value < 0 ? ~(value << 1) : value << 1;
        while (shifted >= 0x20) {
            encoded.append((char) ((0x20 | (shifted & 0x1f)) + 63));
            shifted >>= 5;
        }
        encoded.append((char) (shifted + 63));
    }

    record Point(double latitude, double longitude) {
    }
}
