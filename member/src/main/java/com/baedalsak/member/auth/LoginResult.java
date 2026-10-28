package com.baedalsak.member.auth;

import java.time.Duration;

import com.baedalsak.member.dto.LoginResponse;

/**
 * 로그인(가입 후 로그인 포함) 결과 — Service → Controller 전달용.
 * Controller 는 accessToken·sessionId 로 쿠키를 만들고, body 만 응답 본문으로 보낸다.
 */
public record LoginResult(
    String accessToken,
    Duration accessMaxAge,
    String sessionId,
    LoginResponse body
) {

  // 토큰·세션 ID 가 로그에 찍히지 않게
  @Override
  public String toString() {
    return "LoginResult[accessToken=****, accessMaxAge=" + accessMaxAge + ", sessionId=****, body=" + body + "]";
  }
}