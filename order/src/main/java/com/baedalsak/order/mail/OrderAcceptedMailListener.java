package com.baedalsak.order.mail;

import com.baedalsak.order.config.RabbitConfig;
import com.baedalsak.order.event.OrderAcceptedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 조리 시작 메일 (#33, docs 10-3). order 가 발행한 OrderAccepted 를 자기 메일 큐로 받아 고객에게만 보낸다.
 * 동기 발송(@Async 미사용) — 메서드가 끝나야 ack. 주문 수락 API 는 메일을 기다리지 않는다.
 * 값이 비어 있어도 Map.of 에서 터지지 않게 빈 문자열로 넘긴다 → 수신자가 없으면 MailDispatcher 가 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class OrderAcceptedMailListener {

    private final MailDispatcher mailDispatcher;
    private final OrderMailProperties properties;

    @RabbitListener(queues = RabbitConfig.ORDER_ACCEPTED_MAIL_QUEUE, containerFactory = RabbitConfig.MAIL_LISTENER_FACTORY)
    public void onOrderAccepted(OrderAcceptedPayload event,
                                @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        UUID eventId = MailDispatcher.parseEventId(OrderAcceptedPayload.EVENT_TYPE, messageId);
        Map<String, String> data = Map.of(
                "orderId", String.valueOf(event.orderId()),
                "storeName", Objects.toString(event.storeName(), ""),
                "estimatedReadyAt", event.estimatedReadyAt() != null ? MailDispatcher.formatKst(event.estimatedReadyAt()) : "");
        mailDispatcher.send(eventId, event.customerEmail(), properties.templates().acceptedCustomer(), data);
    }
}
