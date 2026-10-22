package com.baedalsak.delivery.service;

import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.dto.WaitingDeliveryResponse;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 6.2.1 배차 대기 목록 조회. 라이더가 수락할 수 있는(WAITING) 배달 요청을 오래된 순으로 보여 준다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryWaitingService {

    private final DeliveryRepository deliveryRepository;

    @Transactional(readOnly = true)
    public List<WaitingDeliveryResponse> getWaitingDeliveries() {
        return deliveryRepository.findAllByStatusOrderByCreatedAtAscIdAsc(DeliveryStatus.WAITING).stream()
                .map(WaitingDeliveryResponse::from)
                .toList();
    }
}
