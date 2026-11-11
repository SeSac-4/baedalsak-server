-- 회원 탈퇴 (#188): soft delete. NULL 이면 활성 회원, 값이 있으면 탈퇴한 시각
-- 탈퇴한 회원은 JPA 조회 전체에서 빠진다 (Member 엔티티 @SQLRestriction)
ALTER TABLE member ADD COLUMN deleted_at TIMESTAMPTZ;
