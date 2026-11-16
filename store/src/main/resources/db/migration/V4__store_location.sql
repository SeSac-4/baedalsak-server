-- 가게 좌표 (#210). 고객 위치 기준 반경 안 가게만 목록에 보여 준다.
-- 가게 등록 · 수정 때 주소를 카카오 로컬 API 로 변환해 저장한다. 변환 전이거나 실패하면 NULL (반경 조회에서 제외)
ALTER TABLE store
    ADD COLUMN latitude  DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;

-- 반경 조회의 1단계(위도 · 경도 범위)용
CREATE INDEX store_location_idx ON store (latitude, longitude);
