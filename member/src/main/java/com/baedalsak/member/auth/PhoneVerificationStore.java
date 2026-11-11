package com.baedalsak.member.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 휴대폰 인증 Redis 저장소 (#164). 규칙(몇 번까지 등)은 PhoneVerificationService 가 정하고 여기는 읽기·쓰기만.
 *   phone:code:{번호}            = 코드        3분
 *   phone:fail:{번호}            = 틀린 횟수    3분
 *   phone:cooldown:{번호}        = 1           60초 (재발송 간격)
 *   phone:daily:{번호}:{날짜}     = 보낸 횟수    1일
 *   phone:verified:{토큰}        = 번호        10분 (가입 때 한 번 쓰고 지움)
 * Redis 장애면 503 — 인증을 건너뛰고 통과시키지 않는다 (fail-closed)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneVerificationStore {

  public static final Duration CODE_TTL = Duration.ofMinutes(3);
  public static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
  public static final Duration DAILY_TTL = Duration.ofDays(1);
  public static final Duration VERIFIED_TTL = Duration.ofMinutes(10);

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;   // 20261111
  private static final SecureRandom RANDOM = new SecureRandom();

  private final StringRedisTemplate redis;

  /** 60초 안에 보낸 적이 없으면 표시를 남기고 true. 이미 있으면 false (재발송 너무 빠름) */
  public boolean tryStartCooldown(String phone) {
    return call("재발송 간격 확인", () ->
        Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("phone:cooldown:" + phone, "1", COOLDOWN_TTL)));
  }

  /** 문자 발송이 실패했을 때 바로 다시 시도할 수 있게 간격 표시를 지운다 */
  public void clearCooldown(String phone) {
    call("재발송 간격 삭제", () -> redis.delete("phone:cooldown:" + phone));
  }

  /** 오늘 보낸 횟수를 1 올리고 올린 값을 돌려준다 */
  public long incrementDaily(String phone) {
    String key = "phone:daily:" + phone + ":" + LocalDate.now(KST).format(DAY);
    return call("하루 발송 횟수", () -> increment(key, DAILY_TTL));
  }

  /** 새 코드 저장 — 이전 코드와 틀린 횟수는 버린다 */
  public void saveCode(String phone, String code) {
    call("코드 저장", () -> {
      redis.opsForValue().set("phone:code:" + phone, code, CODE_TTL);
      return redis.delete("phone:fail:" + phone);
    });
  }

  public Optional<String> findCode(String phone) {
    return call("코드 조회", () -> Optional.ofNullable(redis.opsForValue().get("phone:code:" + phone)));
  }

  /** 틀린 횟수를 1 올리고 올린 값을 돌려준다 */
  public long incrementFail(String phone) {
    return call("틀린 횟수", () -> increment("phone:fail:" + phone, CODE_TTL));
  }

  /** 코드와 틀린 횟수를 지운다 (맞혔거나 너무 많이 틀렸을 때) */
  public void deleteCode(String phone) {
    call("코드 삭제", () -> redis.delete(java.util.List.of("phone:code:" + phone, "phone:fail:" + phone)));
  }

  /** 인증 완료 표를 만들어 저장하고 표(무작위 문자열)를 돌려준다 */
  public String saveVerified(String phone) {
    String token = newToken();
    call("인증 완료 저장", () -> {
      redis.opsForValue().set("phone:verified:" + token, phone, VERIFIED_TTL);
      return null;
    });
    return token;
  }

  /** 표를 꺼내면서 지운다 (한 번만 쓸 수 있게). 없거나 만료면 empty */
  public Optional<String> consumeVerified(String token) {
    return call("인증 완료 확인", () -> Optional.ofNullable(redis.opsForValue().getAndDelete("phone:verified:" + token)));
  }

  // INCR 후 처음 만든 키(값 1)에만 TTL 을 건다 — 매번 걸면 만료가 계속 밀림
  private long increment(String key, Duration ttl) {
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1) {
      redis.expire(key, ttl);
    }
    return count == null ? 0 : count;
  }

  // Redis 예외를 한곳에서 503 으로 바꾼다
  private <T> T call(String what, Supplier<T> action) {
    try {
      return action.get();
    } catch (DataAccessException e) {
      log.warn("휴대폰 인증 Redis 실패: {}", what, e);
      throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  private String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}