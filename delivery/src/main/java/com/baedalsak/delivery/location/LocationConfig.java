package com.baedalsak.delivery.location;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 실시간 위치 기능 설정 등록 (가상 GPS · 카카오 REST).
 */
@Configuration
@EnableConfigurationProperties({DeliveryGpsProperties.class, KakaoProperties.class})
public class LocationConfig {

    /**
     * 위치 · 가상 GPS 계산의 현재 시각. 테스트에서 고정 시각으로 바꾼다.
     *
     * @return 시스템 시간대(KST) 시계
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
