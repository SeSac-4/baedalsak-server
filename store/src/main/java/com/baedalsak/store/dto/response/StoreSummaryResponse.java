package com.baedalsak.store.dto.response;

import com.baedalsak.store.domain.BusinessHour;
import com.baedalsak.store.domain.Category;
import com.baedalsak.store.domain.OperationStatus;
import com.baedalsak.store.domain.Store;

import java.time.LocalTime;

/**
 * 가게 목록 항목 (StoreSummary, GET /v1/stores 응답)
 */
public record StoreSummaryResponse(
        Long id,
        String name,
        Category category,
        Integer minOrderAmount,
        Integer deliveryFee,
        OperationStatus operationStatus,
        boolean isOpenNow,
        BusinessHourResponse todayHour
) {

    /**
     * @param todayHour 오늘 요일의 영업시간
     * @param now       한국 시각 기준 현재 시각
     */
    public static StoreSummaryResponse of(Store store, BusinessHour todayHour, LocalTime now) {
        return new StoreSummaryResponse(
                store.getId(),
                store.getName(),
                store.getCategory(),
                store.getMinOrderAmount(),
                store.getDeliveryFee(),
                store.getOperationStatus(),
                store.isOpenAt(todayHour, now),
                BusinessHourResponse.from(todayHour)
        );
    }
}
