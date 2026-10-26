package com.baedalsak.order.consumer;

import com.baedalsak.order.config.RabbitConfig;
import com.baedalsak.order.event.DeliveryAssignedEvent;
import com.baedalsak.order.event.DeliveryCompletedEvent;
import com.baedalsak.order.event.DeliveryPickedUpEvent;
import com.baedalsak.order.event.PaymentApprovedEvent;
import com.baedalsak.order.service.OrderEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * payment·delivery 이벤트 소비 (#32). 처리 중 예외가 나면 application.yml 의 retry(3회) 후 <큐>.dlq 로 이동한다.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderEventService orderEventService;

    @RabbitListener(queues = RabbitConfig.PAYMENT_APPROVED_QUEUE)
    public void onPaymentApproved(PaymentApprovedEvent event,
                                  @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        orderEventService.onPaymentApproved(parseEventId(messageId, PaymentApprovedEvent.TYPE), event);
    }

    @RabbitListener(queues = RabbitConfig.DELIVERY_ASSIGNED_QUEUE)
    public void onDeliveryAssigned(DeliveryAssignedEvent event,
                                   @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        orderEventService.onDeliveryAssigned(parseEventId(messageId, DeliveryAssignedEvent.TYPE), event);
    }

    @RabbitListener(queues = RabbitConfig.DELIVERY_PICKED_UP_QUEUE)
    public void onDeliveryPickedUp(DeliveryPickedUpEvent event,
                                   @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        orderEventService.onDeliveryPickedUp(parseEventId(messageId, DeliveryPickedUpEvent.TYPE), event);
    }

    @RabbitListener(queues = RabbitConfig.DELIVERY_COMPLETED_QUEUE)
    public void onDeliveryCompleted(DeliveryCompletedEvent event,
                                    @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        orderEventService.onDeliveryCompleted(parseEventId(messageId, DeliveryCompletedEvent.TYPE), event);
    }

    // messageId(=eventId)가 없거나 UUID 가 아니면 멱등 처리를 할 수 없으므로 거부 → DLQ (docs 3-3)
    private UUID parseEventId(String messageId, String eventType) {
        if (messageId == null) {
            throw new AmqpRejectAndDontRequeueException(eventType + " without messageId");
        }
        try {
            return UUID.fromString(messageId);
        } catch (IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException(eventType + " with invalid messageId: " + messageId, e);
        }
    }
}
