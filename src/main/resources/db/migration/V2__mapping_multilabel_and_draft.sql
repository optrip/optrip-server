-- 한 분류코드가 여러 추구미로 연결될 수 있게 제약 완화
-- (예: 자연경관(산) → 자연/풍경 + 하이킹/트레킹)
alter table taxonomy_mapping
    drop constraint taxonomy_mapping_mapping_version_source_scheme_source_code_key;
alter table taxonomy_mapping
    add constraint uq_taxonomy_mapping unique (mapping_version, source_scheme, source_code, purpose_label);

-- ──────────────────────────────────────────────
-- Phase 2 매핑 초안 (mapping_version = 'v0-draft')
-- 신분류 depth2(59개) → 추구미 12 라벨 (RecommendService.PURPOSE_TO_CATEGORY 와 동일 라벨)
-- 원칙: 분류명만으로 확실한 것만 MAPPED, 애매하면 REVIEW, 추천 대상 아니면 EXCLUDED.
-- 팀 검토 후 승인된 버전을 새 mapping_version 으로 발행한다 (초안을 덮어쓰지 않는다).
-- ──────────────────────────────────────────────
insert into taxonomy_mapping (mapping_version, source_scheme, source_code, purpose_label, status, note) values
-- 숙박(AC): 현재 수집·추천 대상 아님
('v0-draft', 'lcls', 'AC01', null, 'EXCLUDED', '숙박은 추천 후보 아님'),
('v0-draft', 'lcls', 'AC02', null, 'EXCLUDED', '숙박은 추천 후보 아님'),
('v0-draft', 'lcls', 'AC03', null, 'EXCLUDED', '숙박은 추천 후보 아님'),
('v0-draft', 'lcls', 'AC04', null, 'EXCLUDED', '숙박은 추천 후보 아님'),
('v0-draft', 'lcls', 'AC05', null, 'EXCLUDED', '숙박은 추천 후보 아님. 캠핑 수요 생기면 재검토'),
('v0-draft', 'lcls', 'AC06', null, 'EXCLUDED', '숙박은 추천 후보 아님'),
-- 추천코스(C01xx): 관광공사가 만든 완성 코스 → OPTRIP 자체 조립과 역할 중복
('v0-draft', 'lcls', 'C0112', null, 'EXCLUDED', '완성 코스 콘텐츠는 장소 추천 후보 아님'),
('v0-draft', 'lcls', 'C0113', null, 'EXCLUDED', '완성 코스 콘텐츠는 장소 추천 후보 아님'),
('v0-draft', 'lcls', 'C0114', null, 'EXCLUDED', '완성 코스 콘텐츠는 장소 추천 후보 아님'),
('v0-draft', 'lcls', 'C0115', null, 'EXCLUDED', '완성 코스 콘텐츠는 장소 추천 후보 아님'),
('v0-draft', 'lcls', 'C0116', null, 'EXCLUDED', '완성 코스 콘텐츠는 장소 추천 후보 아님'),
('v0-draft', 'lcls', 'C0117', null, 'EXCLUDED', '완성 코스 콘텐츠는 장소 추천 후보 아님'),
-- 축제/공연/행사(EV): 장소가 아니라 기간형 이벤트 → 날짜 필터 설계 후 결정
('v0-draft', 'lcls', 'EV01', null, 'REVIEW', '기간형 이벤트. 여행일 필터와 함께 별도 처리 필요'),
('v0-draft', 'lcls', 'EV02', null, 'REVIEW', '기간형 이벤트. 여행일 필터와 함께 별도 처리 필요'),
('v0-draft', 'lcls', 'EV03', null, 'REVIEW', '기간형 이벤트. 여행일 필터와 함께 별도 처리 필요'),
-- 체험관광(EX)
('v0-draft', 'lcls', 'EX01', '문화체험', 'MAPPED', '전통체험'),
('v0-draft', 'lcls', 'EX02', '문화체험', 'MAPPED', '공예체험'),
('v0-draft', 'lcls', 'EX03', '문화체험', 'MAPPED', '농산어촌 체험'),
('v0-draft', 'lcls', 'EX04', '문화체험', 'MAPPED', '산사체험'),
('v0-draft', 'lcls', 'EX04', '힐링', 'REVIEW', '템플스테이류는 힐링 성격도 있음'),
('v0-draft', 'lcls', 'EX05', '힐링', 'MAPPED', '웰니스관광'),
('v0-draft', 'lcls', 'EX06', null, 'REVIEW', '산업관광은 12개 추구미와 직접 대응 없음'),
('v0-draft', 'lcls', 'EX07', '문화체험', 'REVIEW', '기타체험 — 범위가 넓어 확인 필요'),
-- 음식(FD)
('v0-draft', 'lcls', 'FD01', '맛집', 'MAPPED', '한식'),
('v0-draft', 'lcls', 'FD02', '맛집', 'MAPPED', '외국식'),
('v0-draft', 'lcls', 'FD03', '시장/먹거리', 'REVIEW', '간이음식(분식 등) — 시장/먹거리와 맛집 사이 애매'),
('v0-draft', 'lcls', 'FD04', null, 'REVIEW', '주점 — 추구미와 직접 대응 없음'),
('v0-draft', 'lcls', 'FD05', '카페투어', 'MAPPED', '카페/찻집'),
-- 역사관광(HS)
('v0-draft', 'lcls', 'HS01', '역사/문화', 'MAPPED', '역사유적지'),
('v0-draft', 'lcls', 'HS02', '역사/문화', 'MAPPED', '역사유물'),
('v0-draft', 'lcls', 'HS03', '역사/문화', 'MAPPED', '종교성지'),
('v0-draft', 'lcls', 'HS04', '역사/문화', 'REVIEW', '안보관광지 — 성격 확인 필요'),
-- 레저스포츠(LS)
('v0-draft', 'lcls', 'LS01', '액티비티', 'MAPPED', '육상레저'),
('v0-draft', 'lcls', 'LS02', '액티비티', 'MAPPED', '수상레저'),
('v0-draft', 'lcls', 'LS02', '바다', 'REVIEW', '수상레저는 바다 추구미와 겹칠 수 있음 (하천 제외 확인 필요)'),
('v0-draft', 'lcls', 'LS03', '액티비티', 'MAPPED', '항공레저'),
('v0-draft', 'lcls', 'LS04', '액티비티', 'MAPPED', '복합레저'),
-- 자연관광(NA)
('v0-draft', 'lcls', 'NA01', '자연/풍경', 'MAPPED', '자연경관(산)'),
('v0-draft', 'lcls', 'NA01', '하이킹/트레킹', 'REVIEW', '산이라고 모두 트레킹 가능하지 않음 — depth3 확인 필요'),
('v0-draft', 'lcls', 'NA02', '자연/풍경', 'MAPPED', '자연경관(하천·해양)'),
('v0-draft', 'lcls', 'NA02', '바다', 'REVIEW', '하천 포함 분류라 바다 확정 불가 — depth3(해수욕장 등)으로 분리 필요'),
('v0-draft', 'lcls', 'NA03', '자연/풍경', 'MAPPED', '자연생태'),
('v0-draft', 'lcls', 'NA03', '힐링', 'REVIEW', '수목원·휴양림류는 힐링 성격'),
('v0-draft', 'lcls', 'NA04', '자연/풍경', 'MAPPED', '자연공원'),
('v0-draft', 'lcls', 'NA04', '힐링', 'REVIEW', '공원 산책 = 힐링 가설'),
('v0-draft', 'lcls', 'NA05', '자연/풍경', 'REVIEW', '기타자연 — 범위 확인 필요'),
-- 쇼핑(SH): 시장만 추구미와 직접 연결
('v0-draft', 'lcls', 'SH01', null, 'EXCLUDED', '백화점'),
('v0-draft', 'lcls', 'SH02', null, 'EXCLUDED', '쇼핑몰'),
('v0-draft', 'lcls', 'SH03', null, 'EXCLUDED', '대형마트'),
('v0-draft', 'lcls', 'SH04', null, 'EXCLUDED', '면세점'),
('v0-draft', 'lcls', 'SH05', null, 'REVIEW', '전문매장/상가 — 공방거리 등 관광성 상가 포함 가능'),
('v0-draft', 'lcls', 'SH06', '시장/먹거리', 'MAPPED', '시장'),
('v0-draft', 'lcls', 'SH07', null, 'EXCLUDED', '기타쇼핑'),
-- 문화관광(VE)
('v0-draft', 'lcls', 'VE01', '감성/사진', 'REVIEW', '랜드마크 — 사진 명소 가설, 데이터 신호 확인 필요'),
('v0-draft', 'lcls', 'VE01', '야경', 'REVIEW', '랜드마크 야경 가설 — 분류만으로 확정 불가'),
('v0-draft', 'lcls', 'VE02', '액티비티', 'REVIEW', '테마공원'),
('v0-draft', 'lcls', 'VE03', '힐링', 'REVIEW', '도시공원 산책 = 힐링 가설'),
('v0-draft', 'lcls', 'VE04', '문화체험', 'REVIEW', '도시·지역문화관광 — 역사/문화와 경계 애매'),
('v0-draft', 'lcls', 'VE05', null, 'REVIEW', '복합관광시설 — 범위 넓음'),
('v0-draft', 'lcls', 'VE06', '문화체험', 'REVIEW', '공연시설 — 상시 관람 가능 여부 확인 필요'),
('v0-draft', 'lcls', 'VE07', '문화체험', 'MAPPED', '전시시설(박물관·미술관)'),
('v0-draft', 'lcls', 'VE08', null, 'REVIEW', '행사시설'),
('v0-draft', 'lcls', 'VE09', null, 'REVIEW', '교육시설'),
('v0-draft', 'lcls', 'VE10', '액티비티', 'MAPPED', '레저스포츠시설'),
('v0-draft', 'lcls', 'VE11', null, 'EXCLUDED', '교통시설'),
('v0-draft', 'lcls', 'VE12', null, 'REVIEW', '기타문화관광지');
