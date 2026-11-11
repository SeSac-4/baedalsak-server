package com.baedalsak.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.member.auth.PhoneVerificationStore;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.sms.SmsSender;

import lombok.RequiredArgsConstructor;

/**
 * 휴대폰 번호 소유 확인 (#164) — 6자리 코드 문자 → 입력 → 맞으면 "인증 완료 표"(10분, 1회용).
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

  /** 코드를 만들어 문자로 보낸다 */
  public void sendCode(String rawPhone) {
    String phone = normalize(rawPhone);

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

  /** 코드를 확인하고, 맞으면 가입 때 낼 인증 완료 표를 돌려준다 */
  public String verify(String rawPhone, String code) {
    String phone = normalize(rawPhone);
    String saved = store.findCode(phone).orElseThrow(PhoneVerificationService::wrongCode);   // 없음 = 만료

    if (!sameCode(saved, code)) {
      if (store.incrementFail(phone) >= MAX_FAILS) {
        store.deleteCode(phone);
        throw new BusinessException(MemberErrorCode.PHONE_CODE_TRIES_EXCEEDED);
      }
      throw wrongCode();
    }
    store.deleteCode(phone);   // 한 번 맞힌 코드는 다시 못 씀
    return store.saveVerified(phone);
  }

  /** 가입(3단계)에서 호출: 표가 이 번호 것인지 확인하고 표를 없앤다. 아니면 400 */
  public void confirmVerified(String rawPhone, String token) {
    String phone = normalize(rawPhone);
    boolean ok = token != null && store.consumeVerified(token).map(phone::equals).orElse(false);
    if (!ok) {
      throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
          List.of(new FieldErrorDetail("phoneVerificationToken", "휴대폰 인증을 먼저 완료해 주세요.")));
    }
  }

  static String normalize(String phone) {
    return phone.replaceAll("\\D", "");   // 010-1234-5678 → 01012345678
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
}