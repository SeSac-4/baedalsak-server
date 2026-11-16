package com.baedalsak.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.member.auth.EmailVerificationStore;
import com.baedalsak.member.config.EmailProperties;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.mail.EmailSender;
import com.baedalsak.member.repository.MemberRepository;

/** 가입 전 이메일 인증 규칙 (#207): Redis · 메일은 가짜(mock)로 두고 서비스 규칙만 검사 */
class EmailVerificationServiceTest {

  private static final String EMAIL = "hj.test@test.baedalsak.click";   // Redis 키는 소문자

  private final EmailVerificationStore store = mock(EmailVerificationStore.class);
  private final EmailSender emailSender = mock(EmailSender.class);
  private final MemberRepository memberRepository = mock(MemberRepository.class);

  private EmailVerificationService service(boolean required) {
    return new EmailVerificationService(store, emailSender, memberRepository, new EmailProperties(required));
  }

  private EmailVerificationService service() {
    return service(true);   // 기본값과 같게 필수
  }

  private void sendAllowed() {
    when(store.tryStartCooldown(EMAIL)).thenReturn(true);
    when(store.incrementDaily(EMAIL)).thenReturn(1L);
  }

  // ---------- 코드 보내기 ----------

  @Test
  void 코드를_저장하고_메일로_보낸다_대문자_공백은_맞춘다() {
    sendAllowed();

    service().sendSignupCode("  HJ.Test@test.baedalsak.click ");

    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(store).saveCode(eq(EMAIL), code.capture());
    assertThat(code.getValue()).matches("\\d{6}");
    verify(emailSender).send(eq(EMAIL), anyString(), contains(code.getValue()));
  }

  @Test
  void 이미_가입된_이메일이면_409_메일_안_보냄() {
    when(memberRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> service().sendSignupCode(EMAIL))
        .extracting("errorCode").isEqualTo(MemberErrorCode.EMAIL_DUPLICATED);
    verify(store, never()).tryStartCooldown(anyString());
    verify(emailSender, never()).send(anyString(), anyString(), anyString());
  }

  @Test
  void 일분_안에_다시_받으면_429() {
    when(store.tryStartCooldown(EMAIL)).thenReturn(false);

    assertThatThrownBy(() -> service().sendSignupCode(EMAIL))
        .extracting("errorCode").isEqualTo(MemberErrorCode.EMAIL_CODE_COOLDOWN);
    verify(emailSender, never()).send(anyString(), anyString(), anyString());
  }

  @Test
  void 하루_다섯_통을_넘으면_429() {
    when(store.tryStartCooldown(EMAIL)).thenReturn(true);
    when(store.incrementDaily(EMAIL)).thenReturn(6L);

    assertThatThrownBy(() -> service().sendSignupCode(EMAIL))
        .extracting("errorCode").isEqualTo(MemberErrorCode.EMAIL_CODE_DAILY_LIMIT);
    verify(emailSender, never()).send(anyString(), anyString(), anyString());
  }

  @Test
  void 메일_발송이_실패하면_503_재발송_간격을_풀어_준다() {
    sendAllowed();
    doThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE))
        .when(emailSender).send(anyString(), anyString(), anyString());

    assertThatThrownBy(() -> service().sendSignupCode(EMAIL))
        .extracting("errorCode").isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    verify(store).clearCooldown(EMAIL);
  }

  // ---------- 코드 확인 ----------

  @Test
  void 코드가_맞으면_코드를_지우고_표를_준다() {
    when(store.findCode(EMAIL)).thenReturn(Optional.of("123456"));
    when(store.saveVerified(EMAIL)).thenReturn("token-1");

    assertThat(service().verifySignupCode(EMAIL, "123456")).isEqualTo("token-1");
    verify(store).deleteCode(EMAIL);
  }

  @Test
  void 코드가_틀리면_400_errors_code() {
    when(store.findCode(EMAIL)).thenReturn(Optional.of("123456"));
    when(store.incrementFail(EMAIL)).thenReturn(1L);

    assertThatThrownBy(() -> service().verifySignupCode(EMAIL, "000000"))
        .extracting("errors").asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactly(new FieldErrorDetail("code", "인증번호가 올바르지 않거나 만료되었습니다."));
    verify(store, never()).saveVerified(anyString());
  }

  @Test
  void 다섯_번째로_틀리면_코드를_버리고_429() {
    when(store.findCode(EMAIL)).thenReturn(Optional.of("123456"));
    when(store.incrementFail(EMAIL)).thenReturn(5L);

    assertThatThrownBy(() -> service().verifySignupCode(EMAIL, "000000"))
        .extracting("errorCode").isEqualTo(MemberErrorCode.EMAIL_CODE_TRIES_EXCEEDED);
    verify(store).deleteCode(EMAIL);
  }

  @Test
  void 코드가_만료됐으면_400() {
    when(store.findCode(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().verifySignupCode(EMAIL, "123456"))
        .extracting("errorCode").isEqualTo(CommonErrorCode.VALIDATION_FAILED);
  }

  // ---------- 가입 때 표 확인 ----------

  @Test
  void 표가_이_이메일_것이면_통과_대소문자는_무시() {
    when(store.consumeVerified("token-1")).thenReturn(Optional.of(EMAIL));

    assertThatCode(() -> service().confirmSignupToken("HJ.TEST@test.baedalsak.click", "token-1"))
        .doesNotThrowAnyException();
  }

  @Test
  void 표가_없으면_400_필수() {
    assertThatThrownBy(() -> service().confirmSignupToken(EMAIL, null))
        .extracting("errors").asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactly(new FieldErrorDetail("emailVerificationToken", "이메일 인증을 다시 해 주세요."));
    verify(store, never()).consumeVerified(anyString());
  }

  @Test
  void 스위치를_끄면_표_없이_통과_부하_테스트용() {
    assertThatCode(() -> service(false).confirmSignupToken(EMAIL, null)).doesNotThrowAnyException();
  }

  @Test
  void 다른_이메일의_표거나_만료_재사용이면_400() {
    when(store.consumeVerified("other")).thenReturn(Optional.of("someone@else.com"));
    when(store.consumeVerified("used")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().confirmSignupToken(EMAIL, "other"))
        .extracting("errorCode").isEqualTo(CommonErrorCode.VALIDATION_FAILED);
    assertThatThrownBy(() -> service().confirmSignupToken(EMAIL, "used"))
        .extracting("errorCode").isEqualTo(CommonErrorCode.VALIDATION_FAILED);
  }
}
