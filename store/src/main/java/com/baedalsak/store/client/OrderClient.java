package com.baedalsak.store.client;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * order [내부] API 클라이언트. 가게 폐업 전에 진행 중 주문을 확인한다 (#176).
 * 응답이 없거나 실패하면 503 DEPENDENCY_UNAVAILABLE → 폐업하지 않는다.
 */
@Slf4j
@Component
public class OrderClient {

    private final RestClient restClient;

    public OrderClient(@Qualifier("orderRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // 결제완료 · 조리중 · 배달중 주문 수
    public long countActiveOrders(Long storeId) {
        try {
            ActiveOrderCount result = restClient.get()
                    .uri(uri -> uri.path("/internal/orders/active-count").queryParam("storeId", storeId).build())
                    .retrieve()
                    .body(ActiveOrderCount.class);
            if (result == null) {
                throw new RestClientException("빈 응답");
            }
            return result.count();
        } catch (RestClientException e) {
            log.warn("order 진행 중 주문 수 조회 실패 storeId={}", storeId, e);
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    record ActiveOrderCount(long count) {
    }
}
