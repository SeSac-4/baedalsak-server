package com.baedalsak.payment.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.repository.PaymentCancelRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 오래 머문 환불 처리 중 감시 (#127). 조정자는 거절 · 취소 후 24시간 안에만 명령 도착을 확인하므로,
 * 명령을 받은 뒤 재시도를 다 써서 DLQ 로 간 환불(진행 중 취소 행)은 payment 가 스스로 알린다.
 * 지표 payment.cancel.pending.stale = 지금 stale-after 보다 오래 진행 중인 취소 건수 (누적이 아니라 현재 값)
 */
@Slf4j
@Component
public class StaleCancelMonitor {

  private final PaymentCancelRepository paymentCancelRepository;
  private final Clock clock;
  private final Duration staleAfter;
  private final AtomicLong staleCount = new AtomicLong();

  public StaleCancelMonitor(PaymentCancelRepository paymentCancelRepository, Clock clock, MeterRegistry meterRegistry,
      @Value("${payment-cancel.stale-after:30m}") Duration staleAfter) {
    this.paymentCancelRepository = paymentCancelRepository;
    this.clock = clock;
    this.staleAfter = staleAfter;
    Gauge.builder("payment.cancel.pending.stale", staleCount, AtomicLong::get)
        .description("오래 머문 진행 중 결제 취소 건수")
        .register(meterRegistry);
  }

  @Scheduled(fixedDelayString = "${payment-cancel.stale-check-interval:10m}", initialDelayString = "1m")
  public void check() {
    long count = paymentCancelRepository.countByStatusAndRequestedAtBefore(PaymentCancelStatus.PENDING,
        OffsetDateTime.now(clock).minus(staleAfter));
    staleCount.set(count);
    if (count > 0) {
      log.error("오래 머문 환불 처리 중 {}건 ({}분 넘게 진행 중), DLQ payment.command.cancel.dlq 와 재처리 확인 필요",
          count, staleAfter.toMinutes());
    }
  }
}
