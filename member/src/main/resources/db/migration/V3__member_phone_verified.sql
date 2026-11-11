-- 휴대폰 번호 인증 시각 (#164). NULL = 인증 안 함 (선택 기능이라 기존 회원은 NULL 그대로)
-- 인증한 번호로만 배달 알림 문자를 보낸다 (#182). 번호를 바꾸면 다시 NULL
ALTER TABLE member ADD COLUMN phone_verified_at TIMESTAMPTZ;