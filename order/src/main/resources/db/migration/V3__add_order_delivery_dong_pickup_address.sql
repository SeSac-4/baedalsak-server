-- OrderAccepted 페이로드의 deliveryDong·pickupAddress 를 위한 주문 스냅샷 (#30)
-- delivery 는 두 값을 NOT NULL 로 저장하고(delivery_dong·pickup_address), 배차 대기 목록에 동 단위 배달지·픽업지를 보여 준다(6.2.1).
-- 설계서 orders 에는 두 컬럼이 없어 주문 수락 → 배달 생성이 동작하지 않으므로 추가한다.
--   delivery_dong  : 주문서에서 주소와 함께 받는 배달지 동 (도로명 주소에서는 동을 뽑을 수 없음)
--   pickup_address : 주문 생성 때 store 내부 API 의 가게 주소 스냅샷 (수락 때 store 를 다시 부르지 않음)
-- 개발 DB 는 매일 비워지고(설계서 v5 N2.6) 주문 API 가 막 생긴 상태라 NOT NULL 로 바로 추가한다.
ALTER TABLE orders
    ADD COLUMN delivery_dong  VARCHAR(50)  NOT NULL,
    ADD COLUMN pickup_address VARCHAR(255) NOT NULL;
