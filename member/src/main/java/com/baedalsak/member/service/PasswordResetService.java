package com.baedalsak.member.service;

import org.springframework.stereotype.Service;

import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.dto.PasswordForgotRequest;
import com.baedalsak.member.dto.PasswordResetRequest;
import com.baedalsak.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** 비밀번호 찾기·재설정 (#162). 코드 발송·확인·비밀번호 저장은 Cognito 가 한다. */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

  private final CognitoAuthClient cognitoAuthClient;
  private final MemberRepository memberRepository;

  // 없는 이메일이어도 예외 없이 끝남 (CognitoAuthClient 가 숨김) → 컨트롤러는 항상 202
  public void forgot(PasswordForgotRequest req) {
    cognitoAuthClient.forgotPassword(req.email());
  }

  public void reset(PasswordResetRequest req) {
    // ① 코드 확인 + 새 비밀번호 저장. 코드 틀림·만료 400, 시도 초과 429, 장애 503
    cognitoAuthClient.confirmForgotPassword(req.email(), req.code(), req.newPassword());

    // ② 다른 기기 로그아웃 — Admin API 는 username(회원번호)으로 부른다
    //    DB 에 없으면(데이터 불일치) 건너뜀. 비밀번호는 이미 바뀌었으므로 재설정은 성공
    memberRepository.findByEmail(req.email())
        .ifPresent(member -> cognitoAuthClient.globalSignOut(member.getId()));
  }
}