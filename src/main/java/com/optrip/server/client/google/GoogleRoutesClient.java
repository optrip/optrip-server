package com.optrip.server.client.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.optrip.server.dto.RouteLegRequest;
import com.optrip.server.dto.RouteLegResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@EnableConfigurationProperties(GoogleRoutesProperties.class)
public class GoogleRoutesClient {

    private static final String FIELD_MASK = String.join(",",
            "routes.duration",
            "routes.distanceMeters",
            "routes.polyline.encodedPolyline",
            "routes.legs.steps.travelMode",
            "routes.legs.steps.transitDetails"
    );

    private final GoogleRoutesProperties properties;
    private final RestClient restClient;

    public GoogleRoutesClient(GoogleRoutesProperties properties) {
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
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("GOOGLE_ROUTES_API_KEY가 설정되지 않았습니다.");
        }

        String travelMode = normalizeTravelMode(request.travelMode());
        Map<String, Object> body = Map.of(
                "origin", waypoint(request.originLatitude(), request.originLongitude()),
                "destination", waypoint(request.destinationLatitude(), request.destinationLongitude()),
                "travelMode", travelMode,
                "languageCode", "ko",
                "units", "METRIC"
        );

        JsonNode root = restClient.post()
                .uri("/directions/v2:computeRoutes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Goog-Api-Key", properties.apiKey())
                .header("X-Goog-FieldMask", FIELD_MASK)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode route = root == null ? null : root.path("routes").path(0);
        if (route == null || route.isMissingNode()) {
            throw new IllegalStateException("Google에서 이용 가능한 경로를 찾지 못했습니다.");
        }

        return new RouteLegResponse(
                travelMode,
                parseDurationSeconds(route.path("duration").asText("0s")),
                route.path("distanceMeters").asInt(0),
                route.path("polyline").path("encodedPolyline").asText(""),
                readTransitSteps(route)
        );
    }

    private Map<String, Object> waypoint(double latitude, double longitude) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", latitude,
                "longitude", longitude
        )));
    }

    private String normalizeTravelMode(String travelMode) {
        String normalized = travelMode == null ? "" : travelMode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("TRANSIT") && !normalized.equals("DRIVE")) {
            throw new IllegalArgumentException("travelMode은 TRANSIT 또는 DRIVE여야 합니다.");
        }
        return normalized;
    }

    private long parseDurationSeconds(String duration) {
        String number = duration.endsWith("s") ? duration.substring(0, duration.length() - 1) : duration;
        try {
            return Math.round(Double.parseDouble(number));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private List<RouteLegResponse.TransitStep> readTransitSteps(JsonNode route) {
        List<RouteLegResponse.TransitStep> steps = new ArrayList<>();
        for (JsonNode leg : route.path("legs")) {
            for (JsonNode step : leg.path("steps")) {
                JsonNode details = step.path("transitDetails");
                if (details.isMissingNode()) {
                    continue;
                }
                JsonNode line = details.path("transitLine");
                String lineName = line.path("nameShort").asText(line.path("name").asText(""));
                String vehicleType = line.path("vehicle").path("type").asText("TRANSIT");
                int stopCount = details.path("stopCount").asInt(0);
                steps.add(new RouteLegResponse.TransitStep(vehicleType, lineName, stopCount));
            }
        }
        return List.copyOf(steps);
    }
}
