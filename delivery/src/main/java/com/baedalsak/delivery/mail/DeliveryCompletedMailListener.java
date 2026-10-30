package com.baedalsak.delivery.mail;

import com.baedalsak.delivery.config.RabbitConfig;
import com.baedalsak.delivery.payload.DeliveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 배달완료 메일 (#108, docs 10-3). delivery 가 발행한 DeliveryCompleted 를 자기 메일 큐로 받아 고객·사장님에게 보낸다.
 * 동기 발송(@Async 미사용) — 메서드가 끝나야 ack.
 */
@Component
@RequiredArgsConstructor
public class DeliveryCompletedMailListener {

    private final MailDispatcher mailDispatcher;
    private final DeliveryMailProperties properties;

    @RabbitListener(queues = RabbitConfig.DELIVERY_COMPLETED_MAIL_QUEUE, containerFactory = RabbitConfig.MAIL_LISTENER_FACTORY)
    public void onDeliveryCompleted(DeliveryCompletedEvent event,
                                    @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        UUID eventId = MailDispatcher.parseEventId(DeliveryCompletedEvent.TYPE, messageId);
        Map<String, String> data = Map.of(
                "orderId", String.valueOf(event.orderId()),
                "completedAt", MailDispatcher.formatKst(event.completedAt()));
        mailDispatcher.send(eventId, event.customerEmail(), properties.templates().completedCustomer(), data);
        mailDispatcher.send(eventId, event.ownerEmail(), properties.templates().completedOwner(), data);
    }
}
