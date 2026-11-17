package com.baedalsak.delivery.location.event;

import com.baedalsak.delivery.location.route.DeliveryRouteService;
import com.baedalsak.delivery.location.tracking.VirtualGpsSimulator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 픽업이 커밋된 뒤 가게 → 배달지 경로를 저장하고 가상 GPS 를 배달지까지 구간으로 바꿔 가게에서 다시 출발시킨다.
 * 롤백되면 실행되지 않는다.
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
