package com.baedalsak.payment.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제완료 메일 설정 (application.yml payment-mail). 템플릿은 AWS SES 에 등록된 이름.
 */
@ConfigurationProperties("payment-mail")
public record PaymentMailProperties(
    String region,
    String from,
    Templates templates
) {

  public record Templates(String customer, String owner) {
  }
}
