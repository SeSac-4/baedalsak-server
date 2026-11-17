package com.baedalsak.delivery.location.tracking;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.dto.DeliveryLocationResponse;
import com.baedalsak.delivery.dto.DeliveryRouteResponse;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.delivery.location.geo.Route;
import com.baedalsak.delivery.location.geo.RouteLeg;
import com.baedalsak.delivery.location.route.ApproachRouteService;
import com.baedalsak.delivery.location.route.DeliveryRouteService;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 주문의 배달 경로 · 실시간 위치 조회 (배달실시간초안 10번). 고객 · 사장님 · 배정된 라이더가 같은 값을 본다.
 * 위치 · 가게까지 경로는 캐시 Redis 에서, 배달지까지 경로 · 상태는 DB 에서 읽는다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryTrackingService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteService deliveryRouteService;
    private final ApproachRouteService approachRouteService;
    private final RiderLocationStore riderLocationStore;

    /**
     * @param orderId  주문 ID
     * @param memberId 요청자
     * @return 지금 구간 경로 (배차 완료 = 라이더 → 가게, 픽업 후 = 가게 → 배달지).
     * 배달이 없거나 배차 전이거나 아직 경로가 없으면 빈 값 → 204
     */
    @Transactional(readOnly = true)
    public Optional<DeliveryRouteResponse> getRoute(Long orderId, Long memberId) {
        return findAuthorized(orderId, memberId)
                .filter(delivery -> delivery.getStatus() != DeliveryStatus.WAITING)
                .flatMap(delivery -> {
                    RouteLeg leg = RouteLeg.of(delivery.getStatus());
                    Optional<Route> route = leg == RouteLeg.TO_STORE
                            ? approachRouteService.readRoute(delivery)
                            : deliveryRouteService.readRoute(delivery);
                    return route.map(found -> DeliveryRouteResponse.of(delivery.getId(), leg, found));
                });
    }

    /**
     * @param orderId  주문 ID
     * @param memberId 요청자
     * @return 상태 + 위치. 배달이 없으면 빈 값 → 204. 위치는 진행 중(배차 완료 · 픽업)일 때만,
     * 지금 구간의 위치만 싣는다 (픽업 직후 아직 가게까지 구간 위치가 남아 있으면 싣지 않음)
     */
    @Transactional(readOnly = true)
    public Optional<DeliveryLocationResponse> getLocation(Long orderId, Long memberId) {
        return findAuthorized(orderId, memberId)
                .map(delivery -> DeliveryLocationResponse.of(delivery, currentLegLocation(delivery)));
    }

    private RiderLocation currentLegLocation(Delivery delivery) {
        if (!DeliveryStatus.ACTIVE.contains(delivery.getStatus())) {
            return null;
        }
        RouteLeg leg = RouteLeg.of(delivery.getStatus());
        return riderLocationStore.find(delivery.getId())
                .filter(location -> RouteLeg.orDefault(location.leg()) == leg)
                .orElse(null);
    }

    // 403: 주문 고객 · 가게 사장님 · 배정된 라이더가 아님. 한 계정은 한 역할만 가지므로 셋 중 하나와 맞으면 허용
    private Optional<Delivery> findAuthorized(Long orderId, Long memberId) {
        return deliveryRepository.findByOrderId(orderId)
                .map(delivery -> {
                    if (!memberId.equals(delivery.getCustomerId()) && !memberId.equals(delivery.getOwnerId())
                            && !memberId.equals(delivery.getRiderId())) {
                        throw new BusinessException(CommonErrorCode.FORBIDDEN, "본인 주문 · 내 가게 주문 · 내 배달만 조회할 수 있습니다.");
                    }
                    return delivery;
                });
    }
}
