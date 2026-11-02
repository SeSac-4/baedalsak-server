package com.baedalsak.member.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.auth.RefreshSessionStore;
import com.baedalsak.member.auth.TokenBlacklistStore;
import com.baedalsak.member.repository.MemberRepository;

/** 로그아웃 (#111): gateway 가 넘긴 jti 가 있을 때만 블랙리스트 등록 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLogoutTest {

  @Mock TokenBlacklistStore tokenBlacklistStore;
  @Mock CognitoAuthClient cognitoAuthClient;
  @Mock RefreshSessionStore refreshSessionStore;
  @Mock MemberRepository memberRepository;
  @InjectMocks AuthService authService;

  @Test
  void jti가_오면_세션이_없어도_블랙리스트_등록() {
    authService.logout(null, "jti-1", 2_000_000_000L);

    verify(tokenBlacklistStore).add("jti-1", 2_000_000_000L);
  }

  @Test
  void jti가_없으면_등록하지_않음() {
    authService.logout(null, null, null);

    verify(tokenBlacklistStore, never()).add(anyString(), anyLong());
  }
}
