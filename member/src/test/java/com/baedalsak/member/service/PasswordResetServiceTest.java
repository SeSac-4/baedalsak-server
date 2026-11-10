package com.baedalsak.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.dto.PasswordResetRequest;
import com.baedalsak.member.repository.MemberRepository;

/** 비밀번호 재설정 (#162): 성공하면 회원번호로 다른 기기 로그아웃, 실패하면 로그아웃 안 함 */
class PasswordResetServiceTest {

  private final CognitoAuthClient cognito = mock(CognitoAuthClient.class);
  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private final PasswordResetService service = new PasswordResetService(cognito, memberRepository);

  private final PasswordResetRequest req = new PasswordResetRequest("a@test.com", "123456", "new12345");

  @Test
  void 재설정에_성공하면_회원번호로_전체_로그아웃() {
    Member member = mock(Member.class);
    when(member.getId()).thenReturn(1000L);
    when(memberRepository.findByEmail("a@test.com")).thenReturn(Optional.of(member));

    service.reset(req);

    verify(cognito).confirmForgotPassword("a@test.com", "123456", "new12345");
    verify(cognito).globalSignOut(1000L);   // 이메일이 아니라 username(회원번호)으로
  }

  @Test
  void 코드가_틀려_재설정이_실패하면_로그아웃하지_않음() {
    doThrow(new BusinessException(CommonErrorCode.VALIDATION_FAILED))
        .when(cognito).confirmForgotPassword("a@test.com", "123456", "new12345");

    assertThatThrownBy(() -> service.reset(req)).isInstanceOf(BusinessException.class);

    verify(cognito, never()).globalSignOut(anyLong());
  }
}
