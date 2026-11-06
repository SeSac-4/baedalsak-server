package com.baedalsak.payment.config;

import com.baedalsak.payment.config.OriginTraceIdAdviceTest.Listener;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리스너 컨테이너 팩토리 (#126). originTraceId advice 가 재시도 바깥에 있어 재시도 내내 · 재시도를 다 쓴 뒤 DLQ 로 보낼 때도
 * MDC 에 남고, Boot 재시도(최대 3회)와 영구 오류 바로 DLQ(skipRetryOnReject)는 그대로인지 확인한다.
 */
class ListenerContainerFactoryTest {

    // 재시도 소진 · 영구 오류 때 불리는 복구기 (운영은 Boot 기본 RejectAndDontRequeueRecoverer → DLQ). 그 순간의 MDC 를 기록
    static final List<String> recovered = new ArrayList<>();

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(RabbitConfig.class)
            .withBean(MessageRecoverer.class, () -> (message, cause) -> {
                recovered.add(MDC.get("originTraceId"));
                throw new AmqpRejectAndDontRequeueException("DLQ", cause);
            })
            .withPropertyValues(
                    "spring.rabbitmq.listener.simple.retry.enabled=true",
                    "spring.rabbitmq.listener.simple.retry.max-retries=2",
                    "spring.rabbitmq.listener.simple.retry.initial-interval=10ms");

    @AfterEach
    void clear() {
        recovered.clear();
        MDC.clear();
    }

    @Test
    void 결제_취소_명령_컨테이너도_따로_있고_originTraceId_와_재시도가_같다() {
        runner.run(context -> {
            SimpleRabbitListenerContainerFactory mail =
                    context.getBean("rabbitListenerContainerFactory", SimpleRabbitListenerContainerFactory.class);
            SimpleRabbitListenerContainerFactory cancel =
                    context.getBean(RabbitConfig.CANCEL_COMMAND_CONTAINER_FACTORY, SimpleRabbitListenerContainerFactory.class);

            assertThat(cancel).isNotSameAs(mail);
            assertThat(cancel.getAdviceChain()).hasSize(2);
            assertThat(cancel.getAdviceChain()[0]).isInstanceOf(OriginTraceIdAdvice.class);
        });
    }

    @Test
    void advice_chain_맨_앞이_originTraceId_이고_그_뒤에_재시도가_있다() {
        runner.run(context -> {
            Advice[] chain = context.getBean("rabbitListenerContainerFactory", SimpleRabbitListenerContainerFactory.class)
                    .getAdviceChain();

            assertThat(chain).hasSize(2);
            assertThat(chain[0]).isInstanceOf(OriginTraceIdAdvice.class);
        });
    }

    // 메일 컨테이너와 결제 취소 명령 컨테이너(#127) 모두 같은 재시도 · DLQ 동작
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"rabbitListenerContainerFactory", "cancelCommandContainerFactory"})
    void 재시도_3회_내내_그리고_DLQ_로_보낼_때도_originTraceId_가_있다(String factoryName) {
        runner.run(context -> {
            List<String> attempts = new ArrayList<>();
            Listener listener = proxy(context.getBean(factoryName, SimpleRabbitListenerContainerFactory.class), data -> {
                attempts.add(MDC.get("originTraceId"));
                throw new IllegalStateException("일시 오류");
            });

            assertThatThrownBy(() -> listener.invokeListener(null, OriginTraceIdAdviceTest.message("abc123")))
                    .hasRootCauseInstanceOf(IllegalStateException.class);

            assertThat(attempts).containsExactly("abc123", "abc123", "abc123");
            assertThat(recovered).containsExactly("abc123");
            assertThat(MDC.get("originTraceId")).isNull();
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"rabbitListenerContainerFactory", "cancelCommandContainerFactory"})
    void 영구_오류는_재시도하지_않고_바로_DLQ(String factoryName) {
        runner.run(context -> {
            List<String> attempts = new ArrayList<>();
            Listener listener = proxy(context.getBean(factoryName, SimpleRabbitListenerContainerFactory.class), data -> {
                attempts.add(MDC.get("originTraceId"));
                throw new AmqpRejectAndDontRequeueException("영구 오류");
            });

            assertThatThrownBy(() -> listener.invokeListener(null, OriginTraceIdAdviceTest.message("abc123")));

            assertThat(attempts).hasSize(1);
            assertThat(recovered).containsExactly("abc123");
        });
    }

    // 컨테이너와 같은 방식(ProxyFactory, advice chain 순서대로)으로 리스너를 감싼다
    private static Listener proxy(SimpleRabbitListenerContainerFactory factory, java.util.function.Consumer<Object> body) {
        ProxyFactory proxyFactory = new ProxyFactory((Listener) (channel, data) -> body.accept(data));
        proxyFactory.addInterface(Listener.class);
        for (Advice advice : factory.getAdviceChain()) {
            proxyFactory.addAdvice(advice);
        }
        return (Listener) proxyFactory.getProxy();
    }
}
