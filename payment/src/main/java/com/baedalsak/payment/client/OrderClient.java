package com.baedalsak.payment.client;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * order [내부] API 클라이언트. 결제 요청 생성 때 주문 금액·상태·스냅샷을 받는다 (설계서 '동기 호출' 탭).
 * 주문이 없으면 빈 Optional, 응답이 없거나 실패하면 503 DEPENDENCY_UNAVAILABLE.
 */
@Slf4j
@Component
public class OrderClient {

    private final RestClient restClient;

    public OrderClient(@Qualifier("orderRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<OrderInternal> getOrder(Long orderId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/orders/{orderId}", orderId)
                    .retrieve()
                    .body(OrderInternal.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("order 주문 조회 실패 orderId={}", orderId, e);
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }
}
