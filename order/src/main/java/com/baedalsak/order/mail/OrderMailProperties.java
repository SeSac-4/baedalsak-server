package com.baedalsak.order.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 주문 메일 설정 (application.yml order-mail). 템플릿은 resources/mail/{이름}.html 의 파일 이름.
 */
@ConfigurationProperties("order-mail")
public record OrderMailProperties(
        String region,
        String from,
        Templates templates
) {

    public record Templates(
            String acceptedCustomer
    ) {
    }
}
