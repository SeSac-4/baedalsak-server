-- 메일 발송 기록 (테이블 정의서 mail_log, order·payment·delivery 동일 구조)
-- 이벤트 1건 × 수신자 1명 = 1행. 재전달 시 SENT면 건너뛰고, SENDING이면 다시 발송(최대 1회 중복 허용)
CREATE TABLE mail_log (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   UUID         NOT NULL,                     -- 소비한 이벤트 ID (RabbitMQ messageId)
    recipient  VARCHAR(255) NOT NULL,                     -- 수신자 이메일 (페이로드 customerEmail·ownerEmail)
    status     VARCHAR(10)  NOT NULL DEFAULT 'SENDING',   -- 선점 시 SENDING, SES 발송 성공 시 SENT
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),       -- 선점 시각
    sent_at    TIMESTAMPTZ,                               -- 발송 완료 시각
    CONSTRAINT mail_log_status_ck CHECK (status IN ('SENDING', 'SENT')),
    CONSTRAINT mail_log_event_recipient_uq UNIQUE (event_id, recipient)
);
