package com.baedalsak.delivery.service;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.dto.RiderDeliveryHistoryResponse;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 배달 내역: 한국 날짜 기준 기간, 기간 검증, 응답 매핑
 */
class DeliveryHistoryServiceTest {

    private static final long RIDER_ID = 9L;

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final DeliveryHistoryService service = new DeliveryHistoryService(repository);

    @Test
    void queriesWholeKoreanDaysInclusive() {
        service.getHistory(RIDER_ID, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15));

        verify(repository).findCompletedByRider(RIDER_ID,
                OffsetDateTime.parse("2026-11-09T00:00+09:00"),
                OffsetDateTime.parse("2026-11-16T00:00+09:00"));
    }

    @Test
    void mapsCompletedDelivery() {
        Delivery delivery = Delivery.builder()
                .orderId(11L).customerId(1L).ownerId(2L).storeId(3L).storeName("뽀로로분식")
                .pickupAddress("서울 영등포구 경인로 702").deliveryAddress("서울 동작구 국사봉길 2 30호")
                .deliveryDong("상도동").customerPhone("010-1234-5678").deliveryFee(1000)
                .estimatedReadyAt(OffsetDateTime.now())
                .build();
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-11-13T11:48+09:00");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        ReflectionTestUtils.setField(delivery, "routeDistanceM", 2400);
        ReflectionTestUtils.setField(delivery, "completedAt", completedAt);
        when(repository.findCompletedByRider(anyLong(), any(), any())).thenReturn(List.of(delivery));

        List<RiderDeliveryHistoryResponse> result =
                service.getHistory(RIDER_ID, LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 13));

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.deliveryId()).isEqualTo(5L);
            assertThat(r.orderId()).isEqualTo(11L);
            assertThat(r.deliveryFee()).isEqualTo(1000);
            assertThat(r.distanceM()).isEqualTo(2400);
            assertThat(r.completedAt()).isEqualTo(completedAt);
        });
    }

    @Test
    void rejectsFromAfterTo() {
        assertInvalid(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 9));
    }

    @Test
    void rejectsRangeLongerThanMaxDays() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        assertInvalid(from, from.plusDays(DeliveryHistoryService.MAX_DAYS));
    }

    @Test
    void acceptsRangeOfMaxDays() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThat(service.getHistory(RIDER_ID, from, from.plusDays(DeliveryHistoryService.MAX_DAYS - 1))).isEmpty();
    }

    private void assertInvalid(LocalDate from, LocalDate to) {
        assertThatThrownBy(() -> service.getHistory(RIDER_ID, from, to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(repository, never()).findCompletedByRider(anyLong(), any(), any());
    }
}
