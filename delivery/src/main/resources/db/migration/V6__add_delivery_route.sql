-- 가게 → 배달지 경로 (배달실시간초안 4 · 6번). 픽업 직후 카카오 길찾기로 한 번 채운다
-- route_path: [{"lat":..,"lng":..}, ...] (가게 → 배달지 순서). 길찾기 실패 시 두 점 직선
-- 픽업 전 배달 · 이전 행은 값이 없어 NULL 허용. 세 컬럼은 같이 채워진다
ALTER TABLE delivery
    ADD COLUMN route_path       JSONB,
    ADD COLUMN route_distance_m INTEGER,
    ADD COLUMN route_duration_s INTEGER,
    ADD CONSTRAINT delivery_route_ck CHECK (
        (route_path IS NULL AND route_distance_m IS NULL AND route_duration_s IS NULL)
        OR (route_path IS NOT NULL AND route_distance_m >= 0 AND route_duration_s >= 0));
