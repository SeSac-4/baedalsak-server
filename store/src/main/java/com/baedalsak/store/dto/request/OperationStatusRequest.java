package com.baedalsak.store.dto.request;

import com.baedalsak.store.domain.OperationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 운영 상태 변경 요청 (PATCH /v1/stores/{storeId}/status)
 */
public record OperationStatusRequest(
        @NotNull(message = "운영 상태를 선택해 주세요.")
        OperationStatus operationStatus
) {
}
