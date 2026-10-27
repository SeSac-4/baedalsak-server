-- 메일 수신자 이메일 스냅샷 (설계서 '테이블 정의서' 탭 delivery_db.customer_email·owner_email)
-- OrderAccepted 페이로드 값을 저장해 두고, 배달 이벤트(DeliveryAssigned·PickedUp·Completed) 페이로드에 실어 보낸다.
-- 설계서는 NOT NULL 이지만, 이미 만들어진 배달 행에는 채울 값이 없어 NULL 허용으로 추가한다.
ALTER TABLE delivery
    ADD COLUMN customer_email VARCHAR(255),
    ADD COLUMN owner_email    VARCHAR(255);
