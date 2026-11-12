package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.dto.DeliveryLocationResponse;
import com.baedalsak.delivery.dto.DeliveryRouteResponse;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 주문의 배달 경로 · 실시간 위치 조회 (배달실시간초안 10번). 고객 · 사장님 · 배정된 라이더가 같은 값을 본다.
 * 위치는 캐시 Redis 에서, 경로 · 상태는 DB 에서 읽는다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryTrackingService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteService deliveryRouteService;
    private final RiderLocationStore riderLocationStore;

    /**
     * @param orderId  주문 ID
     * @param memberId 요청자
     * @return 경로. 배차 완료면 라이더 → 가게, 픽업 후면 가게 → 배달지.
     *         배달이 없거나 배차 전이거나 아직 경로가 없으면 빈 값 → 204
     */
    @Transactional(readOnly = true)
    public Optional<DeliveryRouteResponse> getRoute(Long orderId, Long memberId) {
        return findAuthorized(orderId, memberId).flatMap(this::currentRoute);
    }

    private Optional<DeliveryRouteResponse> currentRoute(Delivery delivery) {
        return switch (delivery.getStatus()) {
            case WAITING -> Optional.empty();
            case ASSIGNED -> deliveryRouteService.readApproachRoute(delivery)
                    .map(route -> DeliveryRouteResponse.of(delivery.getId(), RouteLeg.TO_STORE, route));
            case PICKED_UP, DELIVERED -> deliveryRouteService.readRoute(delivery)
                    .map(route -> DeliveryRouteResponse.of(delivery.getId(), RouteLeg.TO_CUSTOMER, route));
        };
    }

    /**
     * @param orderId  주문 ID
     * @param memberId 요청자
     * @return 상태 + 위치. 배달이 없으면 빈 값 → 204. 위치는 픽업 중(PICKED_UP)일 때만 싣는다
     */
    @Transactional(readOnly = true)
    public Optional<DeliveryLocationResponse> getLocation(Long orderId, Long memberId) {
        return findAuthorized(orderId, memberId)
                .map(delivery -> DeliveryLocationResponse.of(delivery,
                        delivery.getStatus() == DeliveryStatus.PICKED_UP
                                ? riderLocationStore.find(delivery.getId()).orElse(null)
                                : null));
    }

    // 403: 주문 고객 · 가게 사장님 · 배정된 라이더가 아님. 한 계정은 한 역할만 가지므로 셋 중 하나와 맞으면 허용
    private Optional<Delivery> findAuthorized(Long orderId, Long memberId) {
        return deliveryRepository.findByOrderId(orderId)
                .map(delivery -> {
                    if (!memberId.equals(delivery.getCustomerId()) && !memberId.equals(delivery.getOwnerId())
                            && !memberId.equals(delivery.getRiderId())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN, "본인 주문 · 내 가게 주문 · 내 배달만 조회할 수 있습니다.");
                    }
                    return delivery;
                });
    }
}
