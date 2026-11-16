package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 라이더 본인 현재 위치 조회 (배차 대기 지도). 가상 위치가 아직 없으면 가장 오래 기다린 배차 대기 가게 주변에 만든다.
 */
@Service
@RequiredArgsConstructor
public class RiderPositionService {

    private final DeliveryRepository deliveryRepository;
    private final RiderPositionProvider riderPositionProvider;

    /**
     * @param riderId 라이더 ID
     * @return 현재 위치. 모르면 빈 값 → 204
     */
    @Transactional(readOnly = true)
    public Optional<GeoPoint> getMyPosition(Long riderId) {
        GeoPoint near = deliveryRepository
                .findFirstByStatusAndPickupLatIsNotNullAndPickupLngIsNotNullOrderByCreatedAtAscIdAsc(
                        DeliveryStatus.WAITING)
                .map(Delivery::getPickupPoint)
                .orElse(null);
        return riderPositionProvider.currentPosition(riderId, near);
    }
}
