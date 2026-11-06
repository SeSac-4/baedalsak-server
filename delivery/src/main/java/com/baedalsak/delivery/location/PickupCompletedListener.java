package com.baedalsak.delivery.location;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 픽업이 커밋된 뒤 경로를 저장하고 위치 추적(가상 GPS)을 시작한다. 롤백되면 실행되지 않는다.
 * 픽업 트랜잭션 밖이라 길찾기 · Redis 가 느리거나 실패해도 픽업 · DeliveryPickedUp 발행에 영향이 없다.
 */
@Component
@RequiredArgsConstructor
public class PickupCompletedListener {

    private final DeliveryRouteService deliveryRouteService;
    private final VirtualGpsSimulator virtualGpsSimulator;

    /**
     * @param event 픽업된 배달
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPickupCompleted(PickupCompletedEvent event) {
        deliveryRouteService.saveRoute(event.deliveryId());
        virtualGpsSimulator.start(event.deliveryId());
    }
}
