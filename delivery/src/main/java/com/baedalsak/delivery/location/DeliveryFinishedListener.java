package com.baedalsak.delivery.location;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 배달 완료가 커밋된 뒤 위치 · 가상 GPS 상태를 지운다 (배달실시간초안 9번).
 * 지우지 못해도 위치 키는 TTL 로 사라지고, 가상 GPS 는 다음 주기에 상태를 보고 스스로 정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryFinishedListener {

    private final RiderLocationStore riderLocationStore;

    /**
     * @param event 완료된 배달
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryFinished(DeliveryFinishedEvent event) {
        riderLocationStore.clear(event.deliveryId());
        log.info("rider location cleared: deliveryId={}", event.deliveryId());
    }
}
