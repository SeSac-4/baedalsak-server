-- 장바구니 조회(GET /cart) 응답의 가게명·배달비·최소주문금액을 위한 가게 스냅샷 (#28)
-- 설계서는 조회 시 다른 서비스 동기 호출을 금지하므로(서비스 경계 원칙), 담을 때 store 내부 API 값을 저장한다.
-- 담을 때마다 최신 값으로 갱신하고, 주문 생성 때는 store 에 다시 검증한다 (4.1.1).
-- cart 를 쓰는 API 가 이 마이그레이션과 함께 처음 생기므로 기존 행이 없다 → NOT NULL 로 바로 추가
ALTER TABLE cart
    ADD COLUMN store_name       VARCHAR(100) NOT NULL,
    ADD COLUMN delivery_fee     INTEGER      NOT NULL,
    ADD COLUMN min_order_amount INTEGER      NOT NULL,
    ADD CONSTRAINT cart_delivery_fee_ck CHECK (delivery_fee >= 0),
    ADD CONSTRAINT cart_min_order_amount_ck CHECK (min_order_amount >= 0);
