package com.baedalsak.order.mail;

import com.baedalsak.order.event.OrderAcceptedPayload;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 조리 시작 메일 리스너 (#33, docs 10-3). 고객에게만, 조리 완료 예상 시각은 KST
 */
@ExtendWith(MockitoExtension.class)
class OrderAcceptedMailListenerTest {

    static final String EVENT_ID = "0b6f2a4e-3c1d-4f5a-9e8b-7d6c5b4a3f21";
    static final UUID EVENT_UUID = UUID.fromString(EVENT_ID);

    @Mock
    MailDispatcher mailDispatcher;

    OrderAcceptedMailListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderAcceptedMailListener(mailDispatcher, TestMailProperties.properties());
    }

    @Test
    void 고객에게만_조리_시작_메일을_보낸다() {
        listener.onOrderAccepted(event("customer@example.com"), EVENT_ID);

        Map<String, String> data = Map.of("orderId", "31", "storeName", "싹분식", "estimatedReadyAt", "2026-10-27 15:30");
        verify(mailDispatcher).send(EVENT_UUID, "customer@example.com", "accepted-customer-template", data);
    }

    @Test
    void 고객_이메일이_없어도_예외_없이_발송기에_넘겨_건너뛰게_한다() {
        OrderAcceptedPayload event = new OrderAcceptedPayload(31L, 1L, null, 2L, 3L, null, null, null, null,
                null, null, null, null, null);

        listener.onOrderAccepted(event, EVENT_ID);

        verify(mailDispatcher).send(eq(EVENT_UUID), isNull(), eq("accepted-customer-template"), anyMap());
    }

    @Test
    void messageId_가_없으면_거부한다() {
        assertThatThrownBy(() -> listener.onOrderAccepted(event("customer@example.com"), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(mailDispatcher);
    }

    // 조리 완료 예상 시각은 UTC 로 와도 메일에는 KST 로 표시
    private static OrderAcceptedPayload event(String customerEmail) {
        return new OrderAcceptedPayload(31L, 1L, "010-1234-5678", 2L, 3L, "싹분식", "서울시 마포구 1",
                "서울시 마포구 연남동 2", "연남동", 3000, null, OffsetDateTime.parse("2026-10-27T06:30:00Z"),
                customerEmail, "owner@example.com");
    }
}
