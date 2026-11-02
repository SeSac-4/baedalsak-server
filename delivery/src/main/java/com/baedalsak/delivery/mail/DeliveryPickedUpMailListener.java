package com.baedalsak.delivery.mail;

import com.baedalsak.delivery.config.RabbitConfig;
import com.baedalsak.delivery.payload.DeliveryPickedUpEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 배달시작 메일 (#108, docs 10-3). delivery 가 발행한 DeliveryPickedUp 을 자기 메일 큐로 받아 고객에게 보낸다.
 * 동기 발송(@Async 미사용) — 메서드가 끝나야 ack.
 */
@Component
@RequiredArgsConstructor
public class DeliveryPickedUpMailListener {

    private final MailDispatcher mailDispatcher;
    private final DeliveryMailProperties properties;

    @RabbitListener(queues = RabbitConfig.DELIVERY_PICKED_UP_MAIL_QUEUE, containerFactory = RabbitConfig.MAIL_LISTENER_FACTORY)
    public void onDeliveryPickedUp(DeliveryPickedUpEvent event,
                                   @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        UUID eventId = MailDispatcher.parseEventId(DeliveryPickedUpEvent.TYPE, messageId);
        Map<String, String> data = Map.of(
                "orderId", String.valueOf(event.orderId()),
                "pickedUpAt", MailDispatcher.formatKst(event.pickedUpAt()));
        mailDispatcher.send(eventId, event.customerEmail(), properties.templates().pickedUpCustomer(), data);
    }
}
