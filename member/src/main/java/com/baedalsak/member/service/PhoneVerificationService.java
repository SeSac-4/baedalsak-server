package com.baedalsak.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.member.auth.PhoneVerificationStore;
import com.baedalsak.member.config.SmsProperties;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.repository.MemberRepository;
import com.baedalsak.member.sms.SmsSender;

import lombok.RequiredArgsConstructor;

/**
 * 휴대폰 번호 인증 (#164) — 6자리 코드 문자 → 입력 → 맞으면 인증. 인증한 번호로만 알림 문자를 보낸다 (#182).
 * 입구 두 개:
 *   가입 전  : 번호로 코드 → 맞으면 "인증 완료 표"(10분, 1회용) → 가입 요청에 실어 보내면 가입과 동시에 인증 기록
 *   가입 후  : 로그인한 회원의 DB 번호로 코드 → 맞으면 phone_verified_at 기록 (가입 때 건너뛴 사람 · 기존 회원)
 * 가입 때 인증 필수 여부는 sms.verification-required (SMS 샌드박스 동안 false = 건너뛰기 허용)
 * 규칙: 코드 3분 · 5번 틀리면 폐기 · 재발송 60초 간격 · 번호당 하루 5통
 */
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

  static final int MAX_FAILS = 5;
  static final int DAILY_LIMIT = 5;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final PhoneVerificationStore store;
  private final SmsSender smsSender;
  private final MemberRepository memberRepository;
  private final SmsProperties props;

  // ---------- 가입 전 (로그인 없음) ----------

  /** 가입 화면: 입력한 번호로 인증번호 문자를 보낸다 */
  public void sendSignupCode(String rawPhone) {
    sendTo(normalize(rawPhone));
  }

  /** 가입 화면: 코드를 확인하고, 맞으면 가입 요청에 실을 인증 완료 표를 돌려준다 */
  public String verifySignupCode(String rawPhone, String code) {
    String phone = normalize(rawPhone);
    checkCode(phone, code);
    return store.saveVerified(phone);
  }

  /**
   * 가입 처리 중 호출: 표가 이 번호 것이면 true (인증된 회원으로 가입).
   * 표가 없으면 스위치가 켜졌을 때만 400, 꺼져 있으면 false (미인증으로 가입). 표가 틀리면 항상 400.
   */
  public boolean confirmSignupToken(String rawPhone, String token) {
    if (token == null || token.isBlank()) {
      if (props.verificationRequired()) {
        throw tokenInvalid();
      }
      return false;
    }
    String phone = normalize(rawPhone);
    boolean ok = store.consumeVerified(token).map(phone::equals).orElse(false);
    if (!ok) {
      throw tokenInvalid();   // 만료 · 이미 사용 · 다른 번호의 표
    }
    return true;
  }

  // ---------- 가입 후 (내 정보) ----------

  /** 내 번호로 인증번호 문자를 보낸다. 이미 인증했으면 409 (문자 비용 아끼기) */
  public void sendCode(long memberId) {
    Member member = findMember(memberId);
    if (member.isPhoneVerified()) {
      throw new BusinessException(MemberErrorCode.PHONE_ALREADY_VERIFIED);
    }
    sendTo(normalize(member.getPhone()));
  }

  /** 코드를 확인하고, 맞으면 회원에 인증 시각을 기록한다 */
  @Transactional
  public void verify(long memberId, String code) {
    Member member = findMember(memberId);
    if (member.isPhoneVerified()) {
      return;   // 이미 인증됨 — 두 번 눌러도 성공으로 (같은 결과)
    }
    checkCode(normalize(member.getPhone()), code);
    member.markPhoneVerified();   // 트랜잭션 끝에 UPDATE
  }

  // ---------- 공통 ----------

  private void sendTo(String phone) {
    // ① 60초 간격 — 먼저 표시를 남겨서 동시에 두 번 눌러도 한 번만 통과
    if (!store.tryStartCooldown(phone)) {
      throw new BusinessException(MemberErrorCode.PHONE_CODE_COOLDOWN);
    }
    // ② 하루 한도
    if (store.incrementDaily(phone) > DAILY_LIMIT) {
      throw new BusinessException(MemberErrorCode.PHONE_CODE_DAILY_LIMIT);
    }
    // ③ 저장 후 발송 — 발송이 실패하면(503) 바로 다시 시도할 수 있게 간격만 풀어 줌 (하루 횟수는 그대로)
    String code = newCode();
    store.saveCode(phone, code);
    try {
      smsSender.send(phone, "[배달싹] 인증번호 [" + code + "]를 입력해 주세요. (3분 이내)");
    } catch (BusinessException e) {
      store.clearCooldown(phone);
      throw e;
    }
  }

  // 맞으면 코드를 지우고 끝, 틀리면 예외 (5번째면 코드 폐기 + 429)
  private void checkCode(String phone, String code) {
    String saved = store.findCode(phone).orElseThrow(PhoneVerificationService::wrongCode);   // 없음 = 만료
    if (!sameCode(saved, code)) {
      if (store.incrementFail(phone) >= MAX_FAILS) {
        store.deleteCode(phone);
        throw new BusinessException(MemberErrorCode.PHONE_CODE_TRIES_EXCEEDED);
      }
      throw wrongCode();
    }
    store.deleteCode(phone);   // 한 번 맞힌 코드는 다시 못 씀
  }

  private Member findMember(long memberId) {
    return memberRepository.findById(memberId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
  }

  static String normalize(String phone) {
    return phone.replaceAll("\\D", "");   // 010-1234-5678 → 01012345678 (Redis 키를 하나로)
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
        List.of(new FieldErrorDetail("phoneVerificationToken", "휴대폰 인증을 다시 해 주세요.")));
  }
}
