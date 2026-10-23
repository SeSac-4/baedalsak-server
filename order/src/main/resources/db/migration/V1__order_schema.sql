-- order 서비스 schema (설계서 v4 '테이블 정의서' 탭 order_db)
-- 다른 서비스 ID(customer_id, store_id, owner_id, menu_id)는 값만 보관하고 FK 를 걸지 않는다.
-- 이미 적용(머지)된 뒤에는 이 파일을 고치지 말고 V2__... 를 추가한다 (docs 4-1).

-- 장바구니. 고객당 1개, 한 가게 메뉴만 (3.1.1)
CREATE TABLE cart (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT      NOT NULL,
    store_id    BIGINT      NOT NULL,                        -- 담긴 메뉴의 가게. 다른 가게 메뉴를 담으면 비우고 교체
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT cart_customer_uq UNIQUE (customer_id)
);

-- 장바구니 항목. 메뉴명·가격은 담을 때 스냅샷 (조회 시 store 호출 없음)
CREATE TABLE cart_item (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id   BIGINT       NOT NULL REFERENCES cart (id) ON DELETE CASCADE,
    menu_id   BIGINT       NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    price     INTEGER      NOT NULL,                         -- 주문 생성 시 store 에 재검증
    quantity  INTEGER      NOT NULL,

    CONSTRAINT cart_item_menu_uq UNIQUE (cart_id, menu_id),
    CONSTRAINT cart_item_price_ck CHECK (price > 0),
    CONSTRAINT cart_item_quantity_ck CHECK (quantity > 0)
);

-- 주문 (order 는 예약어라 복수형). 가게·고객 정보와 메일 수신자 이메일(고객·사장님)은 생성 시 스냅샷
CREATE TABLE orders (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id        BIGINT       NOT NULL,
    store_id           BIGINT       NOT NULL,
    owner_id           BIGINT       NOT NULL,                -- 수락 권한 검증, 사장님 이메일 조회 키
    store_name         VARCHAR(100) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING_PAYMENT',
    delivery_address   VARCHAR(255) NOT NULL,
    customer_phone     VARCHAR(20)  NOT NULL,                -- OrderAccepted 페이로드로 delivery 에 전달
    customer_email     VARCHAR(255) NOT NULL,                -- 메일 수신자 (이벤트 customerEmail)
    owner_email        VARCHAR(255) NOT NULL,                -- 메일 수신자 (이벤트 ownerEmail)
    store_request      VARCHAR(200),
    rider_request      VARCHAR(200),
    items_amount       INTEGER      NOT NULL,                -- Σ(price × quantity), 서버 계산
    delivery_fee       INTEGER      NOT NULL,
    total_amount       INTEGER      NOT NULL,
    estimated_ready_at TIMESTAMPTZ,                          -- 수락 시 now() + 조리 예상 분
    rider_name         VARCHAR(50),                          -- DeliveryAssigned 수신 시 저장
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT orders_status_ck CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'COOKING', 'DELIVERING', 'DELIVERED')),
    CONSTRAINT orders_items_amount_ck CHECK (items_amount >= 0),
    CONSTRAINT orders_delivery_fee_ck CHECK (delivery_fee >= 0),
    CONSTRAINT orders_total_ck CHECK (total_amount = items_amount + delivery_fee)
);

CREATE INDEX orders_store_status_idx ON orders (store_id, status, created_at DESC);
CREATE INDEX orders_customer_idx ON orders (customer_id, created_at DESC);

-- 주문 항목 스냅샷
CREATE TABLE order_item (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id  BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    menu_id   BIGINT       NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    price     INTEGER      NOT NULL,                         -- 주문 시점 단가
    quantity  INTEGER      NOT NULL,

    CONSTRAINT order_item_price_ck CHECK (price > 0),
    CONSTRAINT order_item_quantity_ck CHECK (quantity > 0)
);

CREATE INDEX order_item_order_idx ON order_item (order_id);

-- 이벤트 발행 Outbox. 상태 변경과 같은 트랜잭션에 INSERT, 발행기가 1초 폴링 (docs 3-2·3-3)
CREATE TABLE outbox (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     UUID        NOT NULL,                       -- RabbitMQ messageId, 구독자 멱등 키
    event_type   VARCHAR(50) NOT NULL,                       -- OrderAccepted 등
    routing_key  VARCHAR(50) NOT NULL,                       -- order.accepted 등
    aggregate_id BIGINT      NOT NULL,                       -- orderId
    payload      JSONB       NOT NULL,
    trace_id     VARCHAR(32),                                -- 분산 추적 ID (N5.2)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,                                -- NULL 이면 미발행

    CONSTRAINT outbox_event_uq UNIQUE (event_id)
);

CREATE INDEX outbox_unpublished_idx ON outbox (id) WHERE published_at IS NULL;

-- 이벤트 소비 멱등 (같은 event_id 두 번 처리 방지)
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(50) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 메일 발송 기록. 이벤트 1건 × 수신자 1명 = 1행 (docs 10-4)
CREATE TABLE mail_log (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   UUID         NOT NULL,
    recipient  VARCHAR(255) NOT NULL,                        -- 이벤트 페이로드 customerEmail·ownerEmail
    status     VARCHAR(10)  NOT NULL DEFAULT 'SENDING',      -- 선점 시 SENDING, SES 발송 성공 시 SENT
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at    TIMESTAMPTZ,

    CONSTRAINT mail_log_event_recipient_uq UNIQUE (event_id, recipient),
    CONSTRAINT mail_log_status_ck CHECK (status IN ('SENDING', 'SENT'))
);
