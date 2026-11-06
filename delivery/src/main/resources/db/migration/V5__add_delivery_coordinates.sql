-- 픽업(가게) · 배달지 좌표 (배달실시간초안 4번). 카카오 로컬 API 로 주소를 변환해 채운다
-- 변환이 실패할 수 있고 이미 만들어진 배달 행에는 값이 없어 NULL 허용. 위도 · 경도는 둘 다 있거나 둘 다 없다
-- 경로(route_path 등)는 픽업 시 길찾기 작업에서 따로 추가한다
ALTER TABLE delivery
    ADD COLUMN pickup_lat   DOUBLE PRECISION,
    ADD COLUMN pickup_lng   DOUBLE PRECISION,
    ADD COLUMN delivery_lat DOUBLE PRECISION,
    ADD COLUMN delivery_lng DOUBLE PRECISION,
    ADD CONSTRAINT delivery_pickup_point_ck CHECK ((pickup_lat IS NULL) = (pickup_lng IS NULL)),
    ADD CONSTRAINT delivery_delivery_point_ck CHECK ((delivery_lat IS NULL) = (delivery_lng IS NULL));
