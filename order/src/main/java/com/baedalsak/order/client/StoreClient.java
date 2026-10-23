package com.baedalsak.order.client;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * store [내부] API 클라이언트. 장바구니 담기·주문 생성 때 쓰기 시점 검증에 쓴다 (설계서 '동기 호출' 탭).
 * 가게가 없으면 빈 Optional, 응답이 없거나 실패하면 503 DEPENDENCY_UNAVAILABLE.
 */
@Slf4j
@Component
public class StoreClient {

    private final RestClient restClient;

    public StoreClient(@Qualifier("storeRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // 영업 여부·최소주문금액·배달비·사장님 ID
    public Optional<StoreInternal> getStore(Long storeId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/stores/{storeId}", storeId)
                    .retrieve()
                    .body(StoreInternal.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("store 가게 조회 실패 storeId={}", storeId, e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    // 메뉴 가격·판매 상태. 없는·삭제된 메뉴는 결과에서 빠진다. 가게가 없으면 빈 Optional
    public Optional<List<MenuInternal>> getMenus(Long storeId, Collection<Long> menuIds) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(uri -> uri.path("/internal/stores/{storeId}/menus")
                            .queryParam("ids", menuIds.toArray())
                            .build(storeId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<MenuInternal>>() {
                    }));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("store 메뉴 조회 실패 storeId={}, menuIds={}", storeId, menuIds, e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }
}
