package com.baedalsak.delivery.mail;

import com.baedalsak.delivery.payload.DeliveryAssignedEvent;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 배차완료 메일 리스너 (#108, docs 10-3). 고객·사장님에게 각자 템플릿으로, 시각은 KST
 */
@ExtendWith(MockitoExtension.class)
class DeliveryAssignedMailListenerTest {

    static final String EVENT_ID = "0b6f2a4e-3c1d-4f5a-9e8b-7d6c5b4a3f21";
    static final UUID EVENT_UUID = UUID.fromString(EVENT_ID);

    @Mock
    MailDispatcher mailDispatcher;

    DeliveryAssignedMailListener listener;

    @BeforeEach
    void setUp() {
        listener = new DeliveryAssignedMailListener(mailDispatcher, TestMailProperties.properties());
    }

    @Test
    void 고객과_사장님에게_각자_템플릿으로_보낸다() {
        listener.onDeliveryAssigned(event(), EVENT_ID);

        Map<String, String> data = Map.of("orderId", "31", "riderName", "김라이더", "assignedAt", "2026-10-27 15:30");
        verify(mailDispatcher).send(EVENT_UUID, "customer@example.com", "assigned-customer-template", data);
        verify(mailDispatcher).send(EVENT_UUID, "owner@example.com", "assigned-owner-template", data);
    }

    @Test
    void messageId_가_없으면_거부한다() {
        assertThatThrownBy(() -> listener.onDeliveryAssigned(event(), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(mailDispatcher);
    }

    // 배정 시각은 UTC 로 와도 메일에는 KST 로 표시
    private static DeliveryAssignedEvent event() {
        return new DeliveryAssignedEvent(7L, 31L, 1L, 2L, 3L, "김라이더",
                OffsetDateTime.parse("2026-10-27T06:30:00Z"), "customer@example.com", "owner@example.com");
    }
}
