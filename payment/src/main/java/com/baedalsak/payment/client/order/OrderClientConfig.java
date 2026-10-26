package com.baedalsak.payment.client.order;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * order [내부] API 를 부르는 RestClient. Gateway 를 거치지 않고 K8s DNS 로 직접 호출한다 (설계서 '동기 호출' 탭).
 * Boot 가 만든 RestClient.Builder 를 써야 traceId 가 HTTP 헤더로 전파된다 (docs 6-2).
 */
@Configuration
@EnableConfigurationProperties(OrderClientProperties.class)
public class OrderClientConfig {

    @Bean
    RestClient orderRestClient(RestClient.Builder builder, OrderClientProperties properties) {
        return builder.baseUrl(properties.orderUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    // 응답이 없을 때 무한정 기다리지 않도록 연결·읽기 시간 제한
    private SimpleClientHttpRequestFactory requestFactory(OrderClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
