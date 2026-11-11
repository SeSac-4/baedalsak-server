package com.baedalsak.member.service;

import static org.assertj.core.api.Assertions.assertThat;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.member.auth.PhoneVerificationStore;
import com.baedalsak.member.config.SmsProperties;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.repository.MemberRepository;
import com.baedalsak.member.sms.SmsSender;

/** 휴대폰 인증 규칙 (#164): Redis · 문자는 가짜(mock)로 두고 서비스 규칙만 검사 */
class PhoneVerificationServiceTest {

  private static final String PHONE = "01012345678";   // DB · 요청은 하이픈 있게, Redis 키는 숫자만

  private final PhoneVerificationStore store = mock(PhoneVerificationStore.class);
  private final SmsSender smsSender = mock(SmsSender.class);
  private final MemberRepository memberRepository = mock(MemberRepository.class);

  private Member member;

  @BeforeEach
  void setUp() {
    member = new Member("a@test.com", "고객", "010-1234-5678", Role.CUSTOMER);
    when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
  }

  private PhoneVerificationService service(boolean required) {
    return new PhoneVerificationService(store, smsSender, memberRepository, new SmsProperties(required, true));
  }

  private PhoneVerificationService service() {
    return service(false);
  }

  private void sendAllowed() {
    when(store.tryStartCooldown(PHONE)).thenReturn(true);
    when(store.incrementDaily(PHONE)).thenReturn(1L);
  }

  private static void assertErrorCode(Throwable thrown, Object code) {
    assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(code));
  }

  // ---------- 코드 보내기 (공통 규칙) ----------

  @Test
  void 코드를_저장하고_같은_코드를_문자로() {
    sendAllowed();

    service().sendSignupCode("010-1234-5678");

    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(store).saveCode(eq(PHONE), code.capture());
    assertThat(code.getValue()).matches("\\d{6}");
    verify(smsSender).send(eq(PHONE), contains(code.getValue()));
  }

  @Test
  void 육십초_안에_다시_받으면_429이고_하루_횟수는_안_올림() {
    when(store.tryStartCooldown(PHONE)).thenReturn(false);

    assertErrorCode(catchThrown(() -> service().sendSignupCode(PHONE)), MemberErrorCode.PHONE_CODE_COOLDOWN);
    verify(store, never()).incrementDaily(anyString());
    verify(smsSender, never()).send(anyString(), anyString());
  }

  @Test
  void 하루_다섯_통을_넘으면_429() {
    when(store.tryStartCooldown(PHONE)).thenReturn(true);
    when(store.incrementDaily(PHONE)).thenReturn(6L);

    assertErrorCode(catchThrown(() -> service().sendSignupCode(PHONE)), MemberErrorCode.PHONE_CODE_DAILY_LIMIT);
    verify(smsSender, never()).send(anyString(), anyString());
  }

  @Test
  void 문자_발송이_실패하면_간격을_풀고_503을_그대로() {
    sendAllowed();
    doThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE))
        .when(smsSender).send(anyString(), anyString());

    assertErrorCode(catchThrown(() -> service().sendSignupCode(PHONE)), CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    verify(store).clearCooldown(PHONE);
  }

  // ---------- 코드 확인 (공통 규칙) ----------

  @Test
  void 코드가_틀리면_400_code_필드이고_틀린_횟수_올림() {
    when(store.findCode(PHONE)).thenReturn(Optional.of("123456"));
    when(store.incrementFail(PHONE)).thenReturn(1L);

    assertThatThrownBy(() -> service().verifySignupCode(PHONE, "000000"))
        .isInstanceOfSatisfying(BusinessException.class, e -> {
          assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
          assertThat(e.getErrors()).extracting(FieldErrorDetail::field).containsExactly("code");
        });
    verify(store, never()).saveVerified(anyString());
  }

  @Test
  void 다섯_번째로_틀리면_429이고_코드_폐기() {
    when(store.findCode(PHONE)).thenReturn(Optional.of("123456"));
    when(store.incrementFail(PHONE)).thenReturn(5L);

    assertErrorCode(catchThrown(() -> service().verifySignupCode(PHONE, "000000")),
        MemberErrorCode.PHONE_CODE_TRIES_EXCEEDED);
    verify(store).deleteCode(PHONE);
  }

  @Test
  void 코드가_없으면_만료로_보고_400이고_횟수는_안_셈() {
    when(store.findCode(PHONE)).thenReturn(Optional.empty());

    assertErrorCode(catchThrown(() -> service().verifySignupCode(PHONE, "123456")), CommonErrorCode.VALIDATION_FAILED);
    verify(store, never()).incrementFail(anyString());
  }

  // ---------- 가입 전 ----------

  @Test
  void 가입_전_코드가_맞으면_코드를_지우고_인증_표를_돌려줌() {
    when(store.findCode(PHONE)).thenReturn(Optional.of("123456"));
    when(store.saveVerified(PHONE)).thenReturn("token-1");

    String token = service().verifySignupCode("010-1234-5678", "123456");

    assertThat(token).isEqualTo("token-1");
    verify(store).deleteCode(PHONE);
  }

  @Test
  void 가입_표가_없고_스위치가_꺼져_있으면_미인증으로_통과() {
    assertThat(service(false).confirmSignupToken("010-1234-5678", null)).isFalse();
    assertThat(service(false).confirmSignupToken("010-1234-5678", " ")).isFalse();
  }

  @Test
  void 가입_표가_없고_스위치가_켜져_있으면_400() {
    assertThatThrownBy(() -> service(true).confirmSignupToken("010-1234-5678", null))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getErrors()).extracting(FieldErrorDetail::field).containsExactly("phoneVerificationToken"));
  }

  @Test
  void 가입_표가_이_번호_것이면_인증됨() {
    when(store.consumeVerified("token-1")).thenReturn(Optional.of(PHONE));

    assertThat(service().confirmSignupToken("010-1234-5678", "token-1")).isTrue();
  }

  @Test
  void 다른_번호로_받은_표나_만료된_표는_스위치와_관계없이_400() {
    when(store.consumeVerified("token-other")).thenReturn(Optional.of("01099998888"));
    when(store.consumeVerified("token-expired")).thenReturn(Optional.empty());

    assertErrorCode(catchThrown(() -> service(false).confirmSignupToken(PHONE, "token-other")),
        CommonErrorCode.VALIDATION_FAILED);
    assertErrorCode(catchThrown(() -> service(false).confirmSignupToken(PHONE, "token-expired")),
        CommonErrorCode.VALIDATION_FAILED);
  }

  // ---------- 가입 후 (내 정보) ----------

  @Test
  void 내_번호로_코드를_보냄() {
    sendAllowed();

    service().sendCode(1L);

    verify(smsSender).send(eq(PHONE), anyString());
  }

  @Test
  void 이미_인증했으면_409이고_문자_안_보냄() {
    member.markPhoneVerified();

    assertErrorCode(catchThrown(() -> service().sendCode(1L)), MemberErrorCode.PHONE_ALREADY_VERIFIED);
    verify(smsSender, never()).send(anyString(), anyString());
  }

  @Test
  void 내_번호_코드가_맞으면_인증_기록() {
    when(store.findCode(PHONE)).thenReturn(Optional.of("123456"));

    service().verify(1L, "123456");

    assertThat(member.isPhoneVerified()).isTrue();
    verify(store).deleteCode(PHONE);
  }

  @Test
  void 내_번호_코드가_틀리면_인증_안_됨() {
    when(store.findCode(PHONE)).thenReturn(Optional.of("123456"));
    when(store.incrementFail(PHONE)).thenReturn(1L);

    assertErrorCode(catchThrown(() -> service().verify(1L, "000000")), CommonErrorCode.VALIDATION_FAILED);
    assertThat(member.isPhoneVerified()).isFalse();
  }

  private static Throwable catchThrown(Runnable action) {
    return org.assertj.core.api.Assertions.catchThrowable(action::run);
  }
}
