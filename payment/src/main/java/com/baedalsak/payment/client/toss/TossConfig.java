package com.baedalsak.payment.client.toss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 토스페이먼츠 API 를 부르는 RestClient. 인증은 시크릿 키 뒤에 ':' 를 붙여 Base64 로 인코딩한 Basic 인증.
 * 연결 2초 · 읽기 5초 (승인 · 조회). 승인 재전송은 멱등 키로 중복 승인을 막고 1회만 하며,
 * 결제 확인 전체는 예산(confirm-budget) 안에서만 토스를 부른다 (#125, 설계서 '외부 연동' · '동기 호출').
 * 결제 취소는 읽기 60초 (토스 '타임아웃': 결제 처리 API 최대 60초, #127) 라 RestClient 를 따로 둔다.
 */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossConfig {

    @Bean
    RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
        return build(builder, properties, properties.readTimeout());
    }

    @Bean
    RestClient tossCancelRestClient(RestClient.Builder builder, TossProperties properties,
            @Value("${toss.cancel-read-timeout:60s}") Duration cancelReadTimeout) {
        return build(builder, properties, cancelReadTimeout);
    }

    private static RestClient build(RestClient.Builder builder, TossProperties properties, Duration readTimeout) {
        String credentials = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(readTimeout);
        return builder.clone()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .requestFactory(factory)
                .build();
    }
}
