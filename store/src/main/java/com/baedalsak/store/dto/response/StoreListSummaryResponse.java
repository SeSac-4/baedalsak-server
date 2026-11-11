package com.baedalsak.store.dto.response;

import com.baedalsak.store.domain.Category;

import java.util.Map;

/**
 * 가게 목록 통계 (GET /v1/stores/summary, #169). 목록이 커서로 나뉘어 프론트가 직접 셀 수 없는 값을 준다.
 * - 홈 배너: 지금 영업 중인 가게 수 · 가장 낮은 배달비 (가게가 없으면 null)
 * - PC 카테고리 타일: 전체 · 카테고리별 가게 수 (가게가 없는 카테고리도 0 으로, enum 순서대로)
 */
public record StoreListSummaryResponse(
        long totalCount,
        long openCount,
        Integer minDeliveryFee,
        Map<Category, Long> categoryCounts
) {
}
