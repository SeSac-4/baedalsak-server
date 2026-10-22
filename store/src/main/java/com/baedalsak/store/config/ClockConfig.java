package com.baedalsak.store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 영업 여부는 한국 시각(KST) 기준으로 계산한다. 테스트에서 시각을 고정할 수 있게 Clock 을 빈으로 둔다.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    Clock clock() {
        return Clock.system(KST);
    }
}
