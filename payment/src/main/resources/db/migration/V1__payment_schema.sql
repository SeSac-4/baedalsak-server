-- =============================================================================
-- payment 서비스 V1 스키마
--
-- payment        : 주문당 결제 1건. 결제 요청 금액과 토스 승인 결과 저장
-- payment_outbox : 결제 승인 이벤트(PaymentApproved)를 RabbitMQ 로 보내기 전에 저장
--
-- 참고: 엑셀 설계서 v2 '테이블 정의서'(payment_db), backend-infra-rules 4-1
-- 주의: 머지 후 이 파일은 수정하지 않는다. 변경은 V2 이후 파일로 추가
-- =============================================================================

-- payment: 주문당 결제 1건. 결제 요청 금액과 토스 승인 결과를 저장
CREATE TABLE payment (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id         BIGINT       NOT NULL,
    customer_id      BIGINT       NOT NULL,
    toss_order_id    VARCHAR(64)  NOT NULL,
    amount           INTEGER      NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    toss_payment_key VARCHAR(200),
    method           VARCHAR(30),
    approved_at      TIMESTAMPTZ,
    receipt_url      VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT payment_order_id_uq      UNIQUE (order_id),
    CONSTRAINT payment_toss_order_id_uq UNIQUE (toss_order_id),
    CONSTRAINT payment_toss_key_uq      UNIQUE (toss_payment_key),
    CONSTRAINT payment_amount_ck        CHECK (amount > 0),
    CONSTRAINT payment_status_ck        CHECK (status IN ('REQUESTED', 'APPROVED')),
    CONSTRAINT payment_approved_ck      CHECK (
        status <> 'APPROVED' OR (toss_payment_key IS NOT NULL AND approved_at IS NOT NULL))
);

-- 내 결제 목록 조회: 전체 결제, 생성 시각 최신순
CREATE INDEX payment_customer_idx ON payment (customer_id, created_at DESC);

-- payment_outbox: 결제 승인 이벤트를 RabbitMQ 로 보내기 전에 저장 (order_outbox 와 같은 구조)
CREATE TABLE payment_outbox (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     UUID        NOT NULL UNIQUE,
    event_type   VARCHAR(50) NOT NULL,
    routing_key  VARCHAR(50) NOT NULL,
    aggregate_id BIGINT      NOT NULL,
    payload      JSONB       NOT NULL,
    trace_id     VARCHAR(32),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

-- 발행기가 아직 보내지 않은 행만 빠르게 찾기
CREATE INDEX payment_outbox_unpublished_idx ON payment_outbox (id) WHERE published_at IS NULL;