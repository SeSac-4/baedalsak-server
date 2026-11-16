package com.baedalsak.member.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 가입 전 이메일 인증 Redis 저장소 (#207). 키는 email:code:{이메일} · email:verified:{표} … (규칙은 VerificationCodeStore)
 * 이메일은 소문자로 맞춘 값으로 저장한다 (EmailVerificationService.normalize).
 */
@Component
public class EmailVerificationStore extends VerificationCodeStore {

  public EmailVerificationStore(StringRedisTemplate redis) {
    super(redis, "email");
  }
}
