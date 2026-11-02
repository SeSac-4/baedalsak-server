package com.baedalsak.store.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml 의 image.* 값 (#110). 비어 있으면 기동 실패 (fail fast).
 *
 * @param objectPrefix S3 객체 키 앞에 붙는 경로. 객체 키 = objectPrefix + imageKey
 * @param baseUrl      CDN 기본 주소. imageUrl = baseUrl + imageKey
 */
@Validated
@ConfigurationProperties(prefix = "image")
public record ImageProperties(
        @NotBlank String region,
        @NotBlank String bucket,
        @NotNull String objectPrefix,
        @NotBlank String baseUrl,
        @NotNull Duration uploadUrlTtl
) {

    // 키가 없으면 null
    public String urlOf(String imageKey) {
        return imageKey == null ? null : baseUrl + imageKey;
    }
}
