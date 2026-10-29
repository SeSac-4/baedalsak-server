package com.baedalsak.payment.mail;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * SES v2 클라이언트. 자격 증명은 SDK 기본 체인이 찾는다 (AWS: Pod Identity). local 프로필은 SES 를 쓰지 않는다.
 */
@Configuration
@EnableConfigurationProperties(PaymentMailProperties.class)
public class SesConfig {

  @Bean
  @Profile("!local")
  SesV2Client sesClient(PaymentMailProperties properties) {
    return SesV2Client.builder()
        .region(Region.of(properties.region()))
        // SES 가 느리거나 멈추면 오래 기다리지 않고 실패 → 리스너 재시도 (리스너 스레드가 묶이지 않게)
        .overrideConfiguration(c -> c
            .apiCallAttemptTimeout(Duration.ofSeconds(2))
            .apiCallTimeout(Duration.ofSeconds(5)))
        .build();
  }
}
