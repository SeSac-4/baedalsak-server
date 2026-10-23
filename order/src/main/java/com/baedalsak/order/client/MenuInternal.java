package com.baedalsak.order.client;

/**
 * store [내부] 메뉴 일괄 조회 응답 항목 (GET /internal/stores/{storeId}/menus?ids=).
 * 없는 메뉴·삭제된 메뉴는 응답에서 빠지므로 id 로 요청과 짝을 맞춘다.
 */
public record MenuInternal(
        Long id,
        String name,
        Integer price,
        SaleStatus saleStatus
) {

    public enum SaleStatus {
        ON_SALE,
        SOLD_OUT
    }

    public boolean isOnSale() {
        return saleStatus == SaleStatus.ON_SALE;
    }
}
