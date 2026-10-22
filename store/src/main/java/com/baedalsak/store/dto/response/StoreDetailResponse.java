package com.baedalsak.store.dto.response;

import com.baedalsak.store.domain.BusinessHour;
import com.baedalsak.store.domain.Category;
import com.baedalsak.store.domain.OperationStatus;
import com.baedalsak.store.domain.Store;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 가게 상세 (StoreDetail). 등록·수정·운영 상태·영업시간 API 와 가게 상세·내 가게 조회가 함께 쓴다.
 */
public record StoreDetailResponse(
        Long id,
        String name,
        Category category,
        String address,
        Integer minOrderAmount,
        Integer deliveryFee,
        OperationStatus operationStatus,
        boolean isOpenNow,
        BusinessHourResponse todayHour,
        List<BusinessHourResponse> businessHours
) {

    /**
     * @param hours 가게의 7개 요일 영업시간 (요일 순)
     * @param now   한국 시각 기준 현재 시각
     */
    public static StoreDetailResponse of(Store store, List<BusinessHour> hours, LocalDateTime now) {
        short today = BusinessHour.toDbValue(now.getDayOfWeek());
        BusinessHour todayHour = hours.stream()
                .filter(h -> h.getDayOfWeek() == today)
                .findFirst()
                .orElseThrow();
        return new StoreDetailResponse(
                store.getId(),
                store.getName(),
                store.getCategory(),
                store.getAddress(),
                store.getMinOrderAmount(),
                store.getDeliveryFee(),
                store.getOperationStatus(),
                store.isOpenAt(todayHour, now.toLocalTime()),
                BusinessHourResponse.from(todayHour),
                hours.stream().map(BusinessHourResponse::from).toList()
        );
    }
}
