package com.baedalsak.store.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로컬 API 를 부르는 RestClient (#210).
 * Boot 가 만든 RestClient.Builder 를 써야 traceId 가 이어진다 (docs 6-2).
 */
@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoConfig {

    @Bean
    RestClient kakaoRestClient(RestClient.Builder builder, KakaoProperties properties) {
        // 가게 등록 · 수정이 카카오 응답을 오래 기다리지 않도록 연결 · 읽기 시간 제한
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(properties.localBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                .requestFactory(factory)
                .build();
    }
}
