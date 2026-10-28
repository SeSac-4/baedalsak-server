package com.baedalsak.payment.mail;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.BadRequestException;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.MailFromDomainNotVerifiedException;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;
import software.amazon.awssdk.services.sesv2.model.NotFoundException;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.Template;
import tools.jackson.databind.json.JsonMapper;

/**
 * AWS SES v2 템플릿 메일 발송 (local 프로필 제외).
 * 잘못된 요청·거부·발신 도메인 미인증·템플릿 없음은 다시 보내도 실패하므로 영구 오류로 바꾼다. 그 외(쓰로틀링·네트워크)는 그대로 던져 재시도.
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
public class SesMailSender implements MailSender {

  private final SesV2Client sesClient;
  private final PaymentMailProperties properties;
  private final JsonMapper jsonMapper;

  @Override
  public void send(String to, String templateName, Map<String, String> templateData) {
    SendEmailRequest request = SendEmailRequest.builder()
        .fromEmailAddress(properties.from())
        .destination(Destination.builder().toAddresses(to).build())
        .content(EmailContent.builder()
            .template(Template.builder()
                .templateName(templateName)
                .templateData(jsonMapper.writeValueAsString(templateData))
                .build())
            .build())
        .build();
    try {
      sesClient.sendEmail(request);
    } catch (BadRequestException | MessageRejectedException | MailFromDomainNotVerifiedException
        | NotFoundException e) {
      throw new PermanentMailFailureException("SES 발송 거부 template=" + templateName, e);
    }
  }
}
