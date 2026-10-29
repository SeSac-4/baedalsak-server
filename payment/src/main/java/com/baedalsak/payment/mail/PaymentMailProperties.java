package com.baedalsak.payment.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제완료 메일 설정 (application.yml payment-mail). 템플릿은 resources/mail/{이름}.html 의 파일 이름.
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
