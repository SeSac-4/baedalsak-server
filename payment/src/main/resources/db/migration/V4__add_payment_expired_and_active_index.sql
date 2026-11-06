-- =============================================================================
-- payment V4: 결제 만료 상태 · 고객당 진행 중 결제 요청 1건 (#128)
--
-- 주문 · 결제 오케스트레이션 사가: 조정자(order)가 결제 요청을 지시하고, 결제창만 열고 끝난 요청은 만료로 정리한다.
-- 설계서 '테이블 정의서'(payment status · payment_customer_active_uq), '상태 전이'(REQUESTED → EXPIRED)
-- 주의: 머지 후 이 파일은 수정하지 않는다
-- =============================================================================

-- 결제 만료(EXPIRED) 추가. 환불 상태(CANCEL_REQUESTED · CANCELED)는 결제 취소 명령 처리(#127)에서 추가
ALTER TABLE payment DROP CONSTRAINT payment_status_ck;
ALTER TABLE payment ADD CONSTRAINT payment_status_ck CHECK (status IN ('REQUESTED', 'APPROVED', 'EXPIRED'));

-- 아래 인덱스를 만들기 전에, 한 고객에게 진행 중 결제 요청(REQUESTED)이 여러 건이면 가장 최근 1건만 남기고 만료로 바꾼다.
-- 지금까지는 '결제하기' 를 누를 때마다 결제 요청이 쌓일 수 있었다 (개발 DB). 승인된 결제(APPROVED)는 건드리지 않는다
UPDATE payment p
   SET status = 'EXPIRED'
 WHERE p.status = 'REQUESTED'
   AND EXISTS (SELECT 1
                 FROM payment n
                WHERE n.customer_id = p.customer_id
                  AND n.status = 'REQUESTED'
                  AND n.id > p.id);

-- 고객당 진행 중 결제 요청 1건 (두 탭 이중 결제 방지)
CREATE UNIQUE INDEX payment_customer_active_uq ON payment (customer_id) WHERE status = 'REQUESTED';
