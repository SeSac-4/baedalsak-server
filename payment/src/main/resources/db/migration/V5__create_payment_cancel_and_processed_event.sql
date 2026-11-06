-- =============================================================================
-- payment V5: 결제 취소(환불) 명령 처리 (#127)
--
-- 주문 · 결제 오케스트레이션 사가: 조정자(order)가 결제 취소 명령 CancelPayment 를 보내면 payment 가 토스 전액 취소 후
-- PaymentCanceled / PaymentCancelFailed 로 응답한다. 토스 호출 전에 CANCEL_REQUESTED 로 저장해 결과를 모르는 환불도 DB 에 남긴다.
-- 설계서 '테이블 정의서'(payment status · payment_cancel · processed_event), '상태 전이'(CANCEL_REQUESTED · CANCELED · payment_cancel)
-- 주의: 머지 후 이 파일은 수정하지 않는다
-- =============================================================================

-- 환불 처리 중(CANCEL_REQUESTED) · 환불 완료(CANCELED) 추가
ALTER TABLE payment DROP CONSTRAINT payment_status_ck;
ALTER TABLE payment ADD CONSTRAINT payment_status_ck
    CHECK (status IN ('REQUESTED', 'APPROVED', 'CANCEL_REQUESTED', 'CANCELED', 'EXPIRED'));

-- 결제 취소 요청 1건 = 1행. 지금은 전액 취소만, 이후 부분 취소 확장을 위해 payment 와 분리
-- payment.status 는 결제 한 건의 상태, payment_cancel.status 는 취소 요청 한 건의 진행 상태 (같은 트랜잭션에서 함께 갱신)
CREATE TABLE payment_cancel (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id           BIGINT       NOT NULL REFERENCES payment (id),    -- 취소 대상 결제
    amount               INTEGER      NOT NULL,                            -- 취소 금액 (지금은 전액)
    reason               VARCHAR(200) NOT NULL,                            -- 토스 cancelReason (200자, 넘으면 자름)
    requested_by         VARCHAR(10)  NOT NULL,                            -- 취소를 요청한 쪽 (CancelPayment requestedBy)
    idempotency_key      VARCHAR(64)  NOT NULL,                            -- 토스 취소 멱등 키 (UUID, 재시도 때 같은 값)
    toss_transaction_key VARCHAR(64),                                      -- 취소 성공 시 토스 거래 키
    status               VARCHAR(10)  NOT NULL DEFAULT 'PENDING',          -- 취소 진행 중 → 취소 완료 / 취소 실패
    attempt_count        INTEGER      NOT NULL DEFAULT 0,                  -- 토스 취소 호출 횟수
    requested_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    canceled_at          TIMESTAMPTZ,                                      -- 토스 취소 시각
    CONSTRAINT payment_cancel_requested_by_ck CHECK (requested_by IN ('OWNER', 'CUSTOMER', 'SYSTEM')),
    CONSTRAINT payment_cancel_status_ck CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    CONSTRAINT payment_cancel_idempotency_key_uq UNIQUE (idempotency_key)
);

-- 진행 중 취소는 결제당 1건 (같은 결제에 명령이 동시에 와도 PENDING 행은 하나만)
CREATE UNIQUE INDEX payment_cancel_pending_uq ON payment_cancel (payment_id) WHERE status = 'PENDING';

-- 명령 소비 멱등 (order · delivery 의 processed_event 와 같은 구조)
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,                       -- 소비한 메시지 ID (messageId)
    event_type   VARCHAR(50) NOT NULL,                   -- 메시지 종류 (CancelPayment)
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
