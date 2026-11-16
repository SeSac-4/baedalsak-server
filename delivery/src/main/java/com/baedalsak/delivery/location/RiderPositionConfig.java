package com.baedalsak.delivery.location;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.random.RandomGenerator;

/**
 * 라이더 현재 위치 출처 등록. delivery-gps.mode 와 같이 바뀐다 (virtual = 가상 위치, real = 앱 GPS).
 */
@Configuration
public class RiderPositionConfig {

    /**
     * @return 모드에 맞는 위치 제공자
     */
    @Bean
    RiderPositionProvider riderPositionProvider(DeliveryGpsProperties gpsProperties, RiderLocationStore store,
                                                RiderPositionProperties positionProperties) {
        return gpsProperties.isVirtual()
                ? new VirtualRiderPositionProvider(store, positionProperties, RandomGenerator.getDefault())
                : new RealRiderPositionProvider(store);
    }
}
