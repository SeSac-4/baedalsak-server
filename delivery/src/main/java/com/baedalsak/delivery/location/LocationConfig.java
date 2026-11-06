package com.baedalsak.delivery.location;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 실시간 위치 기능 설정 등록 (가상 GPS · 카카오 REST).
 */
@Configuration
@EnableConfigurationProperties({DeliveryGpsProperties.class, KakaoProperties.class})
public class LocationConfig {
}
