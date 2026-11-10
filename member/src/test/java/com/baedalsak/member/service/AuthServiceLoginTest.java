package com.baedalsak.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.auth.CognitoTokens;
import com.baedalsak.member.auth.LoginResult;
import com.baedalsak.member.auth.RefreshSessionStore;
import com.baedalsak.member.auth.TokenBlacklistStore;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.dto.LoginRequest;
import com.baedalsak.member.repository.MemberRepository;

/**
 * 로그인 (#112 T47 · T45): 성공하면 쿠키용 값, 실패하면 원인별 상태 코드.
 * 쿠키는 세션 저장까지 끝나야만 나간다 — 중간에 실패하면 결과(LoginResult) 자체가 없다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

  private static final String EMAIL = "seed-customer1@baedalsak.click";
  private static final LoginRequest REQUEST = new LoginRequest(EMAIL, "pass1234");
  private static final CognitoTokens TOKENS = new CognitoTokens("access-token", "refresh-token", 1800);

  @Mock TokenBlacklistStore tokenBlacklistStore;
  @Mock CognitoAuthClient cognitoAuthClient;
  @Mock RefreshSessionStore refreshSessionStore;
  @Mock MemberRepository memberRepository;
  @InjectMocks AuthService authService;

  /** DB 에 이 이메일의 회원이 있는 것처럼 */
  private Member memberExists() {
    Member member = mock(Member.class);
    when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
    return member;
  }

  @Test
  void 성공하면_토큰_세션ID_회원정보() {
    when(cognitoAuthClient.login(EMAIL, "pass1234")).thenReturn(TOKENS);
    Member member = memberExists();
    when(member.getId()).thenReturn(1L);
    when(member.getRole()).thenReturn(Role.CUSTOMER);
    when(refreshSessionStore.save("refresh-token")).thenReturn("session-1");

    LoginResult result = authService.login(REQUEST);

    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.accessMaxAge()).isEqualTo(Duration.ofMinutes(30));   // Cognito expiresIn 1800초
    assertThat(result.sessionId()).isEqualTo("session-1");                 // refresh 토큰은 밖으로 안 나감
    assertThat(result.body().memberId()).isEqualTo(1L);
    assertThat(result.body().role()).isEqualTo(Role.CUSTOMER);
  }

  @Test
  void 비밀번호가_틀리면_401_세션_저장_안함() {
    when(cognitoAuthClient.login(EMAIL, "pass1234"))
        .thenThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED));

    assertThatThrownBy(() -> authService.login(REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(CommonErrorCode.UNAUTHORIZED);
    verify(refreshSessionStore, never()).save(anyString());
  }

  @Test
  void Cognito_장애면_503_세션_저장_안함() {
    when(cognitoAuthClient.login(EMAIL, "pass1234"))
        .thenThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE));

    assertThatThrownBy(() -> authService.login(REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    verify(refreshSessionStore, never()).save(anyString());
  }

  @Test
  void Cognito_에는_있는데_DB_에_없으면_500() {
    when(cognitoAuthClient.login(EMAIL, "pass1234")).thenReturn(TOKENS);
    when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_ERROR);
    verify(refreshSessionStore, never()).save(anyString());
  }

  @Test
  void Redis_장애로_세션을_못_남기면_503() {
    when(cognitoAuthClient.login(EMAIL, "pass1234")).thenReturn(TOKENS);
    memberExists();
    when(refreshSessionStore.save("refresh-token"))
        .thenThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE));

    // 예외로 끝나므로 LoginResult(쿠키 값)가 만들어지지 않음 → 반쪽 로그인 없음 (A안)
    assertThatThrownBy(() -> authService.login(REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
  }
}