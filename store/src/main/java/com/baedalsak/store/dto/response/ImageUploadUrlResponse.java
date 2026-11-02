package com.baedalsak.store.dto.response;

import java.time.OffsetDateTime;

/**
 * 사진 업로드 URL (#110). 프론트는 uploadUrl 로 PUT(Content-Type 헤더 포함) 한 뒤
 * 가게·메뉴 저장 요청에 imageKey 를 넣는다
 */
public record ImageUploadUrlResponse(
        String uploadUrl,
        String imageKey,
        OffsetDateTime expiresAt
) {
}
