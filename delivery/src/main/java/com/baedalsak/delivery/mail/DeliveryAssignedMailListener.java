package com.baedalsak.delivery.mail;

import com.baedalsak.delivery.config.RabbitConfig;
import com.baedalsak.delivery.payload.DeliveryAssignedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 배차완료 메일 (#108, docs 10-3). delivery 가 발행한 DeliveryAssigned 를 자기 메일 큐로 받아 고객·사장님에게 보낸다.
 * 동기 발송(@Async 미사용) — 메서드가 끝나야 ack.
 */
@Component
@RequiredArgsConstructor
public class DeliveryAssignedMailListener {

    // member 호출이 실패해도 배차는 성공해 riderName 이 null 로 온다 (설계서 '동기 호출' 탭) → "담당 라이더님" 으로 보낸다
    private static final String UNKNOWN_RIDER_NAME = "담당";

    private final MailDispatcher mailDispatcher;
    private final DeliveryMailProperties properties;

    @RabbitListener(queues = RabbitConfig.DELIVERY_ASSIGNED_MAIL_QUEUE, containerFactory = RabbitConfig.MAIL_LISTENER_FACTORY)
    public void onDeliveryAssigned(DeliveryAssignedEvent event,
                                   @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        UUID eventId = MailDispatcher.parseEventId(DeliveryAssignedEvent.TYPE, messageId);
        Map<String, String> data = Map.of(
                "orderId", String.valueOf(event.orderId()),
                "riderName", event.riderName() != null ? event.riderName() : UNKNOWN_RIDER_NAME,
                "assignedAt", MailDispatcher.formatKst(event.assignedAt()));
        mailDispatcher.send(eventId, event.customerEmail(), properties.templates().assignedCustomer(), data);
        mailDispatcher.send(eventId, event.ownerEmail(), properties.templates().assignedOwner(), data);
    }
}
