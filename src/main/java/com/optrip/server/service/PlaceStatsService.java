package com.optrip.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlaceStatsService {

    private static final String MAPPING_VERSION = "v0-draft";
    private static final Set<String> MAPPING_STATUSES = Set.of("MAPPED", "REVIEW", "EXCLUDED");

    private final JdbcTemplate jdbc;

    public PlaceStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> overview() {
        return jdbc.queryForMap("""
                select count(*) as "totalPlaces",
                       count(*) filter (where exists (
                           select 1 from place_intent i
                           where i.content_id = p.content_id and i.mapping_version = ?
                       )) as "placesWithIntent",
                       count(*) filter (where not exists (
                           select 1 from place_intent i
                           where i.content_id = p.content_id and i.mapping_version = ?
                       )) as "placesWithoutIntent",
                       count(*) filter (where coalesce(p.first_image, '') <> '' or coalesce(p.first_image2, '') <> '') as "placesWithImage",
                       count(*) filter (where coalesce(p.first_image, '') = '' and coalesce(p.first_image2, '') = '') as "placesWithoutImage",
                       coalesce(round(100.0 * count(*) filter (where coalesce(p.first_image, '') <> '' or coalesce(p.first_image2, '') <> '') / nullif(count(*), 0), 2), 0) as "imagePresentPct",
                       coalesce(round(100.0 * count(*) filter (where coalesce(p.first_image, '') = '' and coalesce(p.first_image2, '') = '') / nullif(count(*), 0), 2), 0) as "imageMissingPct",
                       count(*) filter (where p.mapx is not null and p.mapy is not null) as "placesWithCoordinates",
                       count(*) filter (where p.mapx is null or p.mapy is null) as "placesWithoutCoordinates",
                       coalesce(round(100.0 * count(*) filter (where p.mapx is not null and p.mapy is not null) / nullif(count(*), 0), 2), 0) as "coordinatesPresentPct",
                       coalesce(round(100.0 * count(*) filter (where p.mapx is null or p.mapy is null) / nullif(count(*), 0), 2), 0) as "coordinatesMissingPct"
                from place p
                """, MAPPING_VERSION, MAPPING_VERSION);
    }

    public Map<String, Object> purposes(String lDongRegnCd, String lDongSignguCd) {
        RegionFilter filter = regionFilter(lDongRegnCd, lDongSignguCd);
        Long totalPlaces = jdbc.queryForObject(
                "select count(*) from place p" + filter.where(), Long.class, filter.params().toArray());

        List<Object> params = new ArrayList<>();
        params.add(MAPPING_VERSION);
        params.addAll(filter.params());
        List<Map<String, Object>> items = jdbc.queryForList("""
                select i.purpose_label as "purposeLabel",
                       count(distinct p.content_id) as "placeCount",
                       coalesce(round(100.0 * count(distinct p.content_id) / nullif(?, 0), 2), 0) as "totalPlacePct"
                from place p
                join place_intent i on i.content_id = p.content_id and i.mapping_version = ?
                """ + filter.where() + " group by i.purpose_label order by \"placeCount\" desc, i.purpose_label",
                prepend(totalPlaces, params).toArray());

        return resultWithRegion(totalPlaces, lDongRegnCd, lDongSignguCd, items);
    }

    public Map<String, Object> multilabel(String lDongRegnCd, String lDongSignguCd) {
        RegionFilter filter = regionFilter(lDongRegnCd, lDongSignguCd);
        List<Object> params = new ArrayList<>();
        params.add(MAPPING_VERSION);
        params.addAll(filter.params());

        String countsCte = """
                with purpose_counts as (
                    select p.content_id, count(distinct i.purpose_label) as purpose_count
                    from place p
                    left join place_intent i on i.content_id = p.content_id and i.mapping_version = ?
                """ + filter.where() + " group by p.content_id) ";

        Map<String, Object> buckets = jdbc.queryForMap(countsCte + """
                select count(*) as "totalPlaces",
                       count(*) filter (where purpose_count = 0) as "zeroPurposes",
                       count(*) filter (where purpose_count = 1) as "onePurpose",
                       count(*) filter (where purpose_count = 2) as "twoPurposes",
                       count(*) filter (where purpose_count >= 3) as "threeOrMorePurposes"
                from purpose_counts
                """, params.toArray());
        List<Map<String, Object>> exactDistribution = jdbc.queryForList(countsCte + """
                select purpose_count as "purposeCount", count(*) as "placeCount"
                from purpose_counts group by purpose_count order by purpose_count
                """, params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mappingVersion", MAPPING_VERSION);
        result.put("regionFilter", regionDescription(lDongRegnCd, lDongSignguCd));
        result.put("buckets", buckets);
        result.put("exactDistribution", exactDistribution);
        return result;
    }

    public List<Map<String, Object>> mappings(String status, String sourceCode, String purpose) {
        String normalizedStatus = normalizeStatus(status);
        StringBuilder where = new StringBuilder(" where tm.mapping_version = ?");
        List<Object> params = new ArrayList<>(List.of(MAPPING_VERSION));
        if (notBlank(normalizedStatus)) {
            where.append(" and tm.status = ?");
            params.add(normalizedStatus);
        }
        if (notBlank(sourceCode)) {
            where.append(" and tm.source_code ilike ?");
            params.add("%" + sourceCode.trim() + "%");
        }
        if (notBlank(purpose)) {
            where.append(" and tm.purpose_label ilike ?");
            params.add("%" + purpose.trim() + "%");
        }
        return jdbc.queryForList("""
                select tm.mapping_version as "mappingVersion",
                       tm.source_scheme as "sourceScheme",
                       tm.source_code as "sourceCode",
                       (select max(lc.name) from lcls_code lc where lc.depth = 2 and lc.code = tm.source_code) as "sourceName",
                       tm.purpose_label as "purposeLabel",
                       tm.status,
                       tm.note
                from taxonomy_mapping tm
                """ + where + " order by tm.source_scheme, tm.source_code, tm.status, tm.purpose_label nulls last",
                params.toArray());
    }

    public List<Map<String, Object>> mappingStatus() {
        return jdbc.queryForList("""
                with statuses(status, sort_order) as (
                    values ('MAPPED', 1), ('REVIEW', 2), ('EXCLUDED', 3)
                ), mapping_rows as (
                    select status, count(*) as row_count
                    from taxonomy_mapping
                    where mapping_version = ?
                    group by status
                ), affected_places as (
                    select tm.status, count(distinct p.content_id) as place_count
                    from taxonomy_mapping tm
                    join place p on p.lcls2 = tm.source_code
                    where tm.mapping_version = ? and tm.source_scheme = 'lcls'
                    group by tm.status
                )
                select ? as "mappingVersion", s.status,
                       coalesce(m.row_count, 0) as "mappingRowCount",
                       coalesce(a.place_count, 0) as "distinctAffectedPlaceCount"
                from statuses s
                left join mapping_rows m on m.status = s.status
                left join affected_places a on a.status = s.status
                order by s.sort_order
                """, MAPPING_VERSION, MAPPING_VERSION, MAPPING_VERSION);
    }

    public List<Map<String, Object>> lcls(String lcls1, String lcls2) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> params = new ArrayList<>();
        if (notBlank(lcls1)) {
            where.append(" and p.lcls1 = ?");
            params.add(lcls1.trim());
        }
        if (notBlank(lcls2)) {
            where.append(" and p.lcls2 = ?");
            params.add(lcls2.trim());
        }
        params.add(MAPPING_VERSION);

        return jdbc.queryForList("""
                select p.lcls1,
                       c1.name as "lcls1Name",
                       p.lcls2,
                       c2.name as "lcls2Name",
                       p.lcls3,
                       c3.name as "lcls3Name",
                       count(distinct p.content_id) as "placeCount",
                       tm.purpose_label as "purposeLabel",
                       tm.status as "mappingStatus",
                       tm.note as "mappingNote"
                from place p
                left join lcls_code c1 on c1.depth = 1 and c1.parent_code = '' and c1.code = p.lcls1
                left join lcls_code c2 on c2.depth = 2 and c2.parent_code = p.lcls1 and c2.code = p.lcls2
                left join lcls_code c3 on c3.depth = 3 and c3.parent_code = p.lcls2 and c3.code = p.lcls3
                left join taxonomy_mapping tm on tm.mapping_version = ?
                    and tm.source_scheme = 'lcls' and tm.source_code = p.lcls2
                """ + where + """
                group by p.lcls1, c1.name, p.lcls2, c2.name, p.lcls3, c3.name,
                         tm.purpose_label, tm.status, tm.note
                order by p.lcls1 nulls last, p.lcls2 nulls last, p.lcls3 nulls last,
                         tm.status nulls last, tm.purpose_label nulls last
                """, rotateLastToFirst(params).toArray());
    }

    public Map<String, Object> contentTypes(String lDongRegnCd, String lDongSignguCd) {
        RegionFilter filter = regionFilter(lDongRegnCd, lDongSignguCd);
        Long totalPlaces = jdbc.queryForObject(
                "select count(*) from place p" + filter.where(), Long.class, filter.params().toArray());
        List<Map<String, Object>> items = jdbc.queryForList("""
                select p.content_type_id as "contentTypeId", count(*) as "placeCount"
                from place p
                """ + filter.where() + " group by p.content_type_id order by \"placeCount\" desc, p.content_type_id",
                filter.params().toArray());
        return resultWithRegion(totalPlaces, lDongRegnCd, lDongSignguCd, items);
    }

    public Map<String, Object> unclassifiedReasons() {
        List<Map<String, Object>> reasons = jdbc.queryForList("""
                with unclassified as (
                    select p.content_id,
                           case
                               when exists (select 1 from taxonomy_mapping tm
                                   where tm.mapping_version = ? and tm.source_scheme = 'lcls'
                                     and tm.source_code = p.lcls2 and tm.status = 'MAPPED')
                                   then 'MAPPED_RULE_BUT_NO_PLACE_INTENT'
                               when exists (select 1 from taxonomy_mapping tm
                                   where tm.mapping_version = ? and tm.source_scheme = 'lcls'
                                     and tm.source_code = p.lcls2 and tm.status = 'REVIEW')
                                   then 'REVIEW'
                               when exists (select 1 from taxonomy_mapping tm
                                   where tm.mapping_version = ? and tm.source_scheme = 'lcls'
                                     and tm.source_code = p.lcls2 and tm.status = 'EXCLUDED')
                                   then 'EXCLUDED'
                               when p.lcls2 is not null and not exists (select 1 from taxonomy_mapping tm
                                   where tm.mapping_version = ? and tm.source_scheme = 'lcls'
                                     and tm.source_code = p.lcls2)
                                   then 'NO_TAXONOMY_MAPPING'
                               else 'OTHER'
                           end as reason
                    from place p
                    where not exists (select 1 from place_intent i
                        where i.content_id = p.content_id and i.mapping_version = ?)
                )
                select reason, count(*) as "placeCount"
                from unclassified group by reason order by "placeCount" desc, reason
                """, MAPPING_VERSION, MAPPING_VERSION, MAPPING_VERSION, MAPPING_VERSION, MAPPING_VERSION);

        Long total = reasons.stream()
                .map(row -> ((Number) row.get("placeCount")).longValue())
                .reduce(0L, Long::sum);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mappingVersion", MAPPING_VERSION);
        result.put("totalPlacesWithoutIntent", total);
        result.put("classificationPriority", List.of(
                "MAPPED_RULE_BUT_NO_PLACE_INTENT", "REVIEW", "EXCLUDED", "NO_TAXONOMY_MAPPING", "OTHER"));
        result.put("reasons", reasons);
        result.put("limitation", "lcls2와 v0-draft 매핑 상태로만 구분하며, REVIEW/EXCLUDED가 개별 장소에 의미상 적합한지는 판정하지 않음");
        return result;
    }

    private static RegionFilter regionFilter(String lDongRegnCd, String lDongSignguCd) {
        if (notBlank(lDongSignguCd) && !notBlank(lDongRegnCd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "lDongSignguCd를 사용하려면 lDongRegnCd도 함께 입력해야 합니다.");
        }
        if (!notBlank(lDongRegnCd)) {
            return new RegionFilter("", List.of());
        }
        StringBuilder where = new StringBuilder("""
                 where exists (
                    select 1 from tour_raw_content r
                    where r.content_id = p.content_id and r.endpoint = 'areaBasedList2'
                      and r.payload ->> 'lDongRegnCd' = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(lDongRegnCd.trim());
        if (notBlank(lDongSignguCd)) {
            where.append(" and r.payload ->> 'lDongSignguCd' = ?");
            params.add(lDongSignguCd.trim());
        }
        where.append(")");
        return new RegionFilter(where.toString(), params);
    }

    private static Map<String, Object> resultWithRegion(Long totalPlaces, String lDongRegnCd,
                                                         String lDongSignguCd, Object items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mappingVersion", MAPPING_VERSION);
        result.put("regionFilter", regionDescription(lDongRegnCd, lDongSignguCd));
        result.put("totalPlaces", totalPlaces);
        result.put("items", items);
        return result;
    }

    private static Map<String, Object> regionDescription(String lDongRegnCd, String lDongSignguCd) {
        Map<String, Object> region = new LinkedHashMap<>();
        region.put("lDongRegnCd", notBlank(lDongRegnCd) ? lDongRegnCd.trim() : null);
        region.put("lDongSignguCd", notBlank(lDongSignguCd) ? lDongSignguCd.trim() : null);
        region.put("source", "tour_raw_content.areaBasedList2.payload");
        return region;
    }

    private static String normalizeStatus(String status) {
        if (!notBlank(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!MAPPING_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status는 MAPPED, REVIEW, EXCLUDED 중 하나여야 합니다.");
        }
        return normalized;
    }

    private static List<Object> prepend(Object first, List<Object> rest) {
        List<Object> result = new ArrayList<>();
        result.add(first);
        result.addAll(rest);
        return result;
    }

    private static List<Object> rotateLastToFirst(List<Object> values) {
        List<Object> result = new ArrayList<>();
        result.add(values.get(values.size() - 1));
        result.addAll(values.subList(0, values.size() - 1));
        return result;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record RegionFilter(String where, List<Object> params) {}
}
