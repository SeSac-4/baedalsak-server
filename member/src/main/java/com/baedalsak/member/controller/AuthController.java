package com.baedalsak.member.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.member.auth.AuthCookies;
import com.baedalsak.member.auth.CognitoTokens;
import com.baedalsak.member.auth.LoginResult;
import com.baedalsak.member.dto.LoginRequest;
import com.baedalsak.member.dto.LoginResponse;
import com.baedalsak.member.dto.SignupCustomerRequest;
import com.baedalsak.member.dto.SignupOwnerRequest;
import com.baedalsak.member.dto.SignupRiderRequest;
import com.baedalsak.member.service.AuthService;
import com.baedalsak.member.service.SignupService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 인증 API (외부, gateway 경유: /api/v1/auth/** → /v1/auth/**). 토큰은 본문이 아니라 쿠키로만 준다. */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final AuthCookies authCookies;
  private final SignupService signupService;

   // 고객 가입 (API 명세 8.1.1) — 201 + 로그인과 같은 쿠키 2개. 가입 즉시 로그인 상태
  @PostMapping("/signup/customer")
  public ResponseEntity<LoginResponse> signupCustomer(@Valid @RequestBody SignupCustomerRequest req) {
    return loggedIn(HttpStatus.CREATED, signupService.signupCustomer(req));
  }

  // 사장님 가입 (API 명세 8.1.2) — storeName 은 owner_profile 에 저장
  @PostMapping("/signup/owner")
  public ResponseEntity<LoginResponse> signupOwner(@Valid @RequestBody SignupOwnerRequest req) {
    return loggedIn(HttpStatus.CREATED, signupService.signupOwner(req));
  }

  // 라이더 가입 (API 명세 8.1.3) — vehicleType 은 rider_profile 에 저장
  @PostMapping("/signup/rider")
  public ResponseEntity<LoginResponse> signupRider(@Valid @RequestBody SignupRiderRequest req) {
    return loggedIn(HttpStatus.CREATED, signupService.signupRider(req));
  }

  // 이메일 로그인 (API 명세 8.2.1) — 200 + Set-Cookie(__Host-access, __Secure-sid)
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
    return loggedIn(HttpStatus.OK, authService.login(req));
  }
    // 토큰 갱신 (API 명세 8.2.2) — 200 + 새 __Host-access, 본문 없음
  //   세션 쿠키는 Path=/api/v1/auth 라 브라우저가 이 요청에 자동으로 붙인다
  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(
      @CookieValue(name = AuthCookies.SESSION, required = false) String sessionId) {
    CognitoTokens tokens = authService.refresh(sessionId);

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE,
            authCookies.access(tokens.accessToken(), Duration.ofSeconds(tokens.expiresInSeconds())).toString())
        .build();
  }

    // 로그아웃 (API 명세 8.2.2) — 항상 204 + 쿠키 2개 삭제
  //   access 토큰이 만료됐어도 세션 쿠키만 있으면 동작. 세션 쿠키가 없어도 204
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = AuthCookies.SESSION, required = false) String sessionId) {
    authService.logout(sessionId);

    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, authCookies.clearAccess().toString())
        .header(HttpHeaders.SET_COOKIE, authCookies.clearSession().toString())
        .build();
  }

    // 로그인·가입 공통 응답: access·세션 쿠키 2개 + 본문(memberId, role). 토큰은 본문에 안 넣는다
  private ResponseEntity<LoginResponse> loggedIn(HttpStatus status, LoginResult result) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, authCookies.access(result.accessToken(), result.accessMaxAge()).toString())
        .header(HttpHeaders.SET_COOKIE, authCookies.session(result.sessionId()).toString())
        .body(result.body());
  }
}