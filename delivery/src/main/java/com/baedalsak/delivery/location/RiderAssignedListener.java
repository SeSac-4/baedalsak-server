package com.baedalsak.delivery.location;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 배차 수락이 커밋된 뒤 라이더 현재 위치 → 가게 경로를 저장한다. 롤백되면 실행되지 않는다.
 * 수락 트랜잭션 밖이라 길찾기 · Redis 가 느리거나 실패해도 수락 · DeliveryAssigned 발행에 영향이 없다.
 */
@Component
@RequiredArgsConstructor
public class RiderAssignedListener {

    private final DeliveryRouteService deliveryRouteService;

    /**
     * @param event 배차된 배달
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRiderAssigned(RiderAssignedEvent event) {
        deliveryRouteService.saveApproachRoute(event.deliveryId());
    }
}
