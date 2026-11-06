package com.baedalsak.payment.service;

import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.repository.PaymentCancelRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 오래 머문 환불 처리 중 감시 (#127). 지금 기준 stale-after 보다 오래된 진행 중 취소 개수를 지표로 둔다 (누적 아님)
 */
class StaleCancelMonitorTest {

    static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");

    final PaymentCancelRepository repository = mock(PaymentCancelRepository.class);
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final StaleCancelMonitor monitor = new StaleCancelMonitor(repository, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")),
            registry, Duration.ofMinutes(30));

    @Test
    void 기준_시각보다_오래된_진행_중_취소_개수를_지표로_둔다() {
        OffsetDateTime before = OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul")).minusMinutes(30);
        given(repository.countByStatusAndRequestedAtBefore(PaymentCancelStatus.PENDING, before)).willReturn(2L, 0L);

        monitor.check();
        assertThat(registry.get("payment.cancel.pending.stale").gauge().value()).isEqualTo(2.0);

        monitor.check();
        assertThat(registry.get("payment.cancel.pending.stale").gauge().value()).isZero();
    }
}
