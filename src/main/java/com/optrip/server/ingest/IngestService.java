package com.optrip.server.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optrip.server.client.tour.TourApiClient;
import com.optrip.server.client.tour.TourApiClient.TourApiResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TourAPI -> DB 수집 배치.
// 수집은 수 분 이상 걸릴 수 있어 단일 스레드 executor 로 백그라운드 실행하고,
// 진행 상황은 ingest_run / api_call_log 테이블로 추적한다.
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    // areaBasedList2 페이지 크기
    private static final int PAGE_SIZE = 100;
    // 한 번의 실행에서 허용하는 최대 API 호출 수 (일 1,000건 한도 보호)
    private static final int MAX_CALLS_PER_RUN = 900;

    private final TourApiClient client;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 수집 작업 직렬화: 동시에 하나만 실행해 호출 한도와 DB 부하를 통제한다
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public IngestService(TourApiClient client, JdbcTemplate jdbc) {
        this.client = client;
        this.jdbc = jdbc;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // ──────────────────────────────────────────────
    // 실행 관리
    // ──────────────────────────────────────────────

    private long startRun(String kind, Map<String, String> params) {
        return jdbc.queryForObject(
                "insert into ingest_run (kind, params) values (?, ?::jsonb) returning id",
                Long.class, kind, toJson(params));
    }

    private void finishRun(long runId, String status, int apiCalls, int items, String error) {
        jdbc.update("update ingest_run set status = ?, api_calls = ?, items_upserted = ?, error = ?, finished_at = now() where id = ?",
                status, apiCalls, items, error, runId);
    }

    private long submit(String kind, Map<String, String> params, IngestJob job) {
        long runId = startRun(kind, params);
        executor.submit(() -> {
            Counters c = new Counters();
            try {
                job.run(runId, c);
                finishRun(runId, "DONE", c.calls, c.items, null);
                log.info("ingest {} run={} done: calls={} items={}", kind, runId, c.calls, c.items);
            } catch (Exception e) {
                finishRun(runId, "FAILED", c.calls, c.items, e.getMessage());
                log.error("ingest {} run={} failed", kind, runId, e);
            }
        });
        return runId;
    }

    private interface IngestJob {
        void run(long runId, Counters c) throws Exception;
    }

    private static class Counters {
        int calls;
        int items;
    }

    // 호출 + 로그 기록 + 한도 확인을 한 곳에서
    private TourApiResult callLogged(long runId, Counters c, String service, String operation, Map<String, String> params) {
        if (c.calls >= MAX_CALLS_PER_RUN) {
            throw new IllegalStateException("run 호출 한도(" + MAX_CALLS_PER_RUN + ") 도달 — 다음 실행에서 이어서 수집");
        }
        TourApiResult result = client.call(service, operation, params);
        c.calls++;
        jdbc.update("insert into api_call_log (run_id, service, operation, params, http_status, result_code, duration_ms) values (?, ?, ?, ?::jsonb, ?, ?, ?)",
                runId, service, operation, toJson(params), result.httpStatus(), result.resultCode(), (int) result.durationMs());
        if (!result.ok()) {
            log.warn("tourapi {}/{} failed: code={} msg={}", service, operation, result.resultCode(), result.resultMsg());
        }
        client.pause();
        return result;
    }

    // ──────────────────────────────────────────────
    // 1) 코드류: 시도/시군구 + 신분류체계
    // ──────────────────────────────────────────────

    public long ingestCodes() {
        return submit("codes", Map.of(), (runId, c) -> {
            // 시도
            TourApiResult areas = callLogged(runId, c, TourApiClient.KOR_SERVICE, "areaCode2",
                    Map.of("numOfRows", "100"));
            for (JsonNode area : areas.items()) {
                upsertAreaCode("", area);
                c.items++;
            }
            // 시도별 시군구
            for (JsonNode area : areas.items()) {
                String areaCode = area.path("code").asText();
                TourApiResult sigungus = callLogged(runId, c, TourApiClient.KOR_SERVICE, "areaCode2",
                        Map.of("areaCode", areaCode, "numOfRows", "100"));
                for (JsonNode sigungu : sigungus.items()) {
                    upsertAreaCode(areaCode, sigungu);
                    c.items++;
                }
            }
            // 신분류체계 1~3depth
            ingestLclsDepth(runId, c, "", "", 1);
        });
    }

    // lcls1Code: depth1 조상 코드, parentCode: 직전 depth 의 부모 코드
    // (depth1 코드가 "HS"처럼 2글자만 있는 게 아니라 "C01"처럼 3글자도 있어 자르지 않고 그대로 넘긴다)
    private void ingestLclsDepth(long runId, Counters c, String lcls1Code, String parentCode, int depth) {
        if (depth > 3) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("numOfRows", "300");
        if (depth >= 2) {
            params.put("lclsSystm1", lcls1Code);
        }
        if (depth == 3) {
            params.put("lclsSystm2", parentCode);
        }
        TourApiResult result = callLogged(runId, c, TourApiClient.KOR_SERVICE, "lclsSystmCode2", params);
        for (JsonNode item : result.items()) {
            String code = item.path("code").asText();
            jdbc.update("""
                            insert into lcls_code (parent_code, code, name, depth, payload)
                            values (?, ?, ?, ?, ?::jsonb)
                            on conflict (parent_code, code) do update set name = excluded.name, payload = excluded.payload, fetched_at = now()
                            """,
                    parentCode, code, item.path("name").asText(), depth, item.toString());
            c.items++;
            ingestLclsDepth(runId, c, depth == 1 ? code : lcls1Code, code, depth + 1);
        }
    }

    private void upsertAreaCode(String parentCode, JsonNode item) {
        jdbc.update("""
                        insert into area_code (parent_code, code, name, rnum)
                        values (?, ?, ?, ?)
                        on conflict (parent_code, code) do update set name = excluded.name, rnum = excluded.rnum, fetched_at = now()
                        """,
                parentCode, item.path("code").asText(), item.path("name").asText(), item.path("rnum").asInt());
    }

    // ──────────────────────────────────────────────
    // 2) 지역 목록: areaBasedList2 → tour_raw_content + place
    // ──────────────────────────────────────────────

    public long ingestAreaList(String areaCode, String sigunguCode, List<String> contentTypeIds) {
        Map<String, String> runParams = new LinkedHashMap<>();
        runParams.put("areaCode", areaCode);
        runParams.put("sigunguCode", sigunguCode == null ? "" : sigunguCode);
        runParams.put("contentTypeIds", String.join(",", contentTypeIds));

        return submit("area-list", runParams, (runId, c) -> {
            for (String contentTypeId : contentTypeIds) {
                int pageNo = 1;
                while (true) {
                    Map<String, String> params = new LinkedHashMap<>();
                    params.put("areaCode", areaCode);
                    params.put("sigunguCode", sigunguCode);
                    params.put("contentTypeId", contentTypeId);
                    params.put("numOfRows", String.valueOf(PAGE_SIZE));
                    params.put("pageNo", String.valueOf(pageNo));
                    params.put("arrange", "C"); // 수정일순 → 증분 확인이 쉬움

                    TourApiResult result = callLogged(runId, c, TourApiClient.KOR_SERVICE, "areaBasedList2", params);
                    if (!result.ok()) {
                        throw new IllegalStateException("areaBasedList2 실패: " + result.resultCode() + " " + result.resultMsg());
                    }
                    for (JsonNode item : result.items()) {
                        upsertRaw(item.path("contentid").asText(), "areaBasedList2", item);
                        upsertPlace(item);
                        c.items++;
                    }
                    if (pageNo * PAGE_SIZE >= result.totalCount() || result.items().isEmpty()) {
                        break;
                    }
                    pageNo++;
                }
            }
        });
    }

    private void upsertRaw(String contentId, String endpoint, JsonNode payload) {
        jdbc.update("""
                        insert into tour_raw_content (content_id, endpoint, payload, modified_time)
                        values (?, ?, ?::jsonb, ?)
                        on conflict (content_id, endpoint) do update set payload = excluded.payload, modified_time = excluded.modified_time, fetched_at = now()
                        """,
                contentId, endpoint, payload.toString(), payload.path("modifiedtime").asText(null));
    }

    private void upsertPlace(JsonNode item) {
        jdbc.update("""
                        insert into place (content_id, content_type_id, title, addr1, addr2, zipcode,
                                           area_code, sigungu_code, cat1, cat2, cat3, lcls1, lcls2, lcls3,
                                           mapx, mapy, mlevel, first_image, first_image2, tel, created_time, modified_time)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (content_id) do update set
                            content_type_id = excluded.content_type_id, title = excluded.title,
                            addr1 = excluded.addr1, addr2 = excluded.addr2, zipcode = excluded.zipcode,
                            area_code = excluded.area_code, sigungu_code = excluded.sigungu_code,
                            cat1 = excluded.cat1, cat2 = excluded.cat2, cat3 = excluded.cat3,
                            lcls1 = excluded.lcls1, lcls2 = excluded.lcls2, lcls3 = excluded.lcls3,
                            mapx = excluded.mapx, mapy = excluded.mapy, mlevel = excluded.mlevel,
                            first_image = excluded.first_image, first_image2 = excluded.first_image2,
                            tel = excluded.tel, created_time = excluded.created_time,
                            modified_time = excluded.modified_time, last_synced_at = now()
                        """,
                item.path("contentid").asText(), item.path("contenttypeid").asText(), item.path("title").asText(),
                text(item, "addr1"), text(item, "addr2"), text(item, "zipcode"),
                text(item, "areacode"), text(item, "sigungucode"),
                text(item, "cat1"), text(item, "cat2"), text(item, "cat3"),
                text(item, "lclsSystm1"), text(item, "lclsSystm2"), text(item, "lclsSystm3"),
                doubleOrNull(item, "mapx"), doubleOrNull(item, "mapy"), text(item, "mlevel"),
                text(item, "firstimage"), text(item, "firstimage2"), text(item, "tel"),
                text(item, "createdtime"), text(item, "modifiedtime"));
    }

    // ──────────────────────────────────────────────
    // 3) 상세: detailCommon2 + detailIntro2 (raw 저장)
    // ──────────────────────────────────────────────

    public long ingestDetails(String areaCode, int limit) {
        return submit("details", Map.of("areaCode", areaCode, "limit", String.valueOf(limit)), (runId, c) -> {
            // 아직 detailCommon2 원문이 없는 장소부터
            List<Map<String, Object>> targets = jdbc.queryForList("""
                            select p.content_id, p.content_type_id from place p
                            where p.area_code = ?
                              and not exists (select 1 from tour_raw_content r
                                              where r.content_id = p.content_id and r.endpoint = 'detailCommon2')
                            order by p.content_id
                            limit ?
                            """,
                    areaCode, limit);
            for (Map<String, Object> row : targets) {
                String contentId = (String) row.get("content_id");
                String contentTypeId = (String) row.get("content_type_id");

                TourApiResult common = callLogged(runId, c, TourApiClient.KOR_SERVICE, "detailCommon2",
                        Map.of("contentId", contentId));
                if (common.ok() && !common.items().isEmpty()) {
                    upsertRaw(contentId, "detailCommon2", common.items().get(0));
                    c.items++;
                }
                TourApiResult intro = callLogged(runId, c, TourApiClient.KOR_SERVICE, "detailIntro2",
                        Map.of("contentId", contentId, "contentTypeId", contentTypeId));
                if (intro.ok() && !intro.items().isEmpty()) {
                    upsertRaw(contentId, "detailIntro2", intro.items().get(0));
                    c.items++;
                }
            }
        });
    }

    // ──────────────────────────────────────────────
    // 4) 무장애: KorWithService2 detailWithTour2
    //    status 판정은 파생 단계 몫 — 여기서는 원문 저장 + UNKNOWN 유지
    // ──────────────────────────────────────────────

    public long ingestAccessibility(String areaCode, int limit) {
        return submit("accessibility", Map.of("areaCode", areaCode, "limit", String.valueOf(limit)), (runId, c) -> {
            List<Map<String, Object>> targets = jdbc.queryForList("""
                            select p.content_id from place p
                            where p.area_code = ?
                              and not exists (select 1 from place_accessibility a where a.content_id = p.content_id)
                            order by p.content_id
                            limit ?
                            """,
                    areaCode, limit);
            for (Map<String, Object> row : targets) {
                String contentId = (String) row.get("content_id");
                TourApiResult result = callLogged(runId, c, TourApiClient.WITH_SERVICE, "detailWithTour2",
                        Map.of("contentId", contentId));
                String payload = result.ok() && !result.items().isEmpty() ? result.items().get(0).toString() : null;
                jdbc.update("""
                                insert into place_accessibility (content_id, payload)
                                values (?, ?::jsonb)
                                on conflict (content_id) do update set payload = excluded.payload, fetched_at = now()
                                """,
                        contentId, payload);
                c.items++;
            }
        });
    }

    // ──────────────────────────────────────────────
    // 조회
    // ──────────────────────────────────────────────

    public Map<String, Object> run(long runId) {
        return jdbc.queryForMap("select * from ingest_run where id = ?", runId);
    }

    public List<Map<String, Object>> runs(int limit) {
        return jdbc.queryForList("select * from ingest_run order by id desc limit ?", limit);
    }

    // Phase 3 실측용 기본 통계: 지역x타입 후보 수, 결측률, 호출 성능
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("placesByAreaAndType", jdbc.queryForList("""
                select area_code, content_type_id, count(*) as cnt
                from place group by area_code, content_type_id order by area_code, content_type_id
                """));
        stats.put("missingRates", jdbc.queryForList("""
                select area_code,
                       count(*) as total,
                       round(avg(case when mapx is null or mapy is null then 1 else 0 end) * 100, 1) as no_coord_pct,
                       round(avg(case when coalesce(first_image, '') = '' then 1 else 0 end) * 100, 1) as no_image_pct,
                       round(avg(case when coalesce(tel, '') = '' then 1 else 0 end) * 100, 1) as no_tel_pct
                from place group by area_code order by area_code
                """));
        stats.put("apiCalls", jdbc.queryForList("""
                select service, operation,
                       count(*) as calls,
                       round(avg(duration_ms)) as avg_ms,
                       round(avg(case when result_code = '0000' then 1 else 0 end) * 100, 1) as success_pct
                from api_call_log group by service, operation order by service, operation
                """));
        stats.put("accessibilityCoverage", jdbc.queryForList("""
                select p.area_code,
                       count(a.content_id) as fetched,
                       count(a.payload) as has_payload
                from place p left join place_accessibility a on a.content_id = p.content_id
                group by p.area_code order by p.area_code
                """));
        return stats;
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
