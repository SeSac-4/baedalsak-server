-- 라이더당 진행 중 배달 1건 (6.2.2, N2.5) 을 DB 에서 보장한다.
-- 앱의 existsByRiderIdAndStatusIn 검사는 잠금 없는 SELECT 라, 같은 라이더가 서로 다른 배달을 동시에 수락하면
-- 둘 다 통과해 2건이 배정될 수 있었다. 일반 부분 인덱스를 같은 조건의 UNIQUE 부분 인덱스로 바꾼다.
-- 조회(6.2.2 검사, 6.4.2 /me/active)는 이 인덱스를 그대로 탄다.
-- 적용 전 위반 행이 있으면 생성이 실패한다:
--   SELECT rider_id, count(*) FROM delivery WHERE status IN ('ASSIGNED','PICKED_UP') GROUP BY rider_id HAVING count(*) > 1;
DROP INDEX IF EXISTS delivery_rider_active_idx;
CREATE UNIQUE INDEX delivery_rider_active_uq ON delivery (rider_id) WHERE status IN ('ASSIGNED', 'PICKED_UP');
