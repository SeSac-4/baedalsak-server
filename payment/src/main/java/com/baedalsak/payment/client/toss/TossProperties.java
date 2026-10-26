package com.baedalsak.payment.client.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스페이먼츠 결제 승인 설정 (application.yml toss). 시크릿 키는 TOSS_SECRET_KEY 로만 받는다 (docs 3번).
 */
@ConfigurationProperties("toss")
public record TossProperties(
        String baseUrl,
        String secretKey,
        Duration timeout
) {
}
