package com.baedalsak.member.auth;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.member.config.CognitoProperties;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.error.MemberErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AliasExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/** CognitoAuthClient 의 AWS SDK 구현 (B안: username = member_id, 역할 = 그룹). 비밀번호·토큰은 로그에 남기지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AwsCognitoAuthClient implements CognitoAuthClient {

  private final CognitoIdentityProviderClient cognito;
  private final CognitoProperties props;

  @Override
  public void createUser(long memberId, String email, String password, Role role) {
    String username = String.valueOf(memberId);

    // ① 사용자 생성 — 초대 메일 안 보냄(SUPPRESS), 이메일은 인증된 것으로 표시해 바로 로그인 가능
    try {
      cognito.adminCreateUser(r -> r
          .userPoolId(props.userPoolId())
          .username(username)
          .messageAction(MessageActionType.SUPPRESS)
          .userAttributes(
              AttributeType.builder().name("email").value(email).build(),
              AttributeType.builder().name("email_verified").value("true").build()));
    } catch (AliasExistsException e) {
      throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
    } catch (UsernameExistsException e) {
      log.error("Cognito 에 같은 username 이 이미 있음 — DB·Cognito 불일치 memberId={}", memberId);
      throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
    } catch (SdkException e) {
      throw unavailable("AdminCreateUser", e);
    }

    // ② 영구 비밀번호 ③ 역할 그룹 — 여기서 실패하면 ①에서 만든 사용자를 지우고 끝낸다
    try {
      cognito.adminSetUserPassword(r -> r
          .userPoolId(props.userPoolId())
          .username(username)
          .password(password)
          .permanent(true));
      cognito.adminAddUserToGroup(r -> r
          .userPoolId(props.userPoolId())
          .username(username)
          .groupName(role.name()));
    } catch (InvalidPasswordException e) {
      deleteUser(memberId);
      throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
          List.of(new FieldErrorDetail("password", "비밀번호는 영문 소문자와 숫자를 포함해 8자 이상이어야 합니다.")));
    } catch (SdkException e) {
      deleteUser(memberId);
      throw unavailable("AdminSetUserPassword/AdminAddUserToGroup", e);
    }
  }

  @Override
  public void deleteUser(long memberId) {
    // 보상 단계라 예외를 던지지 않는다 — 실패하면 로그로 남겨 수동 정리
    try {
      cognito.adminDeleteUser(r -> r
          .userPoolId(props.userPoolId())
          .username(String.valueOf(memberId)));
    } catch (UserNotFoundException e) {
      // 이미 없음 → 지울 것도 없음
    } catch (SdkException e) {
      log.error("Cognito 사용자 삭제 실패 — 수동 정리 필요 memberId={}", memberId, e);
    }
  }

  @Override
  public CognitoTokens login(String email, String password) {
    try {
      AdminInitiateAuthResponse res = cognito.adminInitiateAuth(r -> r
          .userPoolId(props.userPoolId())
          .clientId(props.clientId())
          .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
          .authParameters(Map.of("USERNAME", email, "PASSWORD", password)));
      return toTokens(res);
    } catch (NotAuthorizedException | UserNotFoundException e) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);   // 이메일·비밀번호 중 무엇이 틀렸는지 알려 주지 않음
    } catch (SdkException e) {
      throw unavailable("AdminInitiateAuth(로그인)", e);
    }
  }

  @Override
  public CognitoTokens refresh(String refreshToken) {
    try {
      AdminInitiateAuthResponse res = cognito.adminInitiateAuth(r -> r
          .userPoolId(props.userPoolId())
          .clientId(props.clientId())
          .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
          .authParameters(Map.of("REFRESH_TOKEN", refreshToken)));
      return toTokens(res);
    } catch (NotAuthorizedException | UserNotFoundException e) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);   // 만료·폐기된 refresh → 다시 로그인
    } catch (SdkException e) {
      throw unavailable("AdminInitiateAuth(갱신)", e);
    }
  }

  @Override
  public void revoke(String refreshToken) {
    // 로그아웃은 항상 성공해야 하므로 실패해도 경고만 남긴다 (설계서 8.2.2)
    try {
      cognito.revokeToken(r -> r
          .clientId(props.clientId())
          .token(refreshToken));
    } catch (SdkException e) {
      log.warn("Cognito RevokeToken 실패 — 로그아웃은 계속 진행", e);
    }
  }

  // 정상이라면 비밀번호 변경 같은 추가 단계(challenge) 없이 토큰이 바로 온다
  private CognitoTokens toTokens(AdminInitiateAuthResponse res) {
    AuthenticationResultType result = res.authenticationResult();
    if (result == null) {
      log.warn("Cognito 가 토큰 대신 추가 단계를 요구함 challenge={}", res.challengeNameAsString());
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
    return new CognitoTokens(result.accessToken(), result.refreshToken(), result.expiresIn());
  }

  private BusinessException unavailable(String api, SdkException e) {
    log.warn("Cognito {} 호출 실패", api, e);
    return new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
  }
}