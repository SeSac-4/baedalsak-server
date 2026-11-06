package com.baedalsak.delivery.location;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 배달 생성이 커밋된 뒤 좌표를 채운다. 롤백되면 실행되지 않는다.
 * 생성 트랜잭션 밖이라 카카오가 느리거나 실패해도 배달 생성 · OrderAccepted ack 에 영향이 없다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryCreatedListener {

    private final DeliveryGeocodeService deliveryGeocodeService;

    /**
     * @param event 생성된 배달
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryCreated(DeliveryCreatedEvent event) {
        deliveryGeocodeService.fillMissingCoordinates(event.deliveryId());
    }
}
