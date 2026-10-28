package com.baedalsak.member.service;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.auth.CognitoTokens;
import com.baedalsak.member.auth.LoginResult;
import com.baedalsak.member.auth.RefreshSessionStore;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.dto.LoginRequest;
import com.baedalsak.member.dto.LoginResponse;
import com.baedalsak.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 로그인·갱신·로그아웃 (member 는 Cognito 창구 — 토큰은 Cognito 가 발급). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final CognitoAuthClient cognitoAuthClient;
  private final RefreshSessionStore refreshSessionStore;
  private final MemberRepository memberRepository;

  public LoginResult login(LoginRequest req) {
    // ① 비밀번호 확인은 Cognito 가 한다 (틀리면 401, 장애면 503 — CognitoAuthClient 가 변환)
    CognitoTokens tokens = cognitoAuthClient.login(req.email(), req.password());

    // ② 응답 본문용 회원 정보. Cognito 에는 있는데 DB 에 없으면 데이터 불일치
    Member member = memberRepository.findByEmail(req.email())
        .orElseThrow(() -> {
          log.error("Cognito 로그인은 성공했지만 member DB 에 없음 — DB·Cognito 불일치");
          return new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        });

    // ③ refresh 토큰은 Redis 에만. 저장 실패면 503 (쿠키를 주지 않아 반쪽 로그인을 막음)
    String sessionId = refreshSessionStore.save(tokens.refreshToken());

    return new LoginResult(
        tokens.accessToken(),
        Duration.ofSeconds(tokens.expiresInSeconds()),
        sessionId,
        new LoginResponse(member.getId(), member.getRole()));
  }
}