package com.baedalsak.member.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.member.auth.AuthCookies;
import com.baedalsak.member.auth.LoginResult;
import com.baedalsak.member.dto.LoginRequest;
import com.baedalsak.member.dto.LoginResponse;
import com.baedalsak.member.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 인증 API (외부, gateway 경유: /api/v1/auth/** → /v1/auth/**). 토큰은 본문이 아니라 쿠키로만 준다. */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final AuthCookies authCookies;

  // 이메일 로그인 (API 명세 8.2.1) — 200 + Set-Cookie(__Host-access, __Secure-sid)
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
    LoginResult result = authService.login(req);

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, authCookies.access(result.accessToken(), result.accessMaxAge()).toString())
        .header(HttpHeaders.SET_COOKIE, authCookies.session(result.sessionId()).toString())
        .body(result.body());
  }
}