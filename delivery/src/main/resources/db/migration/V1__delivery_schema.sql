-- delivery 서비스 schema (설계서 '테이블 정의서' 탭 delivery_db)
-- 다른 서비스 ID(order_id, customer_id, owner_id, store_id, rider_id)는 값만 보관하고 FK 를 걸지 않는다.
-- 이미 적용(머지)된 뒤에는 이 파일을 고치지 말고 V2__... 를 추가한다 (docs 4-1).

-- 배달 요청. OrderAccepted 이벤트로 생성. 주소·가게·고객 정보는 페이로드 스냅샷
CREATE TABLE delivery (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id           BIGINT       NOT NULL,
    customer_id        BIGINT       NOT NULL,
    owner_id           BIGINT       NOT NULL,
    store_id           BIGINT       NOT NULL,
    store_name         VARCHAR(100) NOT NULL,
    pickup_address     VARCHAR(255) NOT NULL,
    delivery_address   VARCHAR(255) NOT NULL,
    delivery_dong      VARCHAR(50)  NOT NULL,
    customer_phone     VARCHAR(20)  NOT NULL,
    rider_request      VARCHAR(200),
    delivery_fee       INTEGER      NOT NULL,
    estimated_ready_at TIMESTAMPTZ  NOT NULL,
    rider_id           BIGINT,
    rider_name         VARCHAR(50),
    status             VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    assigned_at        TIMESTAMPTZ,
    picked_up_at       TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT delivery_order_uq UNIQUE (order_id),                 -- 주문당 1건 + 소비 멱등 (N2.4)
    CONSTRAINT delivery_fee_ck CHECK (delivery_fee >= 0),
    CONSTRAINT delivery_status_ck CHECK (status IN ('WAITING', 'ASSIGNED', 'PICKED_UP', 'DELIVERED')),
    CONSTRAINT delivery_assigned_ck CHECK (status = 'WAITING' OR (rider_id IS NOT NULL AND assigned_at IS NOT NULL)),
    CONSTRAINT delivery_picked_ck CHECK (status NOT IN ('PICKED_UP', 'DELIVERED') OR picked_up_at IS NOT NULL),
    CONSTRAINT delivery_completed_ck CHECK (status <> 'DELIVERED' OR completed_at IS NOT NULL)
);

-- 배차 대기 목록 (6.2.1, 오래된 순)
CREATE INDEX delivery_waiting_idx ON delivery (created_at) WHERE status = 'WAITING';
-- 라이더 진행 중 배달 (6.4.2)
CREATE INDEX delivery_rider_active_idx ON delivery (rider_id) WHERE status IN ('ASSIGNED', 'PICKED_UP');

-- 이벤트 발행 Outbox (상태 변경과 같은 트랜잭션에 INSERT, 발행기가 1초 폴링)
CREATE TABLE outbox (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     UUID        NOT NULL,
    event_type   VARCHAR(50) NOT NULL,
    routing_key  VARCHAR(50) NOT NULL,
    aggregate_id BIGINT      NOT NULL,
    payload      JSONB       NOT NULL,
    trace_id     VARCHAR(32),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,

    CONSTRAINT outbox_event_uq UNIQUE (event_id)
);

CREATE INDEX outbox_unpublished_idx ON outbox (id) WHERE published_at IS NULL;

-- 이벤트 소비 멱등 테이블 (같은 event_id 두 번 처리 방지)
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(50) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
