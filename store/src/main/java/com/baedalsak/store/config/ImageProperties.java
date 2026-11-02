package com.baedalsak.store.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml 의 image.* 값 (#110). 비어 있으면 기동 실패 (fail fast).
 *
 * @param bucket  사진 버킷. S3 객체 키 = imageKey (stores/{storeId}/…)
 * @param baseUrl CDN 기본 주소. imageUrl = baseUrl + "/" + imageKey (끝의 / 는 있어도 없어도 됨).
 *                CloudFront /images/* 가 앞의 /images 를 떼고 S3 로 보낸다
 */
@Validated
@ConfigurationProperties(prefix = "image")
public record ImageProperties(
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String baseUrl,
        @NotNull Duration uploadUrlTtl
) {

    // 인프라가 주는 값은 끝에 / 가 없다 (https://baedalsak.click/images). 있든 없든 하나로 맞춘다
    public ImageProperties {
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    // 키가 없으면 null
    public String urlOf(String imageKey) {
        return imageKey == null ? null : baseUrl + "/" + imageKey;
    }
}
