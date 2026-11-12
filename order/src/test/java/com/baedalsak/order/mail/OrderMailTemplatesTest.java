package com.baedalsak.order.mail;

import com.baedalsak.order.mail.MailTemplateRenderer.RenderedMail;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 조리 시작 메일 템플릿 · application.yml order-mail 설정 (#33)
 */
class OrderMailTemplatesTest {

    static final Map<String, String> DATA = Map.of("orderId", "1005", "storeName", "싹분식",
            "estimatedReadyAt", "2026-10-27 15:30");

    final MailTemplateRenderer renderer = new MailTemplateRenderer();

    @Configuration
    @EnableConfigurationProperties(OrderMailProperties.class)
    static class Config {
    }

    @Test
    void 조리_시작_템플릿은_리스너가_넘기는_변수로_렌더링된다() {
        RenderedMail mail = renderer.render("order-accepted-customer", DATA);

        assertThat(mail.subject()).isEqualTo("[배달싹] 가게에서 조리를 시작했어요");
        assertThat(mail.html()).contains("1005", "싹분식", "2026-10-27 15:30").doesNotContain("{{");
    }

    @Test
    void 설정의_발신_주소와_템플릿_이름이_바인딩되고_실제_파일이다() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class)
                .run(context -> {
                    OrderMailProperties properties = context.getBean(OrderMailProperties.class);

                    assertThat(properties.from()).isNotBlank();
                    assertThat(properties.region()).isNotBlank();
                    assertThat(renderer.render(properties.templates().acceptedCustomer(), DATA).subject())
                            .startsWith("[배달싹]");
                });
    }
}
