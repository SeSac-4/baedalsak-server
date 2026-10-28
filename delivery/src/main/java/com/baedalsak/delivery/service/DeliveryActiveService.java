package com.baedalsak.delivery.service;

import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.dto.RiderDeliveryResponse;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 6.4.2 라이더 진행 중 배달 조회. 라이더가 지금 맡고 있는(ASSIGNED·PICKED_UP) 배달을 보여 준다 (화면 R-02).
 * R-01 진입 시에도 호출해 있으면 R-02 로, 404 면 대기 목록을 보여 준다 (설계서 '화면 흐름').
 */
@Service
@RequiredArgsConstructor
public class DeliveryActiveService {

    private final DeliveryRepository deliveryRepository;

    // 진행 중 배달은 라이더당 1건 (6.2.2). 혹시 2건 이상이면 먼저 배정된 것부터 보여 준다
    @Transactional(readOnly = true)
    public RiderDeliveryResponse getActive(Long riderId) {
        return deliveryRepository
                .findFirstByRiderIdAndStatusInOrderByAssignedAtAscIdAsc(riderId, DeliveryStatus.ACTIVE)
                .map(RiderDeliveryResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }
}
