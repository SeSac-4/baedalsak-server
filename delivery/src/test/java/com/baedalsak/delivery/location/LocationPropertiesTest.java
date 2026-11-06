package com.baedalsak.delivery.location;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 application.yml 의 delivery-gps · kakao 가 바인딩되는지, 키가 없으면 카카오 호출이 꺼지는지
 */
class LocationPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(LocationConfig.class);

    @Test
    void defaultsToVirtualGpsWithTwoSecondIntervalAnd30mRadius() {
        runner.run(context -> {
            DeliveryGpsProperties gps = context.getBean(DeliveryGpsProperties.class);
            assertThat(gps.isVirtual()).isTrue();
            assertThat(gps.updateInterval()).isEqualTo(Duration.ofSeconds(2));
            assertThat(gps.speedKmh()).isPositive();
            assertThat(gps.arrivalRadiusM()).isEqualTo(30);
            assertThat(gps.locationTtl()).isPositive();
        });
    }

    @Test
    void switchesToRealModeViaEnvironmentVariable() {
        runner.withPropertyValues("DELIVERY_GPS_MODE=real")
                .run(context -> assertThat(context.getBean(DeliveryGpsProperties.class).isVirtual()).isFalse());
    }

    @Test
    void kakaoIsDisabledWithoutKeyAndEnabledWithKey() {
        runner.withPropertyValues("KAKAO_REST_API_KEY=")
                .run(context -> assertThat(context.getBean(KakaoProperties.class).enabled()).isFalse());
        runner.withPropertyValues("KAKAO_REST_API_KEY=test-key")
                .run(context -> {
                    KakaoProperties kakao = context.getBean(KakaoProperties.class);
                    assertThat(kakao.enabled()).isTrue();
                    assertThat(kakao.localBaseUrl()).startsWith("https://");
                    assertThat(kakao.readTimeout()).isPositive();
                });
    }
}
