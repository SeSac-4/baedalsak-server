package com.baedalsak.member.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 토큰 블랙리스트 — Redis 키 blacklist:{jti}, TTL = AT 남은 수명 (#111).
 * member 는 로그아웃 때 등록만 하고, 조회는 gateway 가 요청마다 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistStore {

  private static final String KEY_PREFIX = "blacklist:";

  private final StringRedisTemplate redis;

  /** 로그아웃한 AT 를 등록한다. 이미 만료됐으면 생략. 실패해도 예외를 던지지 않는다 (로그아웃은 항상 성공). */
  public void add(String tokenId, long expiresAtEpochSecond) {
    Duration remaining = Duration.between(Instant.now(), Instant.ofEpochSecond(expiresAtEpochSecond));
    if (remaining.isNegative() || remaining.isZero()) {
      return;   // 어차피 gateway 가 만료로 거절
    }

    try {
      redis.opsForValue().set(KEY_PREFIX + tokenId, "1", remaining);
    } catch (DataAccessException e) {
      log.warn("토큰 블랙리스트 등록 실패 (Redis) — AT 만료까지 유효", e);
    }
  }
}