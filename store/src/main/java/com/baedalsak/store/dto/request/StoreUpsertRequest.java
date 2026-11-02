package com.baedalsak.store.dto.request;

import com.baedalsak.store.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 가게 등록·수정 요청 (POST /v1/stores, PUT /v1/stores/{storeId}).
 * 수정은 전체 교체라 imageKey 가 null 이면 사진을 지운다. 등록 시점엔 업로드 URL 을 받을 수 없어 imageKey 는 무시한다
 */
public record StoreUpsertRequest(
        @NotBlank(message = "가게 이름을 입력해 주세요.")
        @Size(max = 100, message = "가게 이름은 100자 이하로 입력해 주세요.")
        String name,

        @NotNull(message = "카테고리를 선택해 주세요.")
        Category category,

        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = 255, message = "주소는 255자 이하로 입력해 주세요.")
        String address,

        @NotNull(message = "최소주문금액을 입력해 주세요.")
        @PositiveOrZero(message = "최소주문금액은 0원 이상이어야 합니다.")
        Integer minOrderAmount,

        @NotNull(message = "배달비를 입력해 주세요.")
        @PositiveOrZero(message = "배달비는 0원 이상이어야 합니다.")
        Integer deliveryFee,

        // 사진 S3 키 (업로드 URL 발급 응답의 imageKey). null 이면 사진 없음 (#110)
        @Size(max = 255, message = "사진을 다시 올려 주세요.")
        String imageKey
) {
}
