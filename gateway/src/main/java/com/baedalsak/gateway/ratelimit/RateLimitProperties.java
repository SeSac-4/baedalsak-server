package com.baedalsak.gateway.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 rate-limit.* 값 (#24).
 * yml 의 auth-per-ip 처럼 하이픈 이름이 authPerIp 필드로 자동 연결된다.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
    boolean enabled,
    int authPerIp,          // 로그인·가입: IP 당 분당 허용 횟수
    int paymentPerMember,   // 결제: 회원 당 분당 허용 횟수
    int aiImagePerMember,
    int defaultPerClient,   // 그 밖: 회원(로그인 전은 IP) 당 분당 허용 횟수
    int trustedProxies,     // X-Forwarded-For 뒤에서 몇 번째를 실제 IP 로 볼지
    Duration timeout        // Redis 응답을 기다리는 최대 시간
) {
}