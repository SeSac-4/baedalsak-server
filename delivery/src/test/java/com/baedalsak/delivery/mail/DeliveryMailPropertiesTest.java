package com.baedalsak.delivery.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 application.yml 의 delivery-mail 이 바인딩되고, 템플릿 이름마다 파일이 렌더링되는지 (#108)
 */
class DeliveryMailPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(DeliveryMailProperties.class)
    static class Config {
    }

    @Test
    void 설정의_템플릿_이름은_모두_실제_파일이다() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class)
                .run(context -> {
                    DeliveryMailProperties properties = context.getBean(DeliveryMailProperties.class);
                    DeliveryMailProperties.Templates t = properties.templates();
                    MailTemplateRenderer renderer = new MailTemplateRenderer();

                    assertThat(properties.from()).isNotBlank();
                    for (String name : List.of(t.assignedCustomer(), t.assignedOwner(), t.pickedUpCustomer(),
                            t.completedCustomer(), t.completedOwner())) {
                        assertThat(name).isNotBlank();
                        assertThat(renderer.render(name, Map.of("orderId", "1", "riderName", "김라이더",
                                "assignedAt", "t", "pickedUpAt", "t", "completedAt", "t")).subject()).startsWith("[배달싹]");
                    }
                });
    }
}
