package com.baedalsak.payment.event;

import com.baedalsak.payment.domain.PaymentOutbox;
import com.baedalsak.payment.repository.PaymentOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * outbox 행 저장 (#151). 행을 저장한 뒤 커밋 신호(OutboxAppended)를 낸다
 */
class OutboxAppenderTest {

    final PaymentOutboxRepository repository = mock(PaymentOutboxRepository.class);
    final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    final OutboxAppender appender = new OutboxAppender(repository, JsonMapper.builder().build(), eventPublisher);

    @Test
    void 행을_저장한_뒤_커밋_신호를_낸다() {
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        PaymentOutbox row = appender.append("PaymentCanceled", "payment.canceled", 7L, Map.of("paymentId", 7));

        assertThat(row.getRoutingKey()).isEqualTo("payment.canceled");
        assertThat(row.getPayload()).contains("\"paymentId\":7");
        var order = inOrder(repository, eventPublisher);
        order.verify(repository).save(any());
        order.verify(eventPublisher).publishEvent(new OutboxAppended());
    }
}
