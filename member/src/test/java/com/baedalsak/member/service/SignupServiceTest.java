package com.baedalsak.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.auth.LoginResult;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.dto.LoginRequest;
import com.baedalsak.member.dto.LoginResponse;
import com.baedalsak.member.dto.SignupCustomerRequest;
import com.baedalsak.member.dto.SignupOwnerRequest;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.repository.MemberRepository;
import com.baedalsak.member.repository.OwnerProfileRepository;
import com.baedalsak.member.repository.RiderProfileRepository;

/**
 * 회원가입 (#19 T47): DB 저장 → Cognito 생성 → 커밋 → 로그인 순서와 실패 시 정리.
 * DB 없이 돌리려고 저장소 · Cognito · 로그인은 가짜(mock), 트랜잭션은 커밋 · 롤백만 기록하는 가짜 관리자를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

  private static final long MEMBER_ID = 1000L;
  private static final String EMAIL = "hj.test@test.baedalsak.click";
  private static final String PASSWORD = "pass1234";

  @Mock MemberRepository memberRepository;
  @Mock OwnerProfileRepository ownerProfileRepository;
  @Mock RiderProfileRepository riderProfileRepository;
  @Mock CognitoAuthClient cognitoAuthClient;
  @Mock AuthService authService;

  private final FakeTransactionManager tx = new FakeTransactionManager();
  private SignupService signupService;

  @BeforeEach
  void setUp() {
    signupService = new SignupService(memberRepository, ownerProfileRepository, riderProfileRepository,
        cognitoAuthClient, new TransactionTemplate(tx), authService);
  }

  private SignupCustomerRequest customer() {
    return new SignupCustomerRequest(EMAIL, PASSWORD, "김형준", "010-1234-5678");
  }

  /** DB 가 INSERT 후 id 1000 을 돌려준 것처럼 */
  private void memberSavedWithId() {
    Member saved = mock(Member.class);
    when(saved.getId()).thenReturn(MEMBER_ID);
    when(memberRepository.saveAndFlush(any(Member.class))).thenReturn(saved);
  }

  @Test
  void 고객_가입_성공_커밋_후_로그인() {
    memberSavedWithId();
    LoginResult loggedIn = new LoginResult("at", Duration.ofMinutes(30), "sid", new LoginResponse(MEMBER_ID, Role.CUSTOMER));
    when(authService.login(new LoginRequest(EMAIL, PASSWORD))).thenReturn(loggedIn);

    LoginResult result = signupService.signupCustomer(customer());

    assertThat(result).isEqualTo(loggedIn);
    verify(cognitoAuthClient).createUser(MEMBER_ID, EMAIL, PASSWORD, Role.CUSTOMER);   // username = member id
    assertThat(tx.committed).isTrue();
    verify(cognitoAuthClient, never()).deleteUser(anyLong());
  }

  @Test
  void 사장님_가입은_가게_이름을_프로필에_저장() {
    memberSavedWithId();

    signupService.signupOwner(new SignupOwnerRequest(EMAIL, PASSWORD, "김형준", "010-1234-5678", "싹싹분식"));

    verify(ownerProfileRepository).saveAndFlush(argThat(profile ->
        profile.getMemberId() == MEMBER_ID && profile.getStoreNameAtSignup().equals("싹싹분식")));
    verify(cognitoAuthClient).createUser(MEMBER_ID, EMAIL, PASSWORD, Role.OWNER);
  }

  @Test
  void 이미_가입된_이메일이면_409_Cognito_는_부르지_않음() {
    when(memberRepository.existsByEmail(EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> signupService.signupCustomer(customer()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(MemberErrorCode.EMAIL_DUPLICATED);
    verify(cognitoAuthClient, never()).createUser(anyLong(), anyString(), anyString(), any());
    assertThat(tx.rolledBack).isTrue();
  }

  @Test
  void 동시_가입으로_DB_UNIQUE_위반이면_409() {
    when(memberRepository.saveAndFlush(any(Member.class)))
        .thenThrow(new DataIntegrityViolationException("member_email_key"));

    assertThatThrownBy(() -> signupService.signupCustomer(customer()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(MemberErrorCode.EMAIL_DUPLICATED);
    verify(cognitoAuthClient, never()).createUser(anyLong(), anyString(), anyString(), any());
  }

  @Test
  void Cognito_생성_실패면_DB_롤백_로그인_안함() {
    memberSavedWithId();
    doThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE))
        .when(cognitoAuthClient).createUser(anyLong(), anyString(), anyString(), any());

    assertThatThrownBy(() -> signupService.signupCustomer(customer()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    assertThat(tx.rolledBack).isTrue();
    assertThat(tx.committed).isFalse();
    verify(cognitoAuthClient, never()).deleteUser(anyLong());   // 만들어진 사용자가 없음 (createUser 가 스스로 정리)
    verify(authService, never()).login(any());
  }

  @Test
  void 커밋이_실패하면_만든_Cognito_사용자를_지움() {
    memberSavedWithId();
    tx.failOnCommit = true;

    assertThatThrownBy(() -> signupService.signupCustomer(customer()))
        .isInstanceOf(TransactionSystemException.class);
    verify(cognitoAuthClient).deleteUser(MEMBER_ID);   // 보상: DB 에 없는 Cognito 사용자를 남기지 않음
    verify(authService, never()).login(any());
  }

  /** DB 없이 트랜잭션 흐름만 흉내 낸다: 커밋 · 롤백 여부를 기록하고, failOnCommit 이면 커밋을 실패시킨다 */
  static class FakeTransactionManager extends AbstractPlatformTransactionManager {
    boolean committed;
    boolean rolledBack;
    boolean failOnCommit;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      if (failOnCommit) {
        throw new TransactionSystemException("커밋 실패 (테스트)");
      }
      committed = true;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rolledBack = true;
    }
  }
}