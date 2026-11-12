package com.baedalsak.delivery.location;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 배달 완료가 커밋된 뒤 위치 · 가상 GPS 상태를 지우고 (배달실시간초안 9번), 라이더를 배달지에 남긴다 (#193).
 * 지우지 못해도 위치 키는 TTL 로 사라지고, 가상 GPS 는 다음 주기에 상태를 보고 스스로 정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryFinishedListener {

    private final RiderLocationStore riderLocationStore;
    private final RiderPositionProvider riderPositionProvider;

    /**
     * @param event 완료된 배달
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryFinished(DeliveryFinishedEvent event) {
        riderLocationStore.clear(event.deliveryId());
        log.info("rider location cleared: deliveryId={}", event.deliveryId());
        // 다음 배차는 이 배달지에서 출발한다 (실제 GPS 모드면 아무것도 하지 않음)
        if (event.riderId() != null && event.deliveryPoint() != null) {
            riderPositionProvider.settle(event.riderId(), event.deliveryPoint());
        }
    }
}
