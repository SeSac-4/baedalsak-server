package com.baedalsak.member.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 개발용: 메일을 보내지 않고 로그에 찍는다 (SES 수신 주소 등록 불필요).
 * 인증 코드가 로그에 그대로 나오므로 AWS 에서는 절대 쓰지 않는다 (email.sender=log 일 때만 뜸).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "email.sender", havingValue = "log")
public class LogEmailSender implements EmailSender {

  @Override
  public void send(String to, String subject, String body) {
    log.info("[가짜 메일] to={} subject={} body={}", to, subject, body);
  }
}
