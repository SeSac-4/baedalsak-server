package com.baedalsak.payment.mail;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// local 프로필: 실제 발송 대신 로그만 남긴다 (docs 10-4, N5.6)
@Slf4j
@Component
@Profile("local")
public class LoggingMailSender implements MailSender {

  @Override
  public void send(String to, String templateName, Map<String, String> templateData) {
    log.info("메일 발송(local, 실제 발송 안 함) to={}, template={}, data={}", to, templateName, templateData);
  }
}
