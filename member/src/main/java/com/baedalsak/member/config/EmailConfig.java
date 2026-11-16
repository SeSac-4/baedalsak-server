package com.baedalsak.member.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * SES 클라이언트 — email.sender=ses 일 때만 만든다 (log 면 AWS 연결 자체를 안 함).
 * 자격 증명은 Cognito · SNS 와 같은 기본 체인 (로컬 ~/.aws / AWS Pod Identity).
 */
@Configuration
@ConditionalOnProperty(name = "email.sender", havingValue = "ses", matchIfMissing = true)
public class EmailConfig {

  @Bean
  public SesV2Client sesV2Client(@Value("${email.region}") String region) {
    return SesV2Client.builder()
        .region(Region.of(region))
        .overrideConfiguration(c -> c
            .apiCallAttemptTimeout(Duration.ofSeconds(2))
            .apiCallTimeout(Duration.ofSeconds(5)))
        .build();
  }
}
