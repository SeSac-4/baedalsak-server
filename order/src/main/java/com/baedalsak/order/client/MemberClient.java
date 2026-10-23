package com.baedalsak.order.client;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * member [내부] API 클라이언트. 주문 생성 때 고객 phone·email, 사장님 email 스냅샷에 쓴다 (설계서 '동기 호출' 탭).
 * 회원이 없으면 빈 Optional, 응답이 없거나 실패하면 503 DEPENDENCY_UNAVAILABLE.
 */
@Slf4j
@Component
public class MemberClient {

    private final RestClient restClient;

    public MemberClient(@Qualifier("memberRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<MemberInternal> getMember(Long memberId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/members/{memberId}", memberId)
                    .retrieve()
                    .body(MemberInternal.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("member 회원 조회 실패 memberId={}", memberId, e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }
}
