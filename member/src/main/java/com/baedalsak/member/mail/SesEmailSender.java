package com.baedalsak.member.mail;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;

/**
 * AWS SES v2 로 메일 발송 (#207). 발신은 팀 공통 no-reply 주소 (infra #61 권한이 이 주소만 허용).
 *   샌드박스: 콘솔에 등록한 주소로만 감
 *   body 에 인증 코드가 들어 있으므로 로그에 남기지 않는다
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "email.sender", havingValue = "ses", matchIfMissing = true)
public class SesEmailSender implements EmailSender {

  private static final String UTF_8 = StandardCharsets.UTF_8.name();

  private final SesV2Client ses;
  private final String from;

  public SesEmailSender(SesV2Client ses, @Value("${email.from}") String from) {
    this.ses = ses;
    this.from = from;
  }

  @Override
  public void send(String to, String subject, String body) {
    try {
      ses.sendEmail(r -> r
          .fromEmailAddress(from)
          .destination(Destination.builder().toAddresses(to).build())
          .content(EmailContent.builder()
              .simple(Message.builder()
                  .subject(Content.builder().data(subject).charset(UTF_8).build())
                  .body(Body.builder().text(Content.builder().data(body).charset(UTF_8).build()).build())
                  .build())
              .build()));
    } catch (SdkException e) {
      // 샌드박스 미등록 주소(MessageRejected)도 여기로 → 사용자에게는 503 (잠시 후 다시)
      log.warn("SES 메일 발송 실패 to={}", to, e);
      throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }
}
