package com.baedalsak.order.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * store·member·payment [내부] API 를 부르는 RestClient. Gateway 를 거치지 않고 K8s DNS 로 직접 호출한다 (설계서 '동기 호출' 탭).
 * Boot 가 만든 RestClient.Builder 를 써야 traceId 가 HTTP 헤더로 전파된다 (docs 6-2).
 */
@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiConfig {

    @Bean
    RestClient storeRestClient(RestClient.Builder builder, InternalApiProperties properties) {
        return builder.baseUrl(properties.storeUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean
    RestClient memberRestClient(RestClient.Builder builder, InternalApiProperties properties) {
        return builder.baseUrl(properties.memberUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean
    RestClient paymentCreateRestClient(RestClient.Builder builder, InternalApiProperties properties) {
        return paymentRestClient(builder, properties, properties.paymentCreateReadTimeout());
    }

    @Bean
    RestClient paymentConfirmRestClient(RestClient.Builder builder, InternalApiProperties properties) {
        return paymentRestClient(builder, properties, properties.paymentConfirmReadTimeout());
    }

    @Bean
    RestClient paymentExpireRestClient(RestClient.Builder builder, InternalApiProperties properties) {
        return paymentRestClient(builder, properties, properties.paymentExpireReadTimeout());
    }

    private RestClient paymentRestClient(RestClient.Builder builder, InternalApiProperties properties,
                                         Duration readTimeout) {
        return builder.baseUrl(properties.paymentUrl())
                .requestFactory(requestFactory(properties.connectTimeout(), readTimeout))
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(InternalApiProperties properties) {
        return requestFactory(properties.connectTimeout(), properties.readTimeout());
    }

    // 응답이 없을 때 무한정 기다리지 않도록 연결·읽기 시간 제한
    private SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
