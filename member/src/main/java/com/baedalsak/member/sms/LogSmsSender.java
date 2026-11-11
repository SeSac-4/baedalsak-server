package com.baedalsak.member.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.baedalsak.common.logging.MaskingUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 개발용: 문자를 보내지 않고 로그에 찍는다 (비용 0, 번호 등록 불필요).
 * 인증 코드가 로그에 그대로 나오므로 AWS 에서는 절대 쓰지 않는다 (sms.sender=log 일 때만 뜸).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.sender", havingValue = "log")
public class LogSmsSender implements SmsSender {

  @Override
  public void send(String phone, String message) {
    log.info("[가짜 문자] to={} message={}", MaskingUtils.maskPhone(phone), message);
  }
}