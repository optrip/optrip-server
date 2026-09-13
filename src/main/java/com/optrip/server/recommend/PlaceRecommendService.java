package com.optrip.server.recommend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlaceRecommendService {

    public record PlaceRequest(String lDongRegnCd, String lDongSignguCd, List<String> purposes,
                               Integer coreCount, Integer suggestionCount, Integer suggestionsPerPurpose) {
    }

    private static final List<String> TYPE_PRIORITY = List.of("12", "14", "28", "39", "38");

    private final JdbcTemplate jdbc;

    public PlaceRecommendService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> recommend(PlaceRequest request) {
        List<String> purposes = request.purposes() == null || request.purposes().isEmpty()
                ? List.of("자연/풍경", "맛집")
                : request.purposes().stream().filter(Purposes.ACTIVE::contains).toList();
        int coreCount = request.coreCount() == null ? 2 : Math.clamp(request.coreCount(), 1, 4);
        int suggestionCount = request.suggestionCount() == null ? 6 : Math.clamp(request.suggestionCount(), 2, 12);

        Map<String, List<Candidate>> byPurpose = new LinkedHashMap<>();
        for (String purpose : purposes) {
            byPurpose.put(purpose, candidates(request.lDongRegnCd(), request.lDongSignguCd(), purpose));
        }

        Set<String> used = new LinkedHashSet<>();
        List<Map<String, Object>> core = pickRoundRobin(byPurpose, purposes, used, coreCount, true);

        List<Map<String, Object>> suggestions;
        if (request.suggestionsPerPurpose() != null) {
            int perPurpose = Math.clamp(request.suggestionsPerPurpose(), 1, 10);
            suggestions = new ArrayList<>();
            for (String purpose : purposes) {
                byPurpose.getOrDefault(purpose, List.of()).stream()
                        .filter(c -> !used.contains(c.contentId))
                        .limit(perPurpose)
                        .forEach(c -> {
                            used.add(c.contentId);
                            suggestions.add(c.toResponse(purpose, false));
                        });
            }
        } else {
            suggestions = pickRoundRobin(byPurpose, purposes, used, suggestionCount, false);
        }

        return Map.of("core", core, "suggestions", suggestions);
    }

    private List<Map<String, Object>> pickRoundRobin(Map<String, List<Candidate>> byPurpose, List<String> purposes,
                                                     Set<String> used, int count, boolean core) {
        List<Map<String, Object>> picked = new ArrayList<>();
        int round = 0;
        while (picked.size() < count && round < 50) {
            boolean any = false;
            for (String purpose : purposes) {
                if (picked.size() >= count) break;
                List<Candidate> list = byPurpose.getOrDefault(purpose, List.of());
                Candidate next = list.stream().filter(c -> !used.contains(c.contentId)).findFirst().orElse(null);
                if (next == null) continue;
                used.add(next.contentId);
                picked.add(next.toResponse(purpose, core));
                any = true;
            }
            if (!any) break;
            round++;
        }
        return picked;
    }

    private List<Candidate> candidates(String regn, String signgu, String purpose) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                        select p.content_id, p.title, p.addr1, p.mapx, p.mapy, p.first_image, p.content_type_id,
                               coalesce(l3.name, l2.name, '') cls_name
                        from place p
                        join place_intent i on i.content_id = p.content_id
                        left join lcls_code l3 on l3.code = p.lcls3
                        left join lcls_code l2 on l2.code = p.lcls2 and l2.parent_code <> ''
                        where p.l_dong_regn_cd = ? and p.l_dong_signgu_cd = ?
                          and i.purpose_label = ? and i.mapping_version = ?
                          and p.mapx is not null
                        """, regn, signgu, purpose, Purposes.MAPPING_VERSION);

        return rows.stream()
                .map(Candidate::new)
                .sorted((a, b) -> {
                    int img = Boolean.compare(b.hasImage, a.hasImage);
                    if (img != 0) return img;
                    int type = Integer.compare(typeRank(a.contentTypeId), typeRank(b.contentTypeId));
                    if (type != 0) return type;
                    return a.contentId.compareTo(b.contentId);
                })
                .toList();
    }

    private static int typeRank(String contentTypeId) {
        int idx = TYPE_PRIORITY.indexOf(contentTypeId);
        return idx < 0 ? TYPE_PRIORITY.size() : idx;
    }

    private static class Candidate {
        final String contentId;
        final String title;
        final String addr1;
        final Object mapx;
        final Object mapy;
        final String imageUrl;
        final String contentTypeId;
        final String clsName;
        final boolean hasImage;

        Candidate(Map<String, Object> row) {
            this.contentId = (String) row.get("content_id");
            this.title = (String) row.get("title");
            this.addr1 = (String) row.get("addr1");
            this.mapx = row.get("mapx");
            this.mapy = row.get("mapy");
            this.imageUrl = (String) row.get("first_image");
            this.contentTypeId = (String) row.get("content_type_id");
            this.clsName = (String) row.get("cls_name");
            this.hasImage = imageUrl != null && !imageUrl.isBlank();
        }

        Map<String, Object> toResponse(String purpose, boolean core) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contentId", contentId);
            m.put("title", title);
            m.put("addr1", addr1);
            m.put("mapx", mapx);
            m.put("mapy", mapy);
            m.put("imageUrl", imageUrl);
            m.put("purpose", purpose);
            String cls = clsName == null || clsName.isBlank() ? "" : clsName + " · ";
            m.put("reason", cls + "'" + purpose + "' 취향과 맞는 곳" + (core ? "이라 먼저 추천해요" : "이에요"));
            return m;
        }
    }
}
