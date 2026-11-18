package com.baedalsak.member.notification;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baedalsak.member.config.SmsLogProperties;
import com.baedalsak.member.repository.SmsLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 문자 멱등 기록 sms_log 정리 (#232). 보관 기간(기본 30일)이 지난 행을 매일 지운다.
 * 중복 전달은 몇 분 안에 오므로 오래된 기록은 멱등에 쓰이지 않는다.
 * 파드가 2개여도 그대로 둔다 — 같은 DELETE 가 두 번 돌아도 결과가 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsLogCleanupScheduler {

  static final int BATCH_SIZE = 1000;

  private final SmsLogRepository smsLogRepository;
  private final SmsLogProperties props;

  @Scheduled(cron = "${sms-log.cleanup-cron}", zone = "Asia/Seoul")
  public void cleanup() {
    cleanupBefore(OffsetDateTime.now().minus(props.retention()));
  }

  /** before 보다 오래된 행을 1,000개씩 지운다. 한 번에 1,000개 미만이면 남은 게 없다는 뜻이라 멈춘다 */
  int cleanupBefore(OffsetDateTime before) {
    int total = 0;
    int deleted;
    do {
      deleted = smsLogRepository.deleteOlderThan(before, BATCH_SIZE);
      total += deleted;
    } while (deleted == BATCH_SIZE);
    log.info("sms_log 정리 완료 deleted={} before={}", total, before);
    return total;
  }
}
