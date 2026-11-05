-- 주문 · 결제 오케스트레이션 사가의 주문 상태 추가 (사가 결정 v2 13번)
--   PAYMENT_EXPIRED : 결제 요청 실패 · 승인 직전 마감/품절 · 45분 만료 · 늦은 승인 환불 (고객 · 사장님에게 안 보임)
--   REJECTED        : 사장님 거절 (PAID 에서만)
--   CANCELED        : 고객 취소 (수락 전 PAID 에서만)
-- 환불 진행 상태는 payment 만 기록하므로 orders 에 컬럼을 추가하지 않는다.
ALTER TABLE orders
    DROP CONSTRAINT orders_status_ck,
    ADD CONSTRAINT orders_status_ck CHECK (status IN (
        'PENDING_PAYMENT', 'PAID', 'COOKING', 'DELIVERING', 'DELIVERED',
        'PAYMENT_EXPIRED', 'REJECTED', 'CANCELED'));
