package com.baedalsak.member.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.logging.MaskingUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * AWS SNS 로 문자 발송 (#164). 토픽 없이 전화번호로 바로 Publish.
 *   샌드박스: 콘솔에 등록한 번호로만 감 · 계정 월 한도 1달러 · 문자 종류 Transactional(infra sms.tf)
 *   message 에 인증 코드가 들어 있으므로 로그에 남기지 않는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.sender", havingValue = "sns", matchIfMissing = true)
public class SnsSmsSender implements SmsSender {

  private final SnsClient sns;

  @Override
  public void send(String phone, String message) {
    try {
      sns.publish(r -> r.phoneNumber(toE164(phone)).message(message));
    } catch (SdkException e) {
      log.warn("SNS 문자 발송 실패 to={}", MaskingUtils.maskPhone(phone), e);
      throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  /** 010-1234-5678 → +821012345678 (SNS 는 국제 형식만 받음) */
  static String toE164(String phone) {
    String digits = phone.replaceAll("\\D", "");
    return "+82" + digits.substring(1);   // 맨 앞 0 을 떼고 국가번호 82
  }
}