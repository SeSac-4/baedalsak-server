package com.baedalsak.order.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * [내부] 동기 호출 설정 (application.yml internal-api). 주소는 STORE_SERVICE_URL·MEMBER_SERVICE_URL 로만 받는다 (docs 3번).
 */
@ConfigurationProperties("internal-api")
public record InternalApiProperties(
        String storeUrl,
        String memberUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
