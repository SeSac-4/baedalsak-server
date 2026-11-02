package com.baedalsak.store.dto.request;

import com.baedalsak.store.domain.ImageTarget;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 사진 업로드 URL 발급 요청 (POST /v1/stores/{storeId}/images/upload-url, #110).
 * 검증 메시지는 프론트가 사진 칸 아래에 그대로 보여 준다.
 */
public record ImageUploadUrlRequest(
        @NotNull(message = "사진을 올릴 곳을 선택해 주세요.")
        ImageTarget target,

        @NotNull(message = "JPG, PNG, WEBP 사진만 올릴 수 있습니다.")
        @Pattern(regexp = "image/(jpeg|png|webp)", message = "JPG, PNG, WEBP 사진만 올릴 수 있습니다.")
        String contentType,

        @NotNull(message = "사진 크기를 확인할 수 없습니다.")
        @Min(value = 1, message = "빈 파일은 올릴 수 없습니다.")
        @Max(value = ImageUploadUrlRequest.MAX_SIZE, message = "사진은 5MB 이하만 올릴 수 있습니다.")
        Long size
) {

    public static final long MAX_SIZE = 5 * 1024 * 1024;
}
