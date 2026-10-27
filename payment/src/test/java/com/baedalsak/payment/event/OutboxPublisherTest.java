package com.baedalsak.payment.event;

import com.baedalsak.payment.domain.PaymentOutbox;
import com.baedalsak.payment.repository.PaymentOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Outbox 발행기 (#14, docs 3-2). 발행 확인 결과별 published_at 기록 여부와 메시지 헤더
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    PaymentOutboxRepository outboxRepository;
    @Mock
    RabbitTemplate rabbitTemplate;
    @InjectMocks
    OutboxPublisher outboxPublisher;

    @Test
    void ack_를_받으면_헤더와_함께_보내고_published_at_을_기록한다() {
        PaymentOutbox row = row();
        given(outboxRepository.findUnpublishedForUpdate(100)).willReturn(List.of(row));
        brokerAnswers(cd -> cd.getFuture().complete(new CorrelationData.Confirm(true, null)));

        outboxPublisher.publishPending();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("baedalsak.events"), eq("payment.approved"), message.capture(), any());
        MessageProperties properties = message.getValue().getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(row.getEventId().toString());
        assertThat((String) properties.getHeader("eventType")).isEqualTo("PaymentApproved");
        assertThat((Integer) properties.getHeader("eventVersion")).isEqualTo(1);
        assertThat((String) properties.getHeader("occurredAt")).isEqualTo("2026-10-26T15:30+09:00");
        assertThat((String) properties.getHeader("traceId")).isEqualTo("trace-1");
        assertThat(new String(message.getValue().getBody())).isEqualTo("{\"paymentId\":7}");
        assertThat(row.getPublishedAt()).isNotNull();
    }

    @Test
    void nack_이면_기록하지_않는다() {
        PaymentOutbox row = row();
        given(outboxRepository.findUnpublishedForUpdate(100)).willReturn(List.of(row));
        brokerAnswers(cd -> cd.getFuture().complete(new CorrelationData.Confirm(false, "nack")));

        outboxPublisher.publishPending();

        assertThat(row.getPublishedAt()).isNull();
    }

    @Test
    void 받을_큐가_없어_return_되면_기록하지_않는다() {
        PaymentOutbox row = row();
        given(outboxRepository.findUnpublishedForUpdate(100)).willReturn(List.of(row));
        brokerAnswers(cd -> {
            cd.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE",
                    "baedalsak.events", "payment.approved"));
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
        });

        outboxPublisher.publishPending();

        assertThat(row.getPublishedAt()).isNull();
    }

    @Test
    void 브로커에_보내지_못하면_기록하지_않는다() {
        PaymentOutbox row = row();
        given(outboxRepository.findUnpublishedForUpdate(100)).willReturn(List.of(row));
        willThrow(new AmqpConnectException(new RuntimeException("connection refused")))
                .given(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxPublisher.publishPending();

        assertThat(row.getPublishedAt()).isNull();
    }

    @Test
    void 미발행_행이_없으면_보내지_않는다() {
        given(outboxRepository.findUnpublishedForUpdate(100)).willReturn(List.of());

        outboxPublisher.publishPending();

        verifyNoInteractions(rabbitTemplate);
    }

    private void brokerAnswers(Consumer<CorrelationData> broker) {
        willAnswer(invocation -> {
            broker.accept(invocation.getArgument(3));
            return null;
        }).given(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));
    }

    // 네이티브 조회처럼 created_at 이 UTC 로 읽힌 행
    private static PaymentOutbox row() {
        PaymentOutbox row = PaymentOutbox.of("PaymentApproved", "payment.approved", 7L, "{\"paymentId\":7}",
                "trace-1");
        ReflectionTestUtils.setField(row, "createdAt", OffsetDateTime.parse("2026-10-26T06:30:00Z"));
        return row;
    }
}
