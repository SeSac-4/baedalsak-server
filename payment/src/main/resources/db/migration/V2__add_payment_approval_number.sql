-- 결제 승인번호 (기능명세 5.4.1·화면 흐름 U-11, N7.5 "토스 승인번호만 보관")
-- 토스 결제 승인 응답의 card.approveNo (카드사 승인 번호, 최대 8자). 카드로 결제했을 때만 값이 있어 NULL 허용
ALTER TABLE payment ADD COLUMN approval_number VARCHAR(8);
