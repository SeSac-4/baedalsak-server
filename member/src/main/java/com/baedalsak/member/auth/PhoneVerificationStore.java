package com.baedalsak.member.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 휴대폰 인증 Redis 저장소 (#164). 키는 phone:code:{번호} · phone:verified:{표} … (규칙은 VerificationCodeStore)
 * 규칙(몇 번까지 등)은 PhoneVerificationService 가 정한다.
 */
@Component
public class PhoneVerificationStore extends VerificationCodeStore {

  public PhoneVerificationStore(StringRedisTemplate redis) {
    super(redis, "phone");
  }
}
