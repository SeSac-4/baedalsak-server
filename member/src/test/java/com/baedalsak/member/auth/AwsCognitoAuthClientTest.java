package com.baedalsak.member.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.config.CognitoProperties;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.error.MemberErrorCode;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AliasExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

/**
 * Cognito 호출 실패를 우리 에러 코드로 바꾸는 곳 (#112 T45 Cognito 장애).
 * AWS 를 부르지 않고 SDK 클라이언트를 가짜로 바꿔, Cognito 가 낸 예외별 결과를 확인한다.
 */
@SuppressWarnings("unchecked")   // Consumer<…Builder> 를 Mockito any(Consumer.class) 로 받느라 생기는 경고
class AwsCognitoAuthClientTest {

  private final CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);
  private final AwsCognitoAuthClient client =
      new AwsCognitoAuthClient(cognito, new CognitoProperties("ap-northeast-2", "pool-id", "client-id"));

  // ── 로그인 ──

  @Test
  void 로그인_성공이면_토큰_세_가지() {
    when(cognito.adminInitiateAuth(any(Consumer.class))).thenReturn(AdminInitiateAuthResponse.builder()
        .authenticationResult(AuthenticationResultType.builder()
            .accessToken("at").refreshToken("rt").expiresIn(1800).build())
        .build());

    CognitoTokens tokens = client.login("a@test.com", "pass1234");

    assertThat(tokens.accessToken()).isEqualTo("at");
    assertThat(tokens.refreshToken()).isEqualTo("rt");
    assertThat(tokens.expiresInSeconds()).isEqualTo(1800);
  }

  @Test
  void 비밀번호가_틀리면_401() {
    when(cognito.adminInitiateAuth(any(Consumer.class)))
        .thenThrow(NotAuthorizedException.builder().message("Incorrect username or password.").build());

    assertThatThrownBy(() -> client.login("a@test.com", "wrong1234"))
        .extracting("errorCode").isEqualTo(CommonErrorCode.UNAUTHORIZED);
  }

  @Test
  void Cognito_연결_실패_시간_초과면_503() {
    when(cognito.adminInitiateAuth(any(Consumer.class)))
        .thenThrow(SdkClientException.create("Unable to execute HTTP request: connect timed out"));

    assertThatThrownBy(() -> client.login("a@test.com", "pass1234"))
        .extracting("errorCode").isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
  }

  @Test
  void 토큰_대신_추가_단계를_요구하면_401() {
    // 정상 사용자는 바로 토큰이 오지만, 비밀번호 변경 요구 같은 추가 단계(challenge)면 토큰이 없다
    when(cognito.adminInitiateAuth(any(Consumer.class)))
        .thenReturn(AdminInitiateAuthResponse.builder().challengeName("NEW_PASSWORD_REQUIRED").build());

    assertThatThrownBy(() -> client.login("a@test.com", "pass1234"))
        .extracting("errorCode").isEqualTo(CommonErrorCode.UNAUTHORIZED);
  }

  // ── 가입 ──

  @Test
  void 이메일이_Cognito_에_이미_있으면_409() {
    when(cognito.adminCreateUser(any(Consumer.class)))
        .thenThrow(AliasExistsException.builder().message("An account with the email already exists.").build());

    assertThatThrownBy(() -> client.createUser(1000L, "a@test.com", "pass1234", Role.CUSTOMER))
        .extracting("errorCode").isEqualTo(MemberErrorCode.EMAIL_DUPLICATED);
  }

  @Test
  void 사용자_생성_후_비밀번호_설정이_실패하면_만든_사용자를_지우고_503() {
    when(cognito.adminSetUserPassword(any(Consumer.class)))
        .thenThrow(SdkClientException.create("connect timed out"));

    assertThatThrownBy(() -> client.createUser(1000L, "a@test.com", "pass1234", Role.CUSTOMER))
        .extracting("errorCode").isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    verify(cognito).adminDeleteUser(any(Consumer.class));   // 반쪽 Cognito 사용자를 남기지 않음
  }

  @Test
  void 사용자_생성부터_실패하면_지울_것이_없음() {
    when(cognito.adminCreateUser(any(Consumer.class)))
        .thenThrow(SdkClientException.create("connect timed out"));

    assertThatThrownBy(() -> client.createUser(1000L, "a@test.com", "pass1234", Role.CUSTOMER))
        .extracting("errorCode").isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    verify(cognito, never()).adminDeleteUser(any(Consumer.class));
  }

  // ── 로그아웃 ──

  @Test
  void refresh_토큰_폐기가_실패해도_예외_없음() {
    when(cognito.revokeToken(any(Consumer.class))).thenThrow(SdkClientException.create("connect timed out"));

    assertThatCode(() -> client.revoke("rt")).doesNotThrowAnyException();   // 로그아웃은 항상 성공
  }
}