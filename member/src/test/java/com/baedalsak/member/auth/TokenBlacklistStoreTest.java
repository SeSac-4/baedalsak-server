package com.baedalsak.member.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 토큰 블랙리스트 등록 (#111): TTL = AT 남은 수명, 만료됐으면 생략, Redis 장애여도 예외 없음 */
class TokenBlacklistStoreTest {

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);
  private final TokenBlacklistStore store = new TokenBlacklistStore(redis);

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(values);
  }

  @Test
  void 남은_수명만큼_TTL로_등록() {
    long expiresAt = Instant.now().plusSeconds(600).getEpochSecond();

    store.add("jti-1", expiresAt);

    ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
    verify(values).set(eq("blacklist:jti-1"), eq("1"), ttl.capture());
    assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(600));
  }

  @Test
  void 이미_만료된_토큰은_등록하지_않음() {
    store.add("jti-1", Instant.now().minusSeconds(1).getEpochSecond());

    verifyNoInteractions(redis);
  }

  @Test
  void Redis_장애여도_예외를_던지지_않음() {
    doThrow(new RedisConnectionFailureException("down"))
        .when(values).set(anyString(), anyString(), any(Duration.class));

    assertThatCode(() -> store.add("jti-1", Instant.now().plusSeconds(600).getEpochSecond()))
        .doesNotThrowAnyException();
  }
}
