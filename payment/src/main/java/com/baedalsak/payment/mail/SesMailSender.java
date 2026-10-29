package com.baedalsak.payment.mail;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.baedalsak.payment.mail.MailTemplateRenderer.RenderedMail;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.BadRequestException;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.MailFromDomainNotVerifiedException;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * AWS SES v2 메일 발송 (local 프로필 제외). 앱이 렌더링한 제목·HTML 본문을 그대로 보낸다 (SES 템플릿 미사용).
 * 렌더링 실패·잘못된 요청·거부·발신 도메인 미인증은 다시 보내도 실패하므로 영구 오류로 바꾼다. 그 외(쓰로틀링·네트워크)는 그대로 던져 재시도.
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
public class SesMailSender implements MailSender {

  private static final String UTF_8 = StandardCharsets.UTF_8.name();

  private final SesV2Client sesClient;
  private final PaymentMailProperties properties;
  private final MailTemplateRenderer renderer;

  @Override
  public void send(String to, String templateName, Map<String, String> templateData) {
    RenderedMail mail = renderer.render(templateName, templateData);
    SendEmailRequest request = SendEmailRequest.builder()
        .fromEmailAddress(properties.from())
        .destination(Destination.builder().toAddresses(to).build())
        .content(EmailContent.builder()
            .simple(Message.builder()
                .subject(Content.builder().data(mail.subject()).charset(UTF_8).build())
                .body(Body.builder().html(Content.builder().data(mail.html()).charset(UTF_8).build()).build())
                .build())
            .build())
        .build();
    try {
      sesClient.sendEmail(request);
    } catch (BadRequestException | MessageRejectedException | MailFromDomainNotVerifiedException e) {
      throw new PermanentMailFailureException("SES 발송 거부 template=" + templateName, e);
    }
  }
}
