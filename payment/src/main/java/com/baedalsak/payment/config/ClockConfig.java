package com.baedalsak.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 결제 확인 시간 예산(#125)처럼 경과 시간을 재는 곳에서 쓰는 Clock. 테스트에서 시각을 고정 · 이동할 수 있게 빈으로 둔다.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    Clock clock() {
        return Clock.system(KST);
    }
}
