package com.baedalsak.member.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키 2개 (설계서 N4.1 · API 명세 8.2).
 *   __Host-access : access 토큰. 모든 요청에 붙어 gateway 가 검증
 *   __Secure-sid  : 세션 ID. /api/v1/auth(갱신·로그아웃)에만 붙음 → refresh:{세션ID}
 * 둘 다 HttpOnly(JS 가 못 읽음) + Secure(HTTPS 만, localhost 는 브라우저가 예외 허용).
 */
@Component
public class AuthCookies {

  public static final String ACCESS = "__Host-access";
  public static final String SESSION = "__Secure-sid";

  // 브라우저 기준 경로 — gateway 가 /api 를 떼기 전 주소
  private static final String SESSION_PATH = "/api/v1/auth";

  public ResponseCookie access(String accessToken, Duration maxAge) {
    return base(ACCESS, accessToken, "/", maxAge);
  }

  public ResponseCookie session(String sessionId) {
    return base(SESSION, sessionId, SESSION_PATH, RefreshSessionStore.TTL);
  }

  /** 로그아웃: 같은 이름·경로로 Max-Age=0 을 보내면 브라우저가 지운다 */
  public ResponseCookie clearAccess() {
    return base(ACCESS, "", "/", Duration.ZERO);
  }

  public ResponseCookie clearSession() {
    return base(SESSION, "", SESSION_PATH, Duration.ZERO);
  }

  private ResponseCookie base(String name, String value, String path, Duration maxAge) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)      // JS 로 못 읽음 → XSS 로 토큰 탈취 불가
        .secure(true)        // HTTPS 로만 전송
        .sameSite("Lax")     // 다른 사이트에서 오는 POST 등에는 안 붙음 (CSRF 1차 방어, 2차는 X-Requested-With)
        .path(path)
        .maxAge(maxAge)
        .build();            // Domain 은 일부러 안 줌 → __Host- 규칙 충족
  }
}