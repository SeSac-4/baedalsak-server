-- 라이더 현재 위치 → 가게 경로 (#193). 배차 수락 직후 카카오 길찾기로 한 번 채운다
-- approach_path: [{"lat":..,"lng":..}, ...] (라이더 → 가게 순서). 길찾기 실패 시 두 점 직선
-- 배차 전 배달 · 이전 행은 값이 없어 NULL 허용. 세 컬럼은 같이 채워진다
ALTER TABLE delivery
    ADD COLUMN approach_path       JSONB,
    ADD COLUMN approach_distance_m INTEGER,
    ADD COLUMN approach_duration_s INTEGER,
    ADD CONSTRAINT delivery_approach_route_ck CHECK (
        (approach_path IS NULL AND approach_distance_m IS NULL AND approach_duration_s IS NULL)
        OR (approach_path IS NOT NULL AND approach_distance_m >= 0 AND approach_duration_s >= 0));
