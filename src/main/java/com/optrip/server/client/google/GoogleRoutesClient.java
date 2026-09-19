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
import java.util.regex.Pattern;

@Component
@EnableConfigurationProperties(GoogleRoutesProperties.class)
public class GoogleRoutesClient {

    private static final Pattern LEADING_BUS_NUMBER = Pattern.compile("^\\s*(\\d+(?:-\\d+)?[A-Za-z]?)");

    private static final String FIELD_MASK = String.join(",",
            "routes.duration",
            "routes.distanceMeters",
            "routes.polyline.encodedPolyline",
            "routes.legs.steps.travelMode",
            "routes.legs.steps.staticDuration",
            "routes.legs.steps.distanceMeters",
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

        List<RouteLegResponse.RouteStep> steps = readRouteSteps(route);
        List<RouteLegResponse.TransitStep> transitSteps = steps.stream()
                .filter(step -> "TRANSIT".equals(step.travelMode()))
                .map(step -> new RouteLegResponse.TransitStep(
                        step.vehicleType(), step.lineName(), step.stopCount()))
                .toList();
        return new RouteLegResponse(
                travelMode,
                parseDurationSeconds(route.path("duration").asText("0s")),
                route.path("distanceMeters").asInt(0),
                route.path("polyline").path("encodedPolyline").asText(""),
                transitSteps,
                steps
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

    private List<RouteLegResponse.RouteStep> readRouteSteps(JsonNode route) {
        List<RouteLegResponse.RouteStep> steps = new ArrayList<>();
        for (JsonNode leg : route.path("legs")) {
            for (JsonNode step : leg.path("steps")) {
                String travelMode = step.path("travelMode").asText("");
                JsonNode details = step.path("transitDetails");
                JsonNode line = details.path("transitLine");
                String vehicleType = line.path("vehicle").path("type").asText("TRANSIT");
                String lineName = compactLineName(line, vehicleType);
                int stopCount = details.path("stopCount").asInt(0);
                steps.add(new RouteLegResponse.RouteStep(
                        travelMode,
                        parseDurationSeconds(step.path("staticDuration").asText("0s")),
                        step.path("distanceMeters").asInt(0),
                        details.isMissingNode() ? "" : vehicleType,
                        details.isMissingNode() ? "" : lineName,
                        details.path("stopDetails").path("departureStop").path("name").asText(""),
                        details.path("stopDetails").path("arrivalStop").path("name").asText(""),
                        stopCount));
            }
        }
        return List.copyOf(steps);
    }

    private String compactLineName(JsonNode line, String vehicleType) {
        String shortName = line.path("nameShort").asText("").trim();
        if (!shortName.isBlank()) {
            return compactCandidate(shortName, vehicleType);
        }
        if (!vehicleType.contains("BUS")) {
            return "";
        }
        return compactCandidate(line.path("name").asText(""), vehicleType);
    }

    private String compactCandidate(String candidate, String vehicleType) {
        if (vehicleType.contains("BUS")) {
            var matcher = LEADING_BUS_NUMBER.matcher(candidate);
            if (matcher.find()) return matcher.group(1);
        }
        int parenthesis = candidate.indexOf('(');
        return (parenthesis >= 0 ? candidate.substring(0, parenthesis) : candidate).trim();
    }
}
