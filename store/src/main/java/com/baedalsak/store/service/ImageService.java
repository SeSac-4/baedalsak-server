package com.baedalsak.store.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.store.config.ClockConfig;
import com.baedalsak.store.config.ImageProperties;
import com.baedalsak.store.dto.request.ImageUploadUrlRequest;
import com.baedalsak.store.dto.response.ImageUploadUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.util.List;
import java.util.UUID;

/**
 * 가게·메뉴 사진 (#110). 프론트가 S3 에 직접 올리도록 presigned PUT URL 을 발급하고,
 * 가게·메뉴 저장 시 받은 키가 그 가게의 키인지 확인한다.
 */
@Service
@RequiredArgsConstructor
public class ImageService {

    private final S3Presigner s3Presigner;
    private final ImageProperties imageProperties;
    private final StoreOwnership storeOwnership;

    // 키: stores/{storeId}/{UUID}.{확장자}. Content-Type·Content-Length 를 서명에 넣어 형식·크기를 S3 에서도 강제한다
    public ImageUploadUrlResponse issueUploadUrl(LoginMember member, Long storeId, ImageUploadUrlRequest request) {
        storeOwnership.getOwnedStore(member, storeId);
        String imageKey = keyPrefix(storeId) + UUID.randomUUID() + "." + extensionOf(request.contentType());

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presign -> presign
                .signatureDuration(imageProperties.uploadUrlTtl())
                .putObjectRequest(put -> put
                        .bucket(imageProperties.bucket())
                        .key(imageKey)
                        .contentType(request.contentType())
                        .contentLength(request.size())));

        return new ImageUploadUrlResponse(presigned.url().toString(), imageKey,
                presigned.expiration().atZone(ClockConfig.KST).toOffsetDateTime());
    }

    // null(사진 없음) 이거나 그 가게의 키여야 한다. 다른 가게 사진 키를 넣지 못하게 막는다 (길이는 요청 DTO 에서 검증)
    public void requireOwnKey(Long storeId, String imageKey) {
        if (imageKey == null) {
            return;
        }
        if (!imageKey.startsWith(keyPrefix(storeId))) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                    List.of(new FieldErrorDetail("imageKey", "사진을 다시 올려 주세요.")));
        }
    }

    private static String keyPrefix(Long storeId) {
        return "stores/" + storeId + "/";
    }

    // contentType 은 요청 검증에서 jpeg·png·webp 만 통과한다
    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("지원하지 않는 형식: " + contentType);
        };
    }
}
