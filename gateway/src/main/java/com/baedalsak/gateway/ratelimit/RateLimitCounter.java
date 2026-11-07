package com.baedalsak.gateway.ratelimit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 요청 제한 카운터 (#24). Redis 에 1분 단위 칸을 만들어 횟수를 센다 (고정 창).
 * 키: rate-limit:{묶음}:{member:id | ip:주소}:{분 번호}, 수명 60초.
 * Redis 장애·지연이면 세지 않고 통과(fail-open) — 요청 제한 때문에 전체 API 가 멈추지 않게.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitCounter {

  static final long WINDOW_SECONDS = 60;   // 칸 하나의 길이 (1분). 필터가 Retry-After 계산에 같이 씀
  private static final String KEY_PREFIX = "rate-limit:";

  // INCR 로 1 올리고, 처음 만든 키(값이 1)면 수명을 붙인다. Redis 가 한 번에 실행 → 중간에 끊겨도 수명 없는 키가 안 남음
  private static final RedisScript<Long> INCREMENT = RedisScript.of("""
      local count = redis.call('INCR', KEYS[1])
      if count == 1 then
        redis.call('EXPIRE', KEYS[1], ARGV[1])
      end
      return count
      """, Long.class);

  private final ReactiveStringRedisTemplate redis;
  private final RateLimitProperties properties;

  /**
   * 이번 분의 횟수를 1 올리고, 한도 안이면 true.
   * @param name  누구의 어떤 요청인지 (예: auth:ip:1.2.3.4, payment:member:1)
   * @param limit 1분 동안 허용할 횟수
   */
  public Mono<Boolean> tryAcquire(String name, int limit) {
    long window = Instant.now().getEpochSecond() / WINDOW_SECONDS;   // 분 번호
    String key = KEY_PREFIX + name + ":" + window;
    return redis.execute(INCREMENT, List.of(key), List.of(String.valueOf(WINDOW_SECONDS)))
        .next()                                   // 스크립트 결과(Flux)에서 첫 값 하나만
        .map(count -> count <= limit)             // 올린 뒤 값이 한도 이하면 허용
        .timeout(properties.timeout())            // 100ms 안에 답이 없으면 실패로 봄
        .onErrorResume(e -> {
          log.warn("요청 제한 카운터 실패 — 통과 처리 (fail-open): {}", e.toString());
          return Mono.just(true);
        });
  }
}