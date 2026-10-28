package com.baedalsak.delivery.service;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.dto.DeliveryStatusResponse;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 6.4.1 배달 상세 조회. 주문 상세 화면(U-09 고객, O-03 사장님)이 주문의 배달 상태·라이더·배정/픽업/완료 시각을 본다.
 * 프론트가 GET /orders/{id} 와 이 API 를 따로 불러 합성한다 (설계서 '동기 호출' 탭).
 */
@Service
@RequiredArgsConstructor
public class DeliveryDetailService {

    private final DeliveryRepository deliveryRepository;

    // 비어 있음: 배달이 아직 없음 (결제대기·결제완료이거나, 사장님 수락 직후 OrderAccepted 소비 전) → 컨트롤러가 204
    // 403: 내 주문·내 가게 주문 아님. 배달이 없으면 소유자를 알 수 없어 확인하지 않는다
    @Transactional(readOnly = true)
    public Optional<DeliveryStatusResponse> getByOrderId(Long orderId, Long memberId) {
        return deliveryRepository.findByOrderId(orderId)
                .map(delivery -> {
                    // 한 계정은 한 역할만 가지므로(설계서 '공통 규칙') 역할을 따로 보지 않고 둘 중 하나와 맞으면 허용
                    if (!memberId.equals(delivery.getCustomerId()) && !memberId.equals(delivery.getOwnerId())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN, "본인 주문 또는 내 가게 주문의 배달만 조회할 수 있습니다.");
                    }
                    return DeliveryStatusResponse.from(delivery);
                });
    }
}
