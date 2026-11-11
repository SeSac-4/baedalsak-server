package com.baedalsak.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.auth.TokenBlacklistStore;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 탈퇴 (#188) — 지금은 고객만.
 * DB 탈퇴(커밋) → Cognito 전체 로그아웃 · 사용자 삭제 → 지금 AT 블랙리스트.
 * DB 를 먼저 커밋하는 이유: Cognito 가 실패해도 DB 가 탈퇴 상태라 로그인 · 조회가 막힌다 (반대면 반쪽 회원)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWithdrawService {

  // seed 계정(1~999)은 팀 공용 테스트 계정 → 탈퇴 금지. 가입은 1000 부터
  static final long FIRST_SIGNUP_ID = 1000;

  private final MemberRepository memberRepository;
  private final CognitoAuthClient cognitoAuthClient;
  private final TokenBlacklistStore tokenBlacklistStore;
  private final TransactionTemplate transactionTemplate;

  /** tokenId · expiresAt: 지금 AT 의 jti · 만료 시각 (gateway 가 넘김, 없으면 블랙리스트 생략) */
  public void withdraw(long memberId, String tokenId, Long expiresAt) {
    // ① DB: 확인 → 탈퇴 표시 → 커밋. 여기서 예외면 아무것도 안 바뀜
    transactionTemplate.executeWithoutResult(status -> {
      Member member = memberRepository.findById(memberId)
          .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));   // 이미 탈퇴했어도 404
      if (member.getRole() != Role.CUSTOMER || memberId < FIRST_SIGNUP_ID) {
        throw new BusinessException(MemberErrorCode.WITHDRAWAL_NOT_ALLOWED);
      }
      member.withdraw();   // 변경 감지로 커밋 때 UPDATE
    });
    log.info("회원 탈퇴 memberId={}", memberId);

    // ② Cognito: 다른 기기 refresh 폐기 → 사용자 삭제 (이후 같은 이메일로 다시 가입 가능)
    //    둘 다 실패해도 예외를 던지지 않고 로그만 (삭제 실패는 ERROR "수동 정리 필요")
    cognitoAuthClient.globalSignOut(memberId);
    cognitoAuthClient.deleteUser(memberId);

    // ③ 지금 AT 를 바로 막음. Cognito 사용자를 지워도 AT 는 만료(30분)까지 서명이 유효해서
    if (tokenId != null && expiresAt != null) {
      tokenBlacklistStore.add(tokenId, expiresAt);
    }
  }
}
