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
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.repository.MemberRepository;
import com.baedalsak.member.sms.SmsSender;

import lombok.RequiredArgsConstructor;

/**
 * 내 휴대폰 번호 인증 (#164, 선택 기능) — 로그인한 회원의 DB 번호로 6자리 코드 → 맞으면 phone_verified_at 기록.
 * 인증한 번호로만 배달 알림 문자를 보낸다 (#182).
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

  /** 내 번호로 인증번호 문자를 보낸다. 이미 인증했으면 409 (문자 비용 아끼기) */
  public void sendCode(long memberId) {
    Member member = findMember(memberId);
    if (member.isPhoneVerified()) {
      throw new BusinessException(MemberErrorCode.PHONE_ALREADY_VERIFIED);
    }
    String phone = normalize(member.getPhone());

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

  /** 코드를 확인하고, 맞으면 회원에 인증 시각을 기록한다 */
  @Transactional
  public void verify(long memberId, String code) {
    Member member = findMember(memberId);
    if (member.isPhoneVerified()) {
      return;   // 이미 인증됨 — 두 번 눌러도 성공으로 (같은 결과)
    }
    String phone = normalize(member.getPhone());
    String saved = store.findCode(phone).orElseThrow(PhoneVerificationService::wrongCode);   // 없음 = 만료

    if (!sameCode(saved, code)) {
      if (store.incrementFail(phone) >= MAX_FAILS) {
        store.deleteCode(phone);
        throw new BusinessException(MemberErrorCode.PHONE_CODE_TRIES_EXCEEDED);
      }
      throw wrongCode();
    }
    store.deleteCode(phone);      // 한 번 맞힌 코드는 다시 못 씀
    member.markPhoneVerified();   // 트랜잭션 끝에 UPDATE
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
}