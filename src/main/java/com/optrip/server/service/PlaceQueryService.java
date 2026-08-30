package com.optrip.server.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlaceQueryService {

    private static final String MAPPING_VERSION = "v0-draft";

    private final JdbcTemplate jdbc;

    public PlaceQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> search(String lDongRegnCd, String lDongSignguCd, String contentTypeId,
                                      String purpose, String q, int limit, int offset) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> params = new ArrayList<>();

        if (notBlank(lDongRegnCd)) {
            where.append(" and p.l_dong_regn_cd = ?");
            params.add(lDongRegnCd);
        }
        if (notBlank(lDongSignguCd)) {
            where.append(" and p.l_dong_signgu_cd = ?");
            params.add(lDongSignguCd);
        }
        if (notBlank(contentTypeId)) {
            where.append(" and p.content_type_id = ?");
            params.add(contentTypeId);
        }
        if (notBlank(purpose)) {
            where.append(" and exists (select 1 from place_intent i where i.content_id = p.content_id and i.purpose_label = ? and i.mapping_version = ?)");
            params.add(purpose);
            params.add(MAPPING_VERSION);
        }
        if (notBlank(q)) {
            where.append(" and p.title ilike ?");
            params.add("%" + q + "%");
        }

        Long total = jdbc.queryForObject("select count(*) from place p" + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(limit);
        pageParams.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.content_id, p.content_type_id, p.title, p.addr1, p.addr2,
                       p.l_dong_regn_cd, p.l_dong_signgu_cd, p.lcls1, p.lcls2, p.lcls3,
                       p.mapx, p.mapy, p.first_image, p.tel, p.modified_time,
                       (select array_agg(distinct i.purpose_label) from place_intent i
                         where i.content_id = p.content_id and i.mapping_version = '%s') as purposes
                from place p
                """.formatted(MAPPING_VERSION) + where + " order by p.title limit ? offset ?",
                pageParams.toArray());

        List<Map<String, Object>> items = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contentId", r.get("content_id"));
            m.put("contentTypeId", r.get("content_type_id"));
            m.put("title", r.get("title"));
            m.put("addr1", r.get("addr1"));
            m.put("addr2", r.get("addr2"));
            m.put("lDongRegnCd", r.get("l_dong_regn_cd"));
            m.put("lDongSignguCd", r.get("l_dong_signgu_cd"));
            m.put("lcls1", r.get("lcls1"));
            m.put("lcls2", r.get("lcls2"));
            m.put("lcls3", r.get("lcls3"));
            m.put("mapx", r.get("mapx"));
            m.put("mapy", r.get("mapy"));
            m.put("firstImage", r.get("first_image"));
            m.put("tel", r.get("tel"));
            m.put("modifiedTime", r.get("modified_time"));
            Object purposes = r.get("purposes");
            m.put("purposes", purposes instanceof java.sql.Array a ? toList(a) : List.of());
            return m;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("limit", limit);
        result.put("offset", offset);
        result.put("items", items);
        return result;
    }

    private static List<Object> toList(java.sql.Array array) {
        try {
            return List.of((Object[]) array.getArray());
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
