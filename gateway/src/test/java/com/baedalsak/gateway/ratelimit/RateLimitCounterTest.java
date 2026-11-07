package com.baedalsak.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import reactor.core.publisher.Flux;

/** 요청 제한 카운터 (#24): 한도 이하 허용, 초과 거절, Redis 장애·지연은 통과 */
class RateLimitCounterTest {

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final RateLimitCounter counter = new RateLimitCounter(redis,
      new RateLimitProperties(true, 10, 10, 10, 100, 2, Duration.ofMillis(100)));

  /** 가짜 Redis 가 스크립트 결과로 count 를 돌려주게 한다 */
  private void redisReturns(Flux<Long> result) {
    when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn((Flux) result);
  }

  @Test
  void 한도까지는_허용() {
    redisReturns(Flux.just(10L));
    assertThat(counter.tryAcquire("auth:ip:1.2.3.4", 10).block()).isTrue();
  }

  @Test
  void 한도를_넘으면_거절() {
    redisReturns(Flux.just(11L));
    assertThat(counter.tryAcquire("auth:ip:1.2.3.4", 10).block()).isFalse();
  }

  @Test
  void Redis_장애면_통과() {
    redisReturns(Flux.error(new RedisConnectionFailureException("down")));
    assertThat(counter.tryAcquire("auth:ip:1.2.3.4", 10).block()).isTrue();
  }

  @Test
  void Redis_가_100ms_넘게_느리면_통과() {
    redisReturns(Flux.never());   // 영원히 답이 없음 → timeout
    assertThat(counter.tryAcquire("auth:ip:1.2.3.4", 10).block()).isTrue();
  }

  @Test
  void 키는_묶음_대상_분번호_모양이고_수명은_60초() {
    redisReturns(Flux.just(1L));
    long window = Instant.now().getEpochSecond() / 60;

    counter.tryAcquire("payment:member:1", 10).block();

    verify(redis).execute(any(RedisScript.class),
        org.mockito.ArgumentMatchers.eq(List.of("rate-limit:payment:member:1:" + window)),
        org.mockito.ArgumentMatchers.eq(List.of("60")));
  }
}