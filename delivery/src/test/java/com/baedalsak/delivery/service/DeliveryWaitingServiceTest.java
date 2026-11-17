package com.baedalsak.delivery.service;

import com.baedalsak.delivery.client.MemberClient;
import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.dto.PageResponse;
import com.baedalsak.delivery.dto.WaitingDeliveryResponse;
import com.baedalsak.delivery.dto.WaitingSort;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.location.DeliveryTimeEstimator;
import com.baedalsak.delivery.location.RiderPositionService;
import com.baedalsak.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 배차 대기 정렬: 빠른 배달순(예상 시간 짧은 순, 모르면 뒤로) · 배달비 높은순 · 페이지 자르기 · 정렬 값 검증
 */
class DeliveryWaitingServiceTest {

    private static final long RIDER_ID = 9L;

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final RiderPositionService positionService = mock(RiderPositionService.class);
    private final MemberClient memberClient = mock(MemberClient.class);
    private final DeliveryTimeEstimator estimator = mock(DeliveryTimeEstimator.class);
    private final DeliveryWaitingService service = new DeliveryWaitingService(repository, positionService, memberClient,
            estimator);

    @Test
    void fastestSortsByTotalDurationWithUnknownLast() {
        List<Delivery> oldestFirst = List.of(delivery(1L, 3000), delivery(2L, 2500), delivery(3L, 4000));
        when(repository.findAllByStatusOrderByCreatedAtAscIdAsc(DeliveryStatus.WAITING)).thenReturn(oldestFirst);
        givenTotals(Map.of(1L, 900), Map.of(2L, 300));   // 3 은 모름

        PageResponse<WaitingDeliveryResponse> page = service.getWaitingDeliveries(RIDER_ID, WaitingSort.FASTEST, 0, 20);

        assertThat(page.content()).extracting(WaitingDeliveryResponse::deliveryId).containsExactly(2L, 1L, 3L);
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    void fastestSlicesRequestedPage() {
        List<Delivery> oldestFirst = List.of(delivery(1L, 3000), delivery(2L, 2500), delivery(3L, 4000));
        when(repository.findAllByStatusOrderByCreatedAtAscIdAsc(DeliveryStatus.WAITING)).thenReturn(oldestFirst);
        givenTotals(Map.of(1L, 100, 2L, 200), Map.of(3L, 300));

        PageResponse<WaitingDeliveryResponse> second = service.getWaitingDeliveries(RIDER_ID, WaitingSort.FASTEST, 1, 2);

        assertThat(second.content()).extracting(WaitingDeliveryResponse::deliveryId).containsExactly(3L);
        assertThat(second.totalPages()).isEqualTo(2);
    }

    @Test
    void feeUsesFeeDescendingQuery() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.findAllByStatusOrderByDeliveryFeeDescCreatedAtAscIdAsc(DeliveryStatus.WAITING, pageable))
                .thenReturn(new PageImpl<>(List.of(delivery(3L, 4000), delivery(1L, 3000)), pageable, 2));
        givenTotals(Map.of(), Map.of());

        PageResponse<WaitingDeliveryResponse> page = service.getWaitingDeliveries(RIDER_ID, WaitingSort.FEE, 0, 20);

        assertThat(page.content()).extracting(WaitingDeliveryResponse::deliveryFee).containsExactly(4000, 3000);
    }

    @Test
    void parsesSortIgnoringCaseAndRejectsUnknown() {
        assertThat(WaitingSort.parse(null)).isEqualTo(WaitingSort.FASTEST);
        assertThat(WaitingSort.parse("fee")).isEqualTo(WaitingSort.FEE);
        assertThatThrownBy(() -> WaitingSort.parse("OLDEST")).isInstanceOf(BusinessException.class);
    }

    // 배달 ID 별 합계 시간. 두 맵을 합쳐 쓰고, 없는 ID 는 null
    private void givenTotals(Map<Long, Integer> first, Map<Long, Integer> second) {
        when(positionService.myPosition(RIDER_ID)).thenReturn(Optional.empty());
        when(memberClient.findRider(RIDER_ID)).thenReturn(Optional.empty());
        when(estimator.estimate(any(), isNull(), isNull())).thenAnswer(call -> {
            Long id = ((Delivery) call.getArgument(0)).getId();
            Integer total = first.getOrDefault(id, second.get(id));
            return new DeliveryTimeEstimator.Estimate(null, total, total);
        });
    }

    private static Delivery delivery(Long id, int fee) {
        Delivery delivery = Delivery.builder()
                .orderId(1000L + id).customerId(1L).ownerId(2L).storeId(3L).storeName("가게" + id)
                .pickupAddress("서울 성동구").deliveryAddress("서울 성동구 302호").deliveryDong("성수동")
                .customerPhone("010-0000-0000").deliveryFee(fee).estimatedReadyAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(delivery, "id", id);
        ReflectionTestUtils.setField(delivery, "status", DeliveryStatus.WAITING);
        return delivery;
    }
}
