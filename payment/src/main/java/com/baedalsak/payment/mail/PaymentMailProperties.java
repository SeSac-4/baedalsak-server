package com.baedalsak.payment.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제 메일 설정 (application.yml payment-mail). 템플릿은 resources/mail/{이름}.html 의 파일 이름.
 * customer · owner 는 결제완료 메일(#37), canceledCustomer 는 환불 완료 메일(#180).
 */
@ConfigurationProperties("payment-mail")
public record PaymentMailProperties(
    String region,
    String from,
    Templates templates
) {

  public record Templates(String customer, String owner, String canceledCustomer) {
  }
}
