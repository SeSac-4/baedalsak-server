package com.baedalsak.payment.mail;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.baedalsak.payment.mail.MailTemplateRenderer.RenderedMail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// local 프로필: 실제 발송 대신 렌더링한 제목·본문을 로그로 남긴다 (docs 10-4, N5.6). 렌더링 실패는 AWS 와 똑같이 DLQ
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LoggingMailSender implements MailSender {

  private final MailTemplateRenderer renderer;

  @Override
  public void send(String to, String templateName, Map<String, String> templateData) {
    RenderedMail mail = renderer.render(templateName, templateData);
    log.info("메일 발송(local, 실제 발송 안 함) to={}, template={}, subject={}\n{}",
        to, templateName, mail.subject(), mail.html());
  }
}
