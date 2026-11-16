package com.baedalsak.store.dto.response;

import com.baedalsak.store.config.ImageProperties;
import com.baedalsak.store.domain.BusinessHour;
import com.baedalsak.store.domain.Category;
import com.baedalsak.store.domain.OperationStatus;
import com.baedalsak.store.domain.Store;

import java.time.LocalTime;

/**
 * 가게 목록 항목 (StoreSummary, GET /v1/stores 응답)
 * distanceM: 고객 위치까지 직선거리 (m 정수, #210). 위치를 보내지 않았으면 null
 */
public record StoreSummaryResponse(
        Long id,
        String name,
        Category category,
        Integer minOrderAmount,
        Integer deliveryFee,
        OperationStatus operationStatus,
        boolean isOpenNow,
        BusinessHourResponse todayHour,
        String imageKey,
        String imageUrl,
        Integer distanceM
) {

    /**
     * @param todayHour 오늘 요일의 영업시간
     * @param now       한국 시각 기준 현재 시각
     * @param images    사진 URL 을 만들 CDN 설정
     * @param distanceM 고객 위치까지 거리 (m). 위치를 보내지 않았으면 null
     */
    public static StoreSummaryResponse of(Store store, BusinessHour todayHour, LocalTime now, ImageProperties images,
                                          Integer distanceM) {
        return new StoreSummaryResponse(
                store.getId(),
                store.getName(),
                store.getCategory(),
                store.getMinOrderAmount(),
                store.getDeliveryFee(),
                store.getOperationStatus(),
                store.isOpenAt(todayHour, now),
                BusinessHourResponse.from(todayHour),
                store.getImageKey(),
                images.urlOf(store.getImageKey()),
                distanceM
        );
    }
}
