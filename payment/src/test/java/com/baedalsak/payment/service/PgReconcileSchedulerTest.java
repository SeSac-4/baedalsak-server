package com.baedalsak.payment.service;

import com.baedalsak.payment.client.toss.TossApiException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * PG 대사 스케줄 (#154). 한국 시각 당일 00:00 ~ 실행 시각을 대사하고, 실패해도 예외를 밖으로 던지지 않는다
 */
class PgReconcileSchedulerTest {

    // 2026-11-06 17:30:00 KST
    final Clock clock = Clock.fixed(Instant.parse("2026-11-06T08:30:00Z"), ZoneOffset.UTC);
    final PgReconcileService service = mock(PgReconcileService.class);
    final PgReconcileScheduler scheduler = new PgReconcileScheduler(service, clock);

    @Test
    void 한국_시각_당일_0시부터_실행_시각까지_대사한다() {
        scheduler.run();

        verify(service).reconcile(LocalDateTime.of(2026, 11, 6, 0, 0), LocalDateTime.of(2026, 11, 6, 17, 30));
    }

    @Test
    void 대사가_실패해도_예외를_던지지_않는다() {
        given(service.reconcile(LocalDateTime.of(2026, 11, 6, 0, 0), LocalDateTime.of(2026, 11, 6, 17, 30)))
                .willThrow(TossApiException.noResponse(new SocketTimeoutException("read timed out")));

        assertThatCode(scheduler::run).doesNotThrowAnyException();
    }
}
