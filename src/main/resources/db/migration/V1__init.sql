-- OPTRIP 관광데이터 스키마 V1
-- 레이어 구분: 원천(raw) / 코드 / 정규화(place) / 매핑(taxonomy) / 파생(intent) / 보정(override) / 접근성 / 수집 운영
-- V2.1 데이터 계약 원칙: 원천 보존, 매핑 버전 관리, 필드 단위 수동 보정, UNKNOWN 유지, 호출 실측 기록

-- 시도/시군구 코드 (areaCode2). parent_code='' 이면 시도, 아니면 해당 시도의 시군구
create table area_code (
    parent_code text not null default '',
    code        text not null,
    name        text not null,
    rnum        int,
    fetched_at  timestamptz not null default now(),
    primary key (parent_code, code)
);

-- 신분류체계 코드 (lclsSystmCode2). depth 1~3
create table lcls_code (
    parent_code text not null default '',
    code        text not null,
    name        text not null,
    depth       int  not null,
    payload     jsonb,
    fetched_at  timestamptz not null default now(),
    primary key (parent_code, code)
);

-- API 응답 원문. 같은 콘텐츠라도 endpoint 별로 한 행 (재파싱·재현성 근거)
create table tour_raw_content (
    content_id    text not null,
    endpoint      text not null,   -- areaBasedList2 / detailCommon2 / detailIntro2 / detailWithTour2 ...
    payload       jsonb not null,
    modified_time text,
    fetched_at    timestamptz not null default now(),
    primary key (content_id, endpoint)
);

-- 목록 응답을 정규화한 장소 마스터
create table place (
    content_id      text primary key,
    content_type_id text not null,
    title           text not null,
    addr1           text,
    addr2           text,
    zipcode         text,
    area_code       text,
    sigungu_code    text,
    cat1            text,
    cat2            text,
    cat3            text,
    lcls1           text,
    lcls2           text,
    lcls3           text,
    mapx            double precision,
    mapy            double precision,
    mlevel          text,
    first_image     text,
    first_image2    text,
    tel             text,
    created_time    text,
    modified_time   text,
    last_synced_at  timestamptz not null default now()
);
create index idx_place_area on place (area_code, sigungu_code);
create index idx_place_type on place (content_type_id);
create index idx_place_lcls on place (lcls1, lcls2, lcls3);

-- 공식 분류코드 -> OPTRIP 추구미(12) 매핑. 버전 필수, 애매한 코드는 REVIEW 로 남긴다
create table taxonomy_mapping (
    id              bigserial primary key,
    mapping_version text not null,
    source_scheme   text not null,             -- 'lcls' | 'cat'
    source_code     text not null,
    purpose_label   text,                      -- 앱 PREFERENCE_LABEL 과 동일한 라벨. EXCLUDED/REVIEW 는 null 허용
    status          text not null default 'REVIEW',  -- MAPPED / REVIEW / EXCLUDED
    note            text,
    created_at      timestamptz not null default now(),
    unique (mapping_version, source_scheme, source_code)
);

-- 매핑 버전별로 배치 생성한 장소 x 추구미 파생 결과 (같은 데이터·규칙 = 같은 결과 추적용)
create table place_intent (
    content_id      text not null references place (content_id),
    purpose_label   text not null,
    mapping_version text not null,
    derived_at      timestamptz not null default now(),
    primary key (content_id, purpose_label, mapping_version)
);

-- 필드 단위 수동 보정. 한 필드의 확인이 장소 전체 검증으로 확대되지 않도록 근거·확인일을 필수로 남긴다
create table place_field_override (
    content_id  text not null,
    field       text not null,
    value       text,
    reason      text not null,
    verified_by text,
    verified_at timestamptz not null default now(),
    primary key (content_id, field)
);

-- 무장애 정보 (KorWithService2). status 판정은 파생 단계에서 하고 기본은 UNKNOWN 유지
create table place_accessibility (
    content_id text primary key,
    payload    jsonb,
    status     text not null default 'UNKNOWN',  -- ACCESSIBLE / LIMITED / UNKNOWN
    fetched_at timestamptz not null default now()
);

-- 수집 실행 단위
create table ingest_run (
    id             bigserial primary key,
    kind           text not null,               -- codes / area-list / details / accessibility
    params         jsonb,
    status         text not null default 'RUNNING',  -- RUNNING / DONE / FAILED
    api_calls      int  not null default 0,
    items_upserted int  not null default 0,
    error          text,
    started_at     timestamptz not null default now(),
    finished_at    timestamptz
);

-- 호출 실측 (Phase 3 가 요구하는 호출량·성공률·응답시간의 근거)
create table api_call_log (
    id          bigserial primary key,
    run_id      bigint references ingest_run (id),
    service     text not null,
    operation   text not null,
    params      jsonb,
    http_status int,
    result_code text,
    duration_ms int,
    called_at   timestamptz not null default now()
);
create index idx_api_call_log_run on api_call_log (run_id);
