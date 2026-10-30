package com.baedalsak.delivery.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메일 전용 팩토리를 추가해도 Boot 기본 팩토리(업무 리스너용)가 그대로 살아 있는지 (#108). 브로커 연결 없이 빈 구성만 확인
 */
class MailListenerContextTest {

    final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(RabbitConfig.class);

    @Test
    void 업무용_기본_팩토리와_메일_팩토리가_함께_있다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("rabbitListenerContainerFactory");
            assertThat(context).hasBean(RabbitConfig.MAIL_LISTENER_FACTORY);
            assertThat(context.getBeansOfType(SimpleRabbitListenerContainerFactory.class)).hasSize(2);
        });
    }
}
