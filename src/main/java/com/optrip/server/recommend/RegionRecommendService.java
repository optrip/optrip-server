package com.optrip.server.recommend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RegionRecommendService {

    public record RegionRequest(List<String> purposes, List<String> destinations, List<String> excludeRegions,
                                Double originMapx, Double originMapy, Integer limit) {
    }

    private static final int MIN_PER_PURPOSE = 3;

    private final JdbcTemplate jdbc;

    public RegionRecommendService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> recommend(RegionRequest request) {
        List<String> purposes = request.purposes() == null || request.purposes().isEmpty()
                ? List.of("자연/풍경", "맛집")
                : request.purposes().stream().filter(Purposes.ACTIVE::contains).toList();
        int limit = request.limit() == null ? 3 : Math.clamp(request.limit(), 1, 5);
        Set<String> exclude = request.excludeRegions() == null ? Set.of() : Set.copyOf(request.excludeRegions());

        String inClause = String.join(",", purposes.stream().map(p -> "?").toList());
        List<Object> params = new ArrayList<>(purposes);
        params.add(Purposes.MAPPING_VERSION);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                        select p.l_dong_regn_cd r, p.l_dong_signgu_cd s, i.purpose_label pl,
                               count(distinct p.content_id) cnt,
                               avg(p.mapx) cx, avg(p.mapy) cy,
                               coalesce(stddev_pop(p.mapx), 0) sx, coalesce(stddev_pop(p.mapy), 0) sy
                        from place p
                        join place_intent i on i.content_id = p.content_id
                        where i.purpose_label in (%s) and i.mapping_version = ?
                          and p.l_dong_regn_cd is not null and p.l_dong_signgu_cd is not null
                          and p.mapx is not null
                        group by 1, 2, 3
                        """.formatted(inClause),
                params.toArray());

        Map<String, RegionAgg> byRegion = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = row.get("r") + "-" + row.get("s");
            RegionAgg agg = byRegion.computeIfAbsent(key, k -> new RegionAgg((String) row.get("r"), (String) row.get("s")));
            agg.purposeCounts.put((String) row.get("pl"), ((Number) row.get("cnt")).longValue());
            agg.cx = ((Number) row.get("cx")).doubleValue();
            agg.cy = ((Number) row.get("cy")).doubleValue();
            agg.spread = Math.max(agg.spread,
                    Math.hypot(((Number) row.get("sx")).doubleValue(), ((Number) row.get("sy")).doubleValue()));
        }

        Set<String> userKeys = new LinkedHashSet<>();
        List<Map<String, Object>> results = new ArrayList<>();
        if (request.destinations() != null) {
            for (String dest : request.destinations()) {
                if (dest == null || dest.isBlank()) continue;
                Map<String, Object> region = resolveRegionByName(dest.trim());
                if (region == null) continue;
                String key = region.get("parent_code") + "-" + region.get("code");
                if (!userKeys.add(key)) continue;
                RegionAgg agg = byRegion.get(key);
                results.add(toResponse((String) region.get("parent_code"), (String) region.get("code"),
                        (String) region.get("name"), "user", agg, purposes));
            }
        }

        byRegion.values().stream()
                .filter(a -> purposes.stream().allMatch(p -> a.purposeCounts.getOrDefault(p, 0L) >= MIN_PER_PURPOSE))
                .filter(a -> !userKeys.contains(a.key()) && !exclude.contains(a.key()))
                .sorted(Comparator.comparingDouble((RegionAgg a) -> -score(a, purposes, request)))
                .limit(Math.max(0, limit - results.size()))
                .forEach(a -> results.add(toResponse(a.regn, a.signgu, regionName(a.regn, a.signgu), "ai", a, purposes)));

        return Map.of("regions", results);
    }

    private double score(RegionAgg agg, List<String> purposes, RegionRequest request) {
        double harmonic = purposes.size() / purposes.stream()
                .mapToDouble(p -> 1.0 / Math.min(agg.purposeCounts.getOrDefault(p, 0L), 200))
                .sum();
        double cohesion = 1.0 / (1.0 + agg.spread / 0.1);
        double origin = 1.0;
        if (request.originMapx() != null && request.originMapy() != null) {
            double km = haversineKm(request.originMapy(), request.originMapx(), agg.cy, agg.cx);
            origin = 1.0 / (1.0 + km / 150.0);
        }
        return harmonic * cohesion * origin;
    }

    private Map<String, Object> toResponse(String regn, String signgu, String name, String source,
                                           RegionAgg agg, List<String> purposes) {
        List<String> reasons = new ArrayList<>();
        if (agg != null) {
            agg.purposeCounts.entrySet().stream()
                    .filter(e -> purposes.contains(e.getKey()))
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(2)
                    .forEach(e -> reasons.add("%s 명소 %d곳".formatted(e.getKey(), e.getValue())));
        }
        if (reasons.isEmpty()) {
            reasons.add(source.equals("user") ? "직접 선택한 목적지" : "취향과 맞는 지역");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name == null ? regn + "-" + signgu : shortName(name));
        m.put("lDongRegnCd", regn);
        m.put("lDongSignguCd", signgu);
        m.put("source", source);
        m.put("reasons", reasons);
        m.put("imageUrl", regionImage(regn, signgu, purposes));
        return m;
    }

    private Map<String, Object> resolveRegionByName(String name) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                        select parent_code, code, name from ldong_code
                        where parent_code <> '' and name like ?
                        order by length(name), code limit 1
                        """, "%" + name + "%");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String regionName(String regn, String signgu) {
        List<String> rows = jdbc.queryForList(
                "select name from ldong_code where parent_code = ? and code = ?", String.class, regn, signgu);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String regionImage(String regn, String signgu, List<String> purposes) {
        String inClause = String.join(",", purposes.stream().map(p -> "?").toList());
        List<Object> params = new ArrayList<>();
        params.add(regn);
        params.add(signgu);
        params.addAll(purposes);
        params.add(Purposes.MAPPING_VERSION);
        List<String> rows = jdbc.queryForList("""
                        select p.first_image from place p
                        join place_intent i on i.content_id = p.content_id
                        where p.l_dong_regn_cd = ? and p.l_dong_signgu_cd = ?
                          and i.purpose_label in (%s) and i.mapping_version = ?
                          and coalesce(p.first_image, '') <> ''
                        order by p.content_type_id, p.content_id limit 1
                        """.formatted(inClause),
                String.class, params.toArray());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String shortName(String name) {
        return name.replace("특별자치도", "").replace("특별자치시", "")
                .replaceAll("^(서울특별시|부산광역시|대구광역시|인천광역시|대전광역시|울산광역시|경기도|강원|충청북도|충청남도|전라남도|경상북도|경상남도|제주|전북) ?", "")
                .trim();
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static class RegionAgg {
        final String regn;
        final String signgu;
        final Map<String, Long> purposeCounts = new LinkedHashMap<>();
        double cx;
        double cy;
        double spread;

        RegionAgg(String regn, String signgu) {
            this.regn = regn;
            this.signgu = signgu;
        }

        String key() {
            return regn + "-" + signgu;
        }
    }
}
