package com.baedalsak.delivery.location;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 위치 제공자: application.yml rider-position 바인딩, delivery-gps.mode 에 따라 가상 · 실제 구현 선택
 */
class RiderPositionConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean(RiderLocationStore.class, () -> mock(RiderLocationStore.class))
            .withUserConfiguration(LocationConfig.class, RiderPositionConfig.class);

    @Test
    void usesVirtualProviderByDefault() {
        runner.run(context -> {
            assertThat(context.getBean(RiderPositionProvider.class)).isInstanceOf(VirtualRiderPositionProvider.class);
            RiderPositionProperties properties = context.getBean(RiderPositionProperties.class);
            assertThat(properties.startMinRadiusM()).isPositive();
            assertThat(properties.startMaxRadiusM()).isGreaterThan(properties.startMinRadiusM());
        });
    }

    @Test
    void usesRealProviderInRealMode() {
        runner.withPropertyValues("DELIVERY_GPS_MODE=real")
                .run(context -> assertThat(context.getBean(RiderPositionProvider.class))
                        .isInstanceOf(RealRiderPositionProvider.class));
    }
}
