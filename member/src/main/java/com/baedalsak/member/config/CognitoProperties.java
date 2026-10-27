package com.baedalsak.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/** application.yml 의 cognito.* 값. 비어 있으면 기동 실패 (fail fast). */
@Validated
@ConfigurationProperties(prefix = "cognito")
public record CognitoProperties(
    @NotBlank String region,
    @NotBlank String userPoolId,
    @NotBlank String clientId
) {
}