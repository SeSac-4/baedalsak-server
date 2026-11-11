-- 가게 폐업 (#176). 주문 이력이 가게를 참조하므로 행은 지우지 않고 soft delete 한다.
-- NULL 이면 운영 중
ALTER TABLE store ADD COLUMN deleted_at TIMESTAMPTZ;

-- 사장님 1명 = 가게 1개는 폐업하지 않은 가게끼리만 → 폐업한 사장님은 새 가게를 다시 등록할 수 있다
ALTER TABLE store DROP CONSTRAINT store_owner_uq;
CREATE UNIQUE INDEX store_owner_active_uq ON store (owner_id) WHERE deleted_at IS NULL;
