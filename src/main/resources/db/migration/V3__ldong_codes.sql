-- TourAPI 4.0 은 법정동 코드(lDong) 체계로 이관 중 — 구 areaCode 필터로는 콘텐츠 일부만 조회된다.
-- (실측: 서울 쇼핑 areaCode=145곳 vs lDongRegnCd=4,357곳)
alter table place add column l_dong_regn_cd text;
alter table place add column l_dong_signgu_cd text;
create index idx_place_ldong on place (l_dong_regn_cd, l_dong_signgu_cd);
