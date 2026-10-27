package com.baedalsak.member.auth;

/**
 * Cognito 가 발급한 토큰.
 * accessToken: 30분, 쿠키(__Host-access)로 브라우저에 전달
 * refreshToken: 14일, Redis 에만 저장 (갱신 응답에서는 null — Cognito 가 새로 주지 않음)
 */
public record CognitoTokens(
    String accessToken,
    String refreshToken,
    int expiresInSeconds
) {

  // 토큰이 로그에 찍히지 않게 (토큰 = 비밀번호급 정보)
  @Override
  public String toString() {
    return "CognitoTokens[accessToken=****, refreshToken=****, expiresInSeconds=" + expiresInSeconds + "]";
  }
}