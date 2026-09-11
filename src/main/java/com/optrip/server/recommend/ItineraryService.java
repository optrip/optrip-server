package com.optrip.server.recommend;

import com.optrip.server.client.google.GoogleRoutesClient;
import com.optrip.server.dto.RouteLegRequest;
import com.optrip.server.dto.RouteLegResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ItineraryService {

    public record ItineraryRequest(List<String> placeIds, String transport, Integer days) {
    }

    private static final Logger log = LoggerFactory.getLogger(ItineraryService.class);
    private static final double WALK_THRESHOLD_KM = 0.8;

    private final JdbcTemplate jdbc;
    private final GoogleRoutesClient routesClient;

    public ItineraryService(JdbcTemplate jdbc, GoogleRoutesClient routesClient) {
        this.jdbc = jdbc;
        this.routesClient = routesClient;
    }

    public Map<String, Object> build(ItineraryRequest request) {
        if (request.placeIds() == null || request.placeIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "placeIds가 필요합니다");
        }
        String transport = "자동차".equals(request.transport()) ? "자동차" : "대중교통";
        int days = request.days() == null ? 1 : Math.clamp(request.days(), 1, 4);

        List<Stop> stops = orderByNearestNeighbor(resolve(request.placeIds()));
        int perDay = (int) Math.ceil((double) stops.size() / days);

        List<Map<String, Object>> dayList = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            int from = d * perDay;
            int to = Math.min(from + perDay, stops.size());
            if (from >= to) break;
            List<Map<String, Object>> items = new ArrayList<>();
            for (int i = from; i < to; i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("place", stops.get(i).toResponse());
                if (i < to - 1) {
                    item.put("legToNext", leg(stops.get(i), stops.get(i + 1), transport));
                }
                items.add(item);
            }
            dayList.add(Map.of("day", d + 1, "items", items));
        }
        return Map.of("transport", transport, "days", dayList);
    }

    private Map<String, Object> leg(Stop from, Stop to, String transport) {
        double km = RegionRecommendService.haversineKm(from.mapy, from.mapx, to.mapy, to.mapx);
        if (km < WALK_THRESHOLD_KM) {
            int minutes = Math.max(1, (int) Math.round(km / 4.0 * 60));
            return legResponse("도보", "도보 · 약 %d분 소요".formatted(minutes), minutes, (int) (km * 1000), null);
        }
        String travelMode = "자동차".equals(transport) ? "DRIVE" : "TRANSIT";
        try {
            RouteLegResponse route = routesClient.computeLeg(new RouteLegRequest(from.mapy, from.mapx, to.mapy, to.mapx, travelMode));
            int minutes = Math.max(1, (int) Math.round(route.durationSeconds() / 60.0));
            return legResponse(transport, transitSummary(transport, route, minutes), minutes,
                    route.distanceMeters(), route.encodedPolyline());
        } catch (Exception e) {
            log.warn("경로 계산 실패, 추정치 사용: {}", e.getMessage());
            double speedKmh = "자동차".equals(transport) ? 40 : 20;
            int minutes = Math.max(5, (int) Math.round(km / speedKmh * 60) + ("자동차".equals(transport) ? 3 : 8));
            return legResponse(transport, "%s · 약 %d분 소요".formatted(transport, minutes), minutes, (int) (km * 1000), null);
        }
    }

    private static String transitSummary(String transport, RouteLegResponse route, int minutes) {
        if ("자동차".equals(transport)) {
            return "자동차 · 약 %d분 소요".formatted(minutes);
        }
        List<RouteLegResponse.TransitStep> steps = route.transitSteps();
        if (steps == null || steps.isEmpty()) {
            return "대중교통 · 약 %d분 소요".formatted(minutes);
        }
        String vehicle = steps.get(0).vehicleType().contains("SUBWAY") ? "지하철" : "버스";
        String line = steps.get(0).lineName();
        String head = vehicle + (line == null || line.isBlank() ? "" : " " + line);
        int transfers = steps.size() - 1;
        String transfer = transfers > 0 ? " · %d회 환승".formatted(transfers) : "";
        return "%s 이용%s · 약 %d분 소요".formatted(head, transfer, minutes);
    }

    private static Map<String, Object> legResponse(String mode, String summary, int minutes, int meters, String polyline) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", mode);
        m.put("summary", summary);
        m.put("durationMinutes", minutes);
        m.put("distanceMeters", meters);
        if (polyline != null && !polyline.isBlank()) {
            m.put("encodedPolyline", polyline);
        }
        return m;
    }

    private List<Stop> resolve(List<String> placeIds) {
        List<Stop> stops = new ArrayList<>();
        for (String id : placeIds) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select content_id, title, addr1, mapx, mapy, first_image from place where content_id = ?", id);
            if (rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "장소 없음: " + id);
            }
            Map<String, Object> r = rows.get(0);
            if (r.get("mapx") == null || r.get("mapy") == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "좌표 없는 장소: " + id);
            }
            stops.add(new Stop((String) r.get("content_id"), (String) r.get("title"), (String) r.get("addr1"),
                    ((Number) r.get("mapx")).doubleValue(), ((Number) r.get("mapy")).doubleValue(),
                    (String) r.get("first_image")));
        }
        return stops;
    }

    private static List<Stop> orderByNearestNeighbor(List<Stop> stops) {
        if (stops.size() <= 2) return stops;
        List<Stop> remaining = new ArrayList<>(stops);
        List<Stop> ordered = new ArrayList<>();
        ordered.add(remaining.remove(0));
        while (!remaining.isEmpty()) {
            Stop last = ordered.get(ordered.size() - 1);
            Stop nearest = remaining.stream()
                    .min((a, b) -> Double.compare(
                            RegionRecommendService.haversineKm(last.mapy, last.mapx, a.mapy, a.mapx),
                            RegionRecommendService.haversineKm(last.mapy, last.mapx, b.mapy, b.mapx)))
                    .orElseThrow();
            remaining.remove(nearest);
            ordered.add(nearest);
        }
        return ordered;
    }

    private record Stop(String contentId, String title, String addr1, double mapx, double mapy, String imageUrl) {
        Map<String, Object> toResponse() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contentId", contentId);
            m.put("title", title);
            m.put("addr1", addr1);
            m.put("mapx", mapx);
            m.put("mapy", mapy);
            m.put("imageUrl", imageUrl);
            return m;
        }
    }
}
