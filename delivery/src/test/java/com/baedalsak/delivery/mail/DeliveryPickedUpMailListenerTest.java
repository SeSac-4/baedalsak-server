package com.baedalsak.delivery.mail;

import com.baedalsak.delivery.payload.DeliveryPickedUpEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 배달시작 메일 리스너 (#108, docs 10-3). 고객에게만 보내고, 시각은 KST
 */
@ExtendWith(MockitoExtension.class)
class DeliveryPickedUpMailListenerTest {

    static final String EVENT_ID = "0b6f2a4e-3c1d-4f5a-9e8b-7d6c5b4a3f21";
    static final UUID EVENT_UUID = UUID.fromString(EVENT_ID);

    @Mock
    MailDispatcher mailDispatcher;

    DeliveryPickedUpMailListener listener;

    @BeforeEach
    void setUp() {
        listener = new DeliveryPickedUpMailListener(mailDispatcher, TestMailProperties.properties());
    }

    @Test
    void 고객에게만_보낸다() {
        listener.onDeliveryPickedUp(event(), EVENT_ID);

        verify(mailDispatcher).send(EVENT_UUID, "customer@example.com", "picked-up-customer-template",
                Map.of("orderId", "31", "pickedUpAt", "2026-10-27 15:50"));
        verify(mailDispatcher, times(1)).send(any(), any(), any(), any());
    }

    @Test
    void messageId_가_없으면_거부한다() {
        assertThatThrownBy(() -> listener.onDeliveryPickedUp(event(), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(mailDispatcher);
    }

    private static DeliveryPickedUpEvent event() {
        return new DeliveryPickedUpEvent(7L, 31L, 1L, 3L,
                OffsetDateTime.parse("2026-10-27T06:50:00Z"), "customer@example.com");
    }
}
