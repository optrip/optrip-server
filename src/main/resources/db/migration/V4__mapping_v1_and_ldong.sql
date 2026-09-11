-- 매핑 v1: v0-draft 확정분 승계 + 팀 결정(9/7) 반영
--   바다·하이킹/트레킹: 세분류(lcls3) 기반 활성화
--   힐링: 공원·자연생태 확장
--   감성/사진·야경: 1차 제외 (후속 확장)
insert into taxonomy_mapping (mapping_version, source_scheme, source_code, purpose_label, status, note)
select 'v1', 'lcls2', source_code, purpose_label, status, note
from taxonomy_mapping
where mapping_version = 'v0-draft' and status = 'MAPPED';

insert into taxonomy_mapping (mapping_version, source_scheme, source_code, purpose_label, status, note) values
('v1', 'lcls3', 'NA020900', '바다', 'MAPPED', '해변·해수욕장'),
('v1', 'lcls3', 'NA020700', '바다', 'MAPPED', '항구/포구'),
('v1', 'lcls3', 'NA020500', '바다', 'MAPPED', '섬'),
('v1', 'lcls3', 'NA020800', '바다', 'MAPPED', '해안절경'),
('v1', 'lcls3', 'NA010100', '하이킹/트레킹', 'MAPPED', '산·고개·오름·봉우리'),
('v1', 'lcls3', 'NA010200', '하이킹/트레킹', 'MAPPED', '숲'),
('v1', 'lcls2', 'VE03', '힐링', 'MAPPED', '도시공원'),
('v1', 'lcls2', 'NA04', '힐링', 'MAPPED', '자연공원'),
('v1', 'lcls2', 'NA03', '힐링', 'MAPPED', '자연생태');

create table ldong_code (
    parent_code text not null default '',
    code        text not null,
    name        text not null,
    fetched_at  timestamptz not null default now(),
    primary key (parent_code, code)
);
