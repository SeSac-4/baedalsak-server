-- 조정자 점검 (사가 결정 3-3). 1분마다 도는 조회가 orders · outbox 전체를 훑지 않게 한다
-- 결제대기 주문
CREATE INDEX orders_pending_payment_idx ON orders (id) WHERE status = 'PENDING_PAYMENT';
-- 환불 확인 대상 (최근 24시간)
CREATE INDEX orders_refund_check_idx ON orders (updated_at)
    WHERE status IN ('PAYMENT_EXPIRED', 'REJECTED', 'CANCELED');
-- 주문별 환불 명령 재전송 판단
CREATE INDEX outbox_cancel_payment_idx ON outbox (aggregate_id) WHERE event_type = 'CancelPayment';
