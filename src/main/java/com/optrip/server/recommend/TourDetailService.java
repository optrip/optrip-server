package com.optrip.server.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optrip.server.client.tour.TourApiClient;
import com.optrip.server.client.tour.TourApiClient.TourApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TourDetailService {

    private static final Logger log = LoggerFactory.getLogger(TourDetailService.class);

    private final JdbcTemplate jdbc;
    private final TourApiClient tourApiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TourDetailService(JdbcTemplate jdbc, TourApiClient tourApiClient) {
        this.jdbc = jdbc;
        this.tourApiClient = tourApiClient;
    }

    public Map<String, Object> detail(String contentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select content_id, content_type_id, title, addr1, mapx, mapy, first_image, tel from place where content_id = ?",
                contentId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "장소 없음: " + contentId);
        }
        Map<String, Object> place = rows.get(0);
        String contentTypeId = (String) place.get("content_type_id");

        JsonNode common = cachedOrFetch(contentId, "detailCommon2", Map.of("contentId", contentId));
        JsonNode intro = cachedOrFetch(contentId, "detailIntro2",
                Map.of("contentId", contentId, "contentTypeId", contentTypeId));
        JsonNode with = accessibility(contentId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("contentId", place.get("content_id"));
        res.put("title", place.get("title"));
        res.put("addr1", place.get("addr1"));
        res.put("mapx", place.get("mapx"));
        res.put("mapy", place.get("mapy"));
        res.put("imageUrl", place.get("first_image"));
        res.put("tel", firstNonBlank(text(common, "tel"), (String) place.get("tel")));
        res.put("overview", text(common, "overview"));
        res.put("homepage", stripTags(text(common, "homepage")));
        res.put("useTime", firstNonBlank(
                text(intro, "usetime"), text(intro, "usetimeculture"), text(intro, "usetimeleports"),
                text(intro, "opentimefood"), text(intro, "opentime")));
        res.put("restDate", firstNonBlank(
                text(intro, "restdate"), text(intro, "restdateculture"), text(intro, "restdateleports"),
                text(intro, "restdatefood"), text(intro, "restdateshopping")));
        res.put("parking", firstNonBlank(
                text(intro, "parking"), text(intro, "parkingculture"), text(intro, "parkingleports"),
                text(intro, "parkingfood"), text(intro, "parkingshopping")));
        res.put("fee", firstNonBlank(
                text(intro, "usefee"), text(intro, "usefeeleports"), text(intro, "usetimefestival")));
        res.put("accessibility", accessibilityResponse(with));
        return res;
    }

    private JsonNode cachedOrFetch(String contentId, String endpoint, Map<String, String> params) {
        List<String> cached = jdbc.queryForList(
                "select payload::text from tour_raw_content where content_id = ? and endpoint = ?",
                String.class, contentId, endpoint);
        if (!cached.isEmpty()) {
            return parse(cached.get(0));
        }
        TourApiResult result = tourApiClient.call(TourApiClient.KOR_SERVICE, endpoint, params);
        if (!result.ok() || result.items().isEmpty()) {
            log.warn("{} 조회 실패 content={}: {}", endpoint, contentId, result.resultMsg());
            return null;
        }
        JsonNode item = result.items().get(0);
        jdbc.update("""
                        insert into tour_raw_content (content_id, endpoint, payload, modified_time)
                        values (?, ?, ?::jsonb, ?)
                        on conflict (content_id, endpoint) do update set payload = excluded.payload, fetched_at = now()
                        """,
                contentId, endpoint, item.toString(), item.path("modifiedtime").asText(null));
        return item;
    }

    private JsonNode accessibility(String contentId) {
        List<String> cached = jdbc.queryForList(
                "select payload::text from place_accessibility where content_id = ?", String.class, contentId);
        if (!cached.isEmpty()) {
            return cached.get(0) == null ? null : parse(cached.get(0));
        }
        TourApiResult result = tourApiClient.call(TourApiClient.WITH_SERVICE, "detailWithTour2",
                Map.of("contentId", contentId));
        JsonNode item = result.ok() && !result.items().isEmpty() ? result.items().get(0) : null;
        jdbc.update("""
                        insert into place_accessibility (content_id, payload, status)
                        values (?, ?::jsonb, ?)
                        on conflict (content_id) do update set payload = excluded.payload, status = excluded.status, fetched_at = now()
                        """,
                contentId, item == null ? null : item.toString(), deriveStatus(item));
        return item;
    }

    private Map<String, Object> accessibilityResponse(JsonNode with) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", deriveStatus(with));
        m.put("wheelchair", text(with, "wheelchair"));
        m.put("elevator", text(with, "elevator"));
        m.put("restroom", text(with, "restroom"));
        m.put("parking", text(with, "parking"));
        m.put("exit", text(with, "exit"));
        return m;
    }

    private static String deriveStatus(JsonNode with) {
        String wheelchair = text(with, "wheelchair");
        if (wheelchair == null) {
            return "UNKNOWN";
        }
        if (wheelchair.contains("불가") || wheelchair.contains("어려움")) {
            return "LIMITED";
        }
        return wheelchair.contains("가능") || wheelchair.contains("있") ? "ACCESSIBLE" : "UNKNOWN";
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        String v = node.path(field).asText("");
        return v.isBlank() ? null : v;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String stripTags(String html) {
        return html == null ? null : html.replaceAll("<[^>]+>", "").trim();
    }
}
