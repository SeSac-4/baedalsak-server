-- 개발용 seed 배달 5건 삭제 (#224). seed 파일(R__delivery_seed)은 #219 에서 지웠지만 이미 들어간 행이 남아 있었다
-- order 서비스에는 없는 주문(order_id 9900001~9900005)이라 다른 서비스 데이터와 섞이지 않는다
-- V7 · V9 는 이전에 만들었다가 되돌린 번호라 건너뛴다 (운영 이력에 남아 있을 수 있음)

-- 시드 배달을 수락 · 픽업하며 쌓인 이벤트 (발행 여부와 관계없이 삭제)
DELETE FROM outbox
 WHERE aggregate_id IN (SELECT id FROM delivery WHERE order_id BETWEEN 9900001 AND 9900005);

DELETE FROM delivery
 WHERE order_id BETWEEN 9900001 AND 9900005;
