-- =============================================================================
-- delivery 개발용 seed 배달 5건 — 지도(가게 · 배달지 핀, 경로선, 라이더 위치)를 바로 볼 수 있게
--   개발 환경에서만 실행: SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
--   운영은 기본값(db/migration 만)이라 실행되지 않는다.
--
--   R__ (반복 마이그레이션): 이 파일 내용이 바뀌면 다시 실행된다 → 여러 번 실행돼도 안전하게 작성한다.
--     ON CONFLICT DO NOTHING: 같은 order_id 가 있거나, 그 라이더에게 이미 진행 중 배달이 있으면 건너뜀
--     (delivery_order_uq · delivery_rider_active_uq). 테스트하다 상태가 바뀐 행은 되돌리지 않는다
--
--   좌표는 카카오 로컬 API 로 미리 변환한 값 (카카오 키 없이도 핀이 뜸)
--   경로 · 라이더 위치는 넣지 않는다 — 라이더가 픽업하면 그때 카카오 길찾기 + 가상 GPS 가 시작된다
--     (PICKED_UP 으로 넣으면 가상 GPS 시작 신호가 없어 위치가 계속 비어 있음)
--
--   id (다른 서비스 값, FK 없음)
--     order_id   9900001~9900005 — order 서비스에는 없는 주문 (배달 이벤트는 order 가 "unknown order" 로그만 남김)
--                                 → 고객 · 사장님 주문 화면에는 안 보이고 라이더 화면에서만 보인다
--     customer   1 · 2 = 시드고객1 · 2, owner 101 · 102 = 시드사장님1(시드분식) · 2(시드치킨) — member · store seed
--     rider      201 · 202 = 시드라이더1 · 2
--     store_id   0 — store seed 는 가게 id 를 고정하지 않아 알 수 없음 (배달에서는 표시에 쓰지 않음)
--     이메일은 비움 → 배달 메일을 보내지 않음
-- =============================================================================

INSERT INTO delivery (order_id, customer_id, owner_id, store_id, store_name, pickup_address,
                      delivery_address, delivery_dong, customer_phone, rider_request, delivery_fee,
                      estimated_ready_at, status, rider_id, rider_name, assigned_at,
                      pickup_lat, pickup_lng, delivery_lat, delivery_lng)
VALUES
    -- 진행 중 (라이더 /me/active) — 로그인하면 바로 지도, 픽업 누르면 경로선 · 위치 이동
    (9900001, 1, 101, 0, '시드분식', '서울특별시 관악구 관악로 105',
     '서울특별시 관악구 봉천로 456, 302호', '봉천동', '010-1000-0001', '문 앞에 두고 벨 눌러 주세요', 2500,
     now() + interval '10 minutes', 'ASSIGNED', 201, '시드라이더1', now(),
     37.4746296, 126.9525993, 37.4821054, 126.9504877),
    (9900002, 2, 102, 0, '시드치킨', '서울특별시 마포구 독막로 152',
     '서울특별시 마포구 양화로 160, 5층', '동교동', '010-1000-0002', NULL, 3000,
     now() + interval '10 minutes', 'ASSIGNED', 202, '시드라이더2', now(),
     37.5473724, 126.9304584, 37.5568904, 126.9236743),

    -- 배차 대기 (라이더 /waiting) — 수락하면 위 흐름과 같음
    (9900003, 1, 101, 0, '시드분식', '서울특별시 관악구 관악로 105',
     '서울특별시 관악구 남부순환로 1820, 1201호', '봉천동', '010-1000-0001', '경비실에 맡겨 주세요', 2500,
     now() + interval '15 minutes', 'WAITING', NULL, NULL, NULL,
     37.4746296, 126.9525993, 37.4809480, 126.9520838),
    (9900004, 2, 102, 0, '시드치킨', '서울특별시 마포구 독막로 152',
     '서울특별시 마포구 와우산로 94, 2층', '상수동', '010-1000-0002', NULL, 3000,
     now() + interval '15 minutes', 'WAITING', NULL, NULL, NULL,
     37.5473724, 126.9304584, 37.5525246, 126.9250355),
    (9900005, 1, 312, 0, '소문난제육볶음', '서울특별시 성동구 왕십리로 66',
     '서울특별시 성동구 성수이로 77, 302호', '성수동2가', '010-1000-0001', '문 앞', 2000,
     now() + interval '20 minutes', 'WAITING', NULL, NULL, NULL,
     37.5434788, 127.0450042, 37.5419942, 127.0559955)
ON CONFLICT DO NOTHING;
