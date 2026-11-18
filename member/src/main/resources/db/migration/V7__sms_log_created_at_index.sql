-- sms_log 정리 스케줄러 (#232) 가 보관 기간이 지난 행을 찾을 때 전체 스캔하지 않게
CREATE INDEX idx_sms_log_created_at ON sms_log (created_at);
