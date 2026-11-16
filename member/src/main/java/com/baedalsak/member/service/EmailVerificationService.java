package com.baedalsak.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.member.auth.EmailVerificationStore;
import com.baedalsak.member.config.EmailProperties;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.mail.EmailSender;
import com.baedalsak.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 가입 전 이메일 인증 (#207) — 휴대폰 가입 전 인증(#164)과 같은 흐름. 이메일 오타로 가입하는 것을 막는다.
 *   코드 메일 → 입력 → 맞으면 "인증 완료 표"(10분, 1회용) → 가입 요청에 실어 보내면 통과
 * 가입 버튼을 누르기 전에는 DB · Cognito 에 아무것도 만들지 않는다 (중간에 나가도 남는 것 없음).
 * Cognito 사용자는 지금처럼 email_verified=true 로 만들어지므로 로그인 · 비밀번호 찾기는 그대로.
 * 가입 때 인증은 필수 — 주문·결제·배달 알림이 메일로 가서 주소가 맞아야 한다.
 *   email.verification-required (기본 true). 부하 테스트 날만 false 로 건너뛰기 허용
 * 규칙: 코드 3분 · 5번 틀리면 폐기 · 재발송 60초 간격 · 이메일당 하루 5통
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

  static final int MAX_FAILS = 5;
  static final int DAILY_LIMIT = 5;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final EmailVerificationStore store;
  private final EmailSender emailSender;
  private final MemberRepository memberRepository;
  private final EmailProperties props;

  /** 가입 화면: 입력한 이메일로 인증번호 메일을 보낸다. 이미 가입된 이메일이면 409 (메일 안 보냄) */
  public void sendSignupCode(String rawEmail) {
    String email = normalize(rawEmail);
    if (memberRepository.existsByEmailIgnoreCase(email)) {
      throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
    }
    // ① 60초 간격 — 먼저 표시를 남겨서 동시에 두 번 눌러도 한 번만 통과
    if (!store.tryStartCooldown(email)) {
      throw new BusinessException(MemberErrorCode.EMAIL_CODE_COOLDOWN);
    }
    // ② 하루 한도
    if (store.incrementDaily(email) > DAILY_LIMIT) {
      throw new BusinessException(MemberErrorCode.EMAIL_CODE_DAILY_LIMIT);
    }
    // ③ 저장 후 발송 — 발송이 실패하면(503) 바로 다시 시도할 수 있게 간격만 풀어 줌 (하루 횟수는 그대로)
    String code = newCode();
    store.saveCode(email, code);
    try {
      emailSender.send(email, "[배달싹] 이메일 인증번호",
          "배달싹 가입 인증번호는 [" + code + "] 입니다.\n3분 안에 가입 화면에 입력해 주세요.\n"
              + "직접 요청하지 않았다면 이 메일은 무시하셔도 됩니다.");
    } catch (BusinessException e) {
      store.clearCooldown(email);
      throw e;
    }
  }

  /** 가입 화면: 코드를 확인하고, 맞으면 가입 요청에 실을 인증 완료 표를 돌려준다 */
  public String verifySignupCode(String rawEmail, String code) {
    String email = normalize(rawEmail);
    String saved = store.findCode(email).orElseThrow(EmailVerificationService::wrongCode);   // 없음 = 만료
    if (!sameCode(saved, code)) {
      if (store.incrementFail(email) >= MAX_FAILS) {
        store.deleteCode(email);
        throw new BusinessException(MemberErrorCode.EMAIL_CODE_TRIES_EXCEEDED);
      }
      throw wrongCode();
    }
    store.deleteCode(email);   // 한 번 맞힌 코드는 다시 못 씀
    return store.saveVerified(email);
  }

  /**
   * 가입 처리 중 호출: 표가 이 이메일 것이면 통과.
   * 표가 없으면 스위치가 켜졌을 때만 400, 꺼져 있으면 그냥 통과(건너뛰기). 표가 틀리면 항상 400.
   */
  public void confirmSignupToken(String rawEmail, String token) {
    if (token == null || token.isBlank()) {
      if (props.verificationRequired()) {
        throw tokenInvalid();
      }
      return;
    }
    String email = normalize(rawEmail);
    boolean ok = store.consumeVerified(token).map(email::equals).orElse(false);
    if (!ok) {
      throw tokenInvalid();   // 만료 · 이미 사용 · 다른 이메일의 표
    }
  }

  // Redis 키와 비교를 하나로 — 앞뒤 공백 제거 + 소문자 (Cognito 이메일 로그인도 대소문자를 구분하지 않음)
  static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static String newCode() {
    return String.format("%06d", RANDOM.nextInt(1_000_000));   // 000000 ~ 999999
  }

  // 글자를 하나씩 비교하다 먼저 끝나지 않게 (응답 시간으로 코드를 추측하는 공격 방지)
  private static boolean sameCode(String saved, String input) {
    return input != null && MessageDigest.isEqual(
        saved.getBytes(StandardCharsets.UTF_8), input.getBytes(StandardCharsets.UTF_8));
  }

  private static BusinessException wrongCode() {
    return new BusinessException(CommonErrorCode.VALIDATION_FAILED,
        List.of(new FieldErrorDetail("code", "인증번호가 올바르지 않거나 만료되었습니다.")));
  }

  private static BusinessException tokenInvalid() {
    return new BusinessException(CommonErrorCode.VALIDATION_FAILED,
        List.of(new FieldErrorDetail("emailVerificationToken", "이메일 인증을 다시 해 주세요.")));
  }
}
