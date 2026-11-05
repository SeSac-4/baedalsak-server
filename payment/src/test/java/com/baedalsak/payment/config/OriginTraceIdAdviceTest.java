package com.baedalsak.payment.config;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.aop.framework.ProxyFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 메시지 헤더 traceId → MDC originTraceId (#126). 처리 중에만 있고, 처리 후(예외 포함) 남지 않는다
 */
class OriginTraceIdAdviceTest {

    // 컨테이너가 advice 로 감싸는 메서드와 같은 모양 (Channel, 메시지)
    interface Listener {
        void invokeListener(Channel channel, Object data);
    }

    final List<String> seen = new ArrayList<>();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void 헤더가_있으면_처리_중에_MDC_에_넣고_처리_후_지운다() {
        proxy(data -> seen.add(MDC.get("originTraceId"))).invokeListener(null, message("abc123"));

        assertThat(seen).containsExactly("abc123");
        assertThat(MDC.get("originTraceId")).isNull();
    }

    @Test
    void 헤더가_없거나_비어_있으면_넣지_않는다() {
        Listener listener = proxy(data -> seen.add(MDC.get("originTraceId")));

        listener.invokeListener(null, message(null));
        listener.invokeListener(null, message("  "));

        assertThat(seen).containsExactly(null, null);
    }

    @Test
    void 리스너가_예외를_던져도_지운다() {
        Listener listener = proxy(data -> {
            throw new IllegalStateException("실패");
        });

        assertThatThrownBy(() -> listener.invokeListener(null, message("abc123")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("originTraceId")).isNull();
    }

    @Test
    void 이전_값이_있으면_처리_후_되돌린다() {
        MDC.put("originTraceId", "before");

        proxy(data -> seen.add(MDC.get("originTraceId"))).invokeListener(null, message("abc123"));

        assertThat(seen).containsExactly("abc123");
        assertThat(MDC.get("originTraceId")).isEqualTo("before");
    }

    @Test
    void 배치면_첫_메시지_헤더를_쓴다() {
        proxy(data -> seen.add(MDC.get("originTraceId")))
                .invokeListener(null, List.of(message("first"), message("second")));

        assertThat(seen).containsExactly("first");
    }

    private static Listener proxy(java.util.function.Consumer<Object> body) {
        ProxyFactory factory = new ProxyFactory((Listener) (channel, data) -> body.accept(data));
        factory.addInterface(Listener.class);
        factory.addAdvice(new OriginTraceIdAdvice());
        return (Listener) factory.getProxy();
    }

    static Message message(String traceId) {
        MessageProperties properties = new MessageProperties();
        if (traceId != null) {
            properties.setHeader("traceId", traceId);
        }
        return new Message("{}".getBytes(), properties);
    }
}
