-- 라이더 운송수단 스냅샷. 배차 수락 때 member 에서 받아 채운다 (가상 GPS 속도 · 도착 예정 시각)
-- 수락 전 배달 · 이전 행 · member 호출 실패는 NULL → 오토바이로 본다
ALTER TABLE delivery
    ADD COLUMN vehicle_type VARCHAR(10),
    ADD CONSTRAINT delivery_vehicle_type_ck CHECK (vehicle_type IN ('WALK', 'BICYCLE', 'MOTORCYCLE'));
