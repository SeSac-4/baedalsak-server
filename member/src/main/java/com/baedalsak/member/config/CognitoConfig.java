package com.baedalsak.member.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * Cognito 사용자 풀 API 클라이언트.
 * 자격 증명은 SDK 기본 체인이 찾는다 (로컬: ~/.aws 의 IAM 키 / AWS: Pod Identity).
 */
@Configuration
public class CognitoConfig {

  @Bean
  public CognitoIdentityProviderClient cognitoClient(CognitoProperties props) {
    return CognitoIdentityProviderClient.builder()
        .region(Region.of(props.region()))
        // Cognito 가 느리거나 멈추면 오래 기다리지 않고 실패 → 503 으로 응답 (스레드가 묶이지 않게)
        .overrideConfiguration(c -> c
            .apiCallAttemptTimeout(Duration.ofSeconds(2))   // 한 번 시도당 최대
            .apiCallTimeout(Duration.ofSeconds(5)))         // 재시도 포함 전체 최대
        .build();
  }
}