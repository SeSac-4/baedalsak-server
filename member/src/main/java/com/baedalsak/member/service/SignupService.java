package com.baedalsak.member.service;

import java.util.function.LongConsumer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.auth.LoginResult;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.OwnerProfile;
import com.baedalsak.member.domain.RiderProfile;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.dto.LoginRequest;
import com.baedalsak.member.dto.SignupCustomerRequest;
import com.baedalsak.member.dto.SignupOwnerRequest;
import com.baedalsak.member.dto.SignupRequest;
import com.baedalsak.member.dto.SignupRiderRequest;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.repository.MemberRepository;
import com.baedalsak.member.repository.OwnerProfileRepository;
import com.baedalsak.member.repository.RiderProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원가입 (API 명세 8.1.1~8.1.3, B안: Cognito username = member_id, 역할 = 그룹).
 * DB INSERT → Cognito 생성 → 커밋 → 즉시 로그인. 커밋이 실패하면 만든 Cognito 사용자를 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

  private final MemberRepository memberRepository;
  private final OwnerProfileRepository ownerProfileRepository;
  private final RiderProfileRepository riderProfileRepository;
  private final CognitoAuthClient cognitoAuthClient;
  private final TransactionTemplate transactionTemplate;   // Boot 가 기본으로 만들어 줌
  private final AuthService authService;

  public LoginResult signupCustomer(SignupCustomerRequest req) {
    return signup(req, Role.CUSTOMER, memberId -> { });   // 고객은 프로필 없음
  }

  public LoginResult signupOwner(SignupOwnerRequest req) {
    return signup(req, Role.OWNER,
        memberId -> ownerProfileRepository.saveAndFlush(new OwnerProfile(memberId, req.storeName())));
  }

  public LoginResult signupRider(SignupRiderRequest req) {
    return signup(req, Role.RIDER,
        memberId -> riderProfileRepository.saveAndFlush(new RiderProfile(memberId, req.vehicleType().name())));
  }

  /** 세 역할 공통 흐름. saveProfile 은 역할별 프로필 INSERT (member id 를 받아서) */
  private LoginResult signup(SignupRequest req, Role role, LongConsumer saveProfile) {
    transactionTemplate.executeWithoutResult(status -> {
      // ① 이메일 중복 — 대부분 여기서 걸러진다 (Cognito 까지 가지 않음)
      if (memberRepository.existsByEmail(req.email())) {
        throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
      }

      // ② member INSERT 를 지금 실행(flush)해 id 를 받는다. 커밋은 아직
      //    동시에 같은 이메일이 들어와 ①을 둘 다 통과하면 UNIQUE 제약이 막는다 → 같은 409
      long memberId;
      try {
        memberId = memberRepository.saveAndFlush(new Member(req.email(), req.name(), req.phone(), role)).getId();
      } catch (DataIntegrityViolationException e) {
        throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
      }
      saveProfile.accept(memberId);   // 프로필도 flush → DB 오류는 Cognito 전에 터지게

      // ③ Cognito 사용자 생성. 실패하면 스스로 정리하고 예외 → 이 트랜잭션 롤백
      cognitoAuthClient.createUser(memberId, req.email(), req.password(), role);

      // ④ 여기까지 왔는데 커밋이 실패하면 Cognito 에만 사용자가 남는다 → 끝난 뒤 지우도록 예약
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int completionStatus) {
          if (completionStatus != STATUS_COMMITTED) {
            log.warn("가입 커밋 실패 — Cognito 사용자 보상 삭제 memberId={}", memberId);
            cognitoAuthClient.deleteUser(memberId);
          }
        }
      });
    });

    // ⑤ 커밋 후 바로 로그인 → 쿠키용 토큰·세션 ID (로그인과 같은 길)
    return authService.login(new LoginRequest(req.email(), req.password()));
  }
}