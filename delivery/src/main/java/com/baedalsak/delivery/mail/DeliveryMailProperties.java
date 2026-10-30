package com.baedalsak.delivery.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배달 메일 설정 (application.yml delivery-mail). 템플릿은 resources/mail/{이름}.html 의 파일 이름.
 */
@ConfigurationProperties("delivery-mail")
public record DeliveryMailProperties(
        String region,
        String from,
        Templates templates
) {

    public record Templates(String assignedCustomer, String assignedOwner, String pickedUpCustomer) {
    }
}
