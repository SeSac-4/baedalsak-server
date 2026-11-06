package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * 배달의 픽업 · 배달지 주소를 좌표로 바꿔 저장한다 (배달실시간초안 5번).
 * store · order 는 좌표를 모르므로 delivery 가 스냅샷 주소로 직접 변환한다.
 * 카카오 호출은 트랜잭션 밖에서 해서 HTTP 대기 동안 DB 커넥션을 잡지 않는다.
 */
@Slf4j
@Service
public class DeliveryGeocodeService {

    private final DeliveryRepository deliveryRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final TransactionTemplate transactionTemplate;

    public DeliveryGeocodeService(DeliveryRepository deliveryRepository, KakaoLocalClient kakaoLocalClient,
                                  PlatformTransactionManager transactionManager) {
        this.deliveryRepository = deliveryRepository;
        this.kakaoLocalClient = kakaoLocalClient;
        // 항상 새 트랜잭션. 생성 커밋 후(AFTER_COMMIT) 리스너에서 불리면 끝난 트랜잭션이 아직 묶여 있어,
        // 기본값(REQUIRED)으로는 거기에 합류해 UPDATE 가 'No active transaction' 으로 실패한다
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 비어 있는 좌표만 변환해 채운다. 이미 다 있으면 카카오를 호출하지 않는다. 실패해도 예외를 던지지 않는다.
     * 배달 생성 커밋 직후(DeliveryCreatedListener)와, 그때 못 채웠으면 배차 수락 때 다시 부른다.
     *
     * @param deliveryId 배달 ID
     */
    public void fillMissingCoordinates(Long deliveryId) {
        try {
            Optional<Delivery> found = deliveryRepository.findById(deliveryId);
            if (found.isEmpty() || !found.get().isMissingCoordinates()) {
                return;
            }
            Delivery delivery = found.get();
            GeoPoint pickup = delivery.getPickupPoint() != null ? null
                    : kakaoLocalClient.geocode(delivery.getPickupAddress()).orElse(null);
            GeoPoint destination = delivery.getDeliveryPoint() != null ? null
                    : kakaoLocalClient.geocode(delivery.getDeliveryAddress()).orElse(null);
            if (pickup == null && destination == null) {
                log.warn("delivery coordinates not found: deliveryId={}", deliveryId);
                return;
            }
            transactionTemplate.executeWithoutResult(status -> deliveryRepository.fillCoordinates(deliveryId,
                    pickup == null ? null : pickup.lat(), pickup == null ? null : pickup.lng(),
                    destination == null ? null : destination.lat(), destination == null ? null : destination.lng()));
            log.info("delivery coordinates filled: deliveryId={}, pickup={}, destination={}",
                    deliveryId, pickup != null, destination != null);
        } catch (RuntimeException e) {
            // 좌표는 부가 정보 — 배달 생성 · 배차 수락을 실패시키지 않는다
            log.warn("delivery geocode failed: deliveryId={}, cause={}", deliveryId, e.toString());
        }
    }
}
