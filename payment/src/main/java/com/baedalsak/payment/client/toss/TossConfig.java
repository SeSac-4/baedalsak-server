package com.baedalsak.payment.client.toss;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 토스페이먼츠 API 를 부르는 RestClient. 인증은 시크릿 키 뒤에 ':' 를 붙여 Base64 로 인코딩한 Basic 인증.
 * 승인은 5초 안에 끝나지 않으면 끊고 재시도하지 않는다 (N1.4, 중복 승인 방지).
 */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossConfig {

    @Bean
    RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
        String credentials = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return builder.baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .requestFactory(factory)
                .build();
    }
}
