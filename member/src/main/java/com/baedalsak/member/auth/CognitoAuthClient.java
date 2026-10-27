package com.baedalsak.member.auth;

import com.baedalsak.member.domain.Role;

/**
 * member 가 Cognito 에 시키는 일 (B안: username = member_id, 역할 = 그룹).
 * 실패는 BusinessException 으로 던진다:
 *   이메일 중복 409 EMAIL_DUPLICATED / 로그인·갱신 실패 401 UNAUTHORIZED / Cognito 장애·시간 초과 503 DEPENDENCY_UNAVAILABLE
 */
public interface CognitoAuthClient {

  /**
   * 가입: AdminCreateUser(username=memberId, 메일 발송 안 함) → AdminSetUserPassword(영구) → AdminAddUserToGroup(role).
   * 중간 단계가 실패하면 만든 Cognito 사용자를 스스로 지우고 예외를 던진다 (DB 롤백은 호출한 쪽 몫).
   */
  void createUser(long memberId, String email, String password, Role role);

  /** 가입 보상: DB 커밋이 실패했을 때 이미 만든 Cognito 사용자를 지운다. 없으면 조용히 무시. */
  void deleteUser(long memberId);

  /** 로그인: AdminInitiateAuth(ADMIN_USER_PASSWORD_AUTH, 이메일로 로그인). */
  CognitoTokens login(String email, String password);

  /** 갱신: AdminInitiateAuth(REFRESH_TOKEN_AUTH). 새 accessToken 만 오고 refreshToken 은 null. */
  CognitoTokens refresh(String refreshToken);

  /** 로그아웃: RevokeToken 으로 refresh 토큰 폐기. 이미 폐기·만료된 토큰이어도 성공으로 본다. */
  void revoke(String refreshToken);
}