package com.baedalsak.member.service;

import java.util.function.LongConsumer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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
 * 회원번호 받기(nextval) → Cognito 생성 → DB 저장 · 커밋 → 즉시 로그인 (#234).
 * DB 저장이 실패하면 만든 Cognito 사용자를 지운다.
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
  private final PhoneVerificationService phoneVerificationService;
  private final EmailVerificationService emailVerificationService;
  private final MemberAddressService memberAddressService;

  public LoginResult signupCustomer(SignupCustomerRequest req) {
    // 고객은 프로필 대신, 주소를 넣었으면 첫 주소(기본)로 저장 (#215). 회원 INSERT 와 같은 트랜잭션 — 실패하면 함께 롤백
    return signup(req, Role.CUSTOMER, memberId -> {
      if (req.address() != null) {
        memberAddressService.addSignupAddress(memberId, req.address());
      }
    });
  }

  public LoginResult signupOwner(SignupOwnerRequest req) {
    return signup(req, Role.OWNER,
        memberId -> ownerProfileRepository.saveAndFlush(new OwnerProfile(memberId, req.storeName())));
  }

  public LoginResult signupRider(SignupRiderRequest req) {
    return signup(req, Role.RIDER,
        memberId -> riderProfileRepository.saveAndFlush(new RiderProfile(memberId, req.vehicleType().name())));
  }

  /**
   * 세 역할 공통 흐름 (#234). saveProfile 은 역할별 프로필 INSERT (member id 를 받아서).
   * 회원번호를 먼저 받아 Cognito 를 트랜잭션 밖에서 부르고, DB 는 마지막에 짧은 트랜잭션 하나로 저장한다.
   * → Cognito 응답을 기다리는 동안 DB 커넥션을 잡지 않는다.
   */
  private LoginResult signup(SignupRequest req, Role role, LongConsumer saveProfile) {
    // ① 흔한 실패부터 거른다 — 아무것도 만들기 전이라 정리할 게 없음
    //    이메일 중복 — 대부분 여기서 걸러진다 (표도 쓰지 않음)
    if (memberRepository.existsByEmail(req.email())) {
      throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
    }
    //    이메일 인증 표 (#207). 필수 — 없거나 틀리면 400 (부하 테스트 날 스위치를 끄면 표 없이 통과)
    emailVerificationService.confirmSignupToken(req.email(), req.emailVerificationToken());
    //    휴대폰 인증 표 (#164). 맞으면 인증된 회원으로, 없으면(건너뛰기) 미인증으로. 틀린 표는 400
    boolean phoneVerified = phoneVerificationService.confirmSignupToken(req.phone(), req.phoneVerificationToken());

    // ② 회원번호만 먼저 받는다 (시퀀스 nextval). 가입이 실패하면 이 번호는 비어 있게 된다
    long memberId = memberRepository.nextId();

    // ③ Cognito 사용자 생성 (username = 회원번호) — 트랜잭션 밖. 실패하면 스스로 정리하고 예외 (DB 는 손대지 않음)
    cognitoAuthClient.createUser(memberId, req.email(), req.password(), role);

    // ④ 짧은 트랜잭션: 회원 · 프로필 INSERT → 커밋. 어디서든 실패하면 ③에서 만든 Cognito 사용자를 지운다
    try {
      transactionTemplate.executeWithoutResult(status -> {
        Member member = new Member(memberId, req.email(), req.name(), req.phone(), role);
        if (phoneVerified) {
          member.markPhoneVerified();
        }
        // 동시에 같은 이메일이 들어와 ①을 둘 다 통과하면 UNIQUE 제약이 막는다 → 같은 409
        try {
          memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
          throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
        }
        saveProfile.accept(memberId);
      });
    } catch (RuntimeException e) {
      log.warn("가입 DB 저장 실패 — Cognito 사용자 보상 삭제 memberId={}", memberId);
      cognitoAuthClient.deleteUser(memberId);   // 예외를 던지지 않음 (실패하면 로그로 남겨 수동 정리)
      throw e;
    }

    // ⑤ 커밋 후 바로 로그인 → 쿠키용 토큰·세션 ID (로그인과 같은 길)
    return authService.login(new LoginRequest(req.email(), req.password()));
  }
}
