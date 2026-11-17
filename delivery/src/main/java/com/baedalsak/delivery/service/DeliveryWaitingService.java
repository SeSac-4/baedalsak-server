package com.baedalsak.delivery.service;

import com.baedalsak.delivery.client.MemberClient;
import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.domain.VehicleType;
import com.baedalsak.delivery.dto.PageResponse;
import com.baedalsak.delivery.dto.WaitingDeliveryResponse;
import com.baedalsak.delivery.dto.WaitingSort;
import com.baedalsak.delivery.location.geo.GeoPoint;
import com.baedalsak.delivery.location.route.DeliveryTimeEstimator;
import com.baedalsak.delivery.location.tracking.RiderPositionService;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 6.2.1 배차 대기 목록 조회. 라이더가 수락할 수 있는(WAITING) 배달 요청을 정렬해 보여 준다.
 * 항목마다 그 라이더 기준 예상 시간(가게까지 · 배달 · 배달 완료까지)을 싣는다.
 * 라이더 위치 · 운송수단을 못 구해도 목록은 그대로 보여 준다 (예상 시간만 비거나 오토바이 기준).
 */
@Service
@RequiredArgsConstructor
public class DeliveryWaitingService {

    // 빠른 배달순: 예상 시간을 모르는 요청은 뒤로, 같으면 오래된 순 (목록이 이미 오래된 순이라 안정 정렬로 유지)
    private static final Comparator<WaitingDeliveryResponse> FASTEST_FIRST = Comparator.comparing(
            WaitingDeliveryResponse::totalDurationS, Comparator.nullsLast(Comparator.naturalOrder()));

    private final DeliveryRepository deliveryRepository;
    private final RiderPositionService riderPositionService;
    private final MemberClient memberClient;
    private final DeliveryTimeEstimator deliveryTimeEstimator;

    /**
     * 트랜잭션을 걸지 않는다 — member 호출 · Redis 를 기다리는 동안 DB 커넥션을 잡고 있지 않게 (조회 쿼리는 각각 짧게 끝난다).
     *
     * @param riderId 요청한 라이더
     * @param sort    정렬
     * @param page    페이지 (0부터)
     * @param size    페이지 크기
     * @return 배차 대기 목록 페이지
     */
    public PageResponse<WaitingDeliveryResponse> getWaitingDeliveries(Long riderId, WaitingSort sort, int page,
                                                                      int size) {
        GeoPoint rider = riderPositionService.myPosition(riderId).orElse(null);
        VehicleType vehicleType = memberClient.findRider(riderId)
                .map(MemberClient.RiderInfo::vehicleType)
                .orElse(null);

        if (sort == WaitingSort.FEE) {
            PageRequest pageable = PageRequest.of(page, size);
            Page<Delivery> deliveries = deliveryRepository.findAllByStatusOrderByDeliveryFeeDescCreatedAtAscIdAsc(
                    DeliveryStatus.WAITING, pageable);
            return PageResponse.of(deliveries, deliveries.stream()
                    .map(delivery -> toResponse(delivery, rider, vehicleType))
                    .toList());
        }

        List<WaitingDeliveryResponse> all = deliveryRepository
                .findAllByStatusOrderByCreatedAtAscIdAsc(DeliveryStatus.WAITING).stream()
                .map(delivery -> toResponse(delivery, rider, vehicleType))
                .sorted(FASTEST_FIRST)
                .toList();
        return PageResponse.slice(all, page, size);
    }

    private WaitingDeliveryResponse toResponse(Delivery delivery, GeoPoint rider, VehicleType vehicleType) {
        return WaitingDeliveryResponse.of(delivery, deliveryTimeEstimator.estimate(delivery, rider, vehicleType));
    }
}
