package com.baedalsak.member.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baedalsak.member.config.SmsLogProperties;
import com.baedalsak.member.repository.SmsLogRepository;

/** sms_log 정리 (#232): DB 는 가짜(mock)로 두고 "얼마나 오래된 것을 몇 번 나눠 지우는지"만 검사 */
class SmsLogCleanupSchedulerTest {

  private static final int BATCH = SmsLogCleanupScheduler.BATCH_SIZE;

  private final SmsLogRepository repository = mock(SmsLogRepository.class);
  private final SmsLogCleanupScheduler scheduler =
      new SmsLogCleanupScheduler(repository, new SmsLogProperties(Duration.ofDays(30)));

  @Test
  void 지울_게_1000개를_넘으면_다_지울_때까지_반복() {
    OffsetDateTime before = OffsetDateTime.now();
    when(repository.deleteOlderThan(before, BATCH)).thenReturn(BATCH, BATCH, 5);

    int total = scheduler.cleanupBefore(before);

    assertThat(total).isEqualTo(BATCH * 2 + 5);
    verify(repository, times(3)).deleteOlderThan(before, BATCH);
  }

  @Test
  void 지울_게_없으면_한_번만_조회하고_끝() {
    OffsetDateTime before = OffsetDateTime.now();
    when(repository.deleteOlderThan(before, BATCH)).thenReturn(0);

    assertThat(scheduler.cleanupBefore(before)).isZero();
    verify(repository, times(1)).deleteOlderThan(before, BATCH);
  }

  @Test
  void 기준_시각은_지금부터_보관_기간_전() {
    when(repository.deleteOlderThan(any(), eq(BATCH))).thenReturn(0);
    OffsetDateTime start = OffsetDateTime.now();

    scheduler.cleanup();

    ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(repository).deleteOlderThan(captor.capture(), eq(BATCH));
    assertThat(captor.getValue())
        .isBetween(start.minusDays(30), OffsetDateTime.now().minusDays(30));
  }
}
