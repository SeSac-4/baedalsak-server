package com.baedalsak.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PG 대사 스케줄 (#154, 설계서 '동기 호출' PG 대사 평일 17:30 · '인프라 구성 상세' 앱 스케줄러).
 * 당일 00:00 ~ 실행 시각(한국 시각)을 대사한다. 실패해도 예외를 밖으로 던지지 않고 다음 실행에서 다시 한다.
 * 인프라가 평일 18:00 에 꺼지고 개발 기간 DB 를 매일 비워 17:30 ~ 18:00 거래는 대상 밖 (노트 6절).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgReconcileScheduler {

  private final PgReconcileService pgReconcileService;
  private final Clock clock;

  @Scheduled(cron = "${payment-reconcile.cron:0 30 17 * * MON-FRI}", zone = "Asia/Seoul")
  public void run() {
    LocalDateTime end = LocalDateTime.now(clock.withZone(PgReconcileService.SEOUL)).withNano(0);
    LocalDateTime start = end.toLocalDate().atStartOfDay();
    try {
      pgReconcileService.reconcile(start, end);
    } catch (RuntimeException e) {
      log.error("PG 대사 실패, 다음 실행에서 다시 기간={}~{}", start, end, e);
    }
  }
}
