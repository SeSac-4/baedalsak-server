-- 주문·배달 알림 문자 발송 기록 (#182). 같은 이벤트로 문자가 두 번 가지 않게 먼저 선점한다 (메일 mail_log 와 같은 방식)
--   event_id: 이벤트 메시지의 message-id (order·delivery outbox 가 넣는 UUID)
--   status  : SENDING(선점) → SENT(발송 완료). SENDING 으로 남은 건 재전달 때 다시 보낸다
CREATE TABLE sms_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    UUID         NOT NULL UNIQUE,
    event_type  VARCHAR(30)  NOT NULL,
    member_id   BIGINT       NOT NULL,
    status      VARCHAR(10)  NOT NULL CHECK (status IN ('SENDING', 'SENT')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);