package com.baedalsak.delivery.consumer;

import com.baedalsak.delivery.config.RabbitConfig;
import com.baedalsak.delivery.payload.OrderAcceptedEvent;
import com.baedalsak.delivery.service.DeliveryCreateService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * OrderAccepted 소비 → 배달 요청 생성 (6.1.1).
 * 처리 중 예외가 나면 application.yml 의 retry(3회) 후 delivery.order-accepted.dlq 로 이동한다.
 */
@Component
@RequiredArgsConstructor
public class OrderAcceptedListener {

    private final DeliveryCreateService deliveryCreateService;

    @RabbitListener(queues = RabbitConfig.ORDER_ACCEPTED_QUEUE)
    public void onOrderAccepted(OrderAcceptedEvent event,
                                @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        deliveryCreateService.createFromOrderAccepted(parseEventId(messageId), event);
    }

    // messageId(=eventId)가 없거나 UUID 가 아니면 멱등 처리를 할 수 없으므로 거부 → (retry 후) DLQ
    private UUID parseEventId(String messageId) {
        if (messageId == null) {
            throw new AmqpRejectAndDontRequeueException("OrderAccepted without messageId");
        }
        try {
            return UUID.fromString(messageId);
        } catch (IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("OrderAccepted with invalid messageId: " + messageId, e);
        }
    }
}
