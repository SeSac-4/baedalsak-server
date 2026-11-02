package com.baedalsak.store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 메뉴 등록·수정 요청 (POST /v1/stores/{storeId}/menus, PUT /v1/stores/{storeId}/menus/{menuId})
 */
public record MenuUpsertRequest(
        @NotBlank(message = "메뉴 이름을 입력해 주세요.")
        @Size(max = 100, message = "메뉴 이름은 100자 이하로 입력해 주세요.")
        String name,

        // 선택
        @Size(max = 500, message = "메뉴 설명은 500자 이하로 입력해 주세요.")
        String description,

        @NotNull(message = "가격을 입력해 주세요.")
        @Positive(message = "가격은 0원보다 커야 합니다.")
        Integer price,

        // 사진 S3 키 (업로드 URL 발급 응답의 imageKey). null 이면 사진 없음 (#110)
        @Size(max = 255, message = "사진을 다시 올려 주세요.")
        String imageKey
) {
}
