package com.baedalsak.store.dto.request;

import com.baedalsak.store.domain.SaleStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 판매·품절 전환 요청 (PATCH /v1/stores/{storeId}/menus/{menuId}/status)
 */
public record SaleStatusRequest(
        @NotNull(message = "판매 상태를 선택해 주세요.")
        SaleStatus saleStatus
) {
}
