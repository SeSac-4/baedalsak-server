package com.baedalsak.payment.client.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스페이먼츠 설정 (application.yml toss). 시크릿 키는 TOSS_SECRET_KEY 로만 받는다 (docs 3번).
 *
 * @param connectTimeout 연결 제한. 2초 넘게 걸리면 이미 장애로 본다
 * @param readTimeout    읽기 제한 (승인 · 조회)
 * @param confirmBudget  결제 확인 한 번의 전체 시간 예산. 토스 호출을 시작하기 전에 남은 시간이
 *                       호출 1건 최대(연결 + 읽기)보다 적으면 호출하지 않는다 (#125)
 */
@ConfigurationProperties("toss")
public record TossProperties(
        String baseUrl,
        String secretKey,
        Duration connectTimeout,
        Duration readTimeout,
        Duration confirmBudget
) {

    /** 토스 호출 1건이 걸릴 수 있는 최대 시간 (연결 + 읽기) */
    public Duration maxCallTime() {
        return connectTimeout.plus(readTimeout);
    }
}
