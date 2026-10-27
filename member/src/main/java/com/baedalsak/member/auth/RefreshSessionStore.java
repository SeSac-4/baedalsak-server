package com.baedalsak.member.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * refresh 세션 저장소 — Redis 키 refresh:{세션ID} = Cognito refresh 토큰, TTL 14일 (2026-10-27 인프라와 확정).
 * 브라우저에는 세션 ID 만 쿠키로 주고, refresh 토큰은 여기에만 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshSessionStore {

  private static final String KEY_PREFIX = "refresh:";
  public static final Duration TTL = Duration.ofDays(14);   // Cognito refresh 토큰 수명과 같게

  private static final SecureRandom RANDOM = new SecureRandom();

  private final StringRedisTemplate redis;

  /** 새 세션 ID 를 만들어 refresh 토큰을 저장하고, 세션 ID 를 돌려준다. Redis 장애면 503 (로그인 실패 처리). */
  public String save(String refreshToken) {
    String sessionId = newSessionId();
    try {
      redis.opsForValue().set(KEY_PREFIX + sessionId, refreshToken, TTL);
    } catch (DataAccessException e) {
      log.warn("refresh 세션 저장 실패 (Redis)", e);
      throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    }
    return sessionId;
  }

  /** 세션 ID 로 refresh 토큰을 찾는다. 없거나 만료면 empty. Redis 장애면 503. */
  public Optional<String> find(String sessionId) {
    try {
      return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + sessionId));
    } catch (DataAccessException e) {
      log.warn("refresh 세션 조회 실패 (Redis)", e);
      throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  /** 로그아웃: 세션을 지운다. 실패해도 예외를 던지지 않는다 (로그아웃은 항상 성공). */
  public void delete(String sessionId) {
    try {
      redis.delete(KEY_PREFIX + sessionId);
    } catch (DataAccessException e) {
      log.warn("refresh 세션 삭제 실패 (Redis) — TTL 만료로 정리됨", e);
    }
  }

  // 추측 불가능한 무작위 32바이트 → URL·쿠키에 안전한 43자 문자열
  private String newSessionId() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}