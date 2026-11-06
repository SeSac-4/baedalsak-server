package com.baedalsak.order.client;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorCode;
import com.baedalsak.common.error.ErrorResponse;
import com.baedalsak.order.error.OrderErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * payment [내부] API 클라이언트 (#128). 호출마다 읽기 시간 제한이 달라 RestClient 를 따로 쓴다 (사가 결정 1-3).
 * payment 가 준 에러는 같은 코드·메시지로 전달한다.
 */
@Slf4j
@Component
public class PaymentClient {

    private static final String ALREADY_APPROVED = "PAYMENT_ALREADY_APPROVED";

    private final RestClient createClient;
    private final RestClient confirmClient;
    private final RestClient expireClient;

    public PaymentClient(@Qualifier("paymentCreateRestClient") RestClient createClient,
                         @Qualifier("paymentConfirmRestClient") RestClient confirmClient,
                         @Qualifier("paymentExpireRestClient") RestClient expireClient) {
        this.createClient = createClient;
        this.confirmClient = confirmClient;
        this.expireClient = expireClient;
    }

    // 결제 요청 생성. 같은 주문의 재호출은 payment 가 기존 값을 돌려주므로 타임아웃이면 1회 다시 부른다
    public PaymentReady create(CreatePaymentCommand command) {
        try {
            try {
                return postCreate(command);
            } catch (ResourceAccessException e) {
                log.warn("payment 결제 요청 생성 응답 없음, 재호출 orderId={}", command.orderId(), e);
                return postCreate(command);
            }
        } catch (RestClientResponseException e) {
            throw relayed(e, CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("payment 결제 요청 생성 실패 orderId={}", command.orderId(), e);
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    // 결제 승인. 이미 승인된 결제면 성공으로 본다. 응답이 없으면 결과를 모르므로 502 (같은 paymentKey 로 다시 시도)
    public void confirm(ConfirmPaymentCommand command) {
        try {
            confirmClient.post()
                    .uri("/internal/payments/confirm")
                    .body(command)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            BusinessException relayed = relayed(e, OrderErrorCode.PAYMENT_GATEWAY_ERROR);
            if (!ALREADY_APPROVED.equals(relayed.getErrorCode().name())) {
                throw relayed;
            }
        } catch (RestClientException e) {
            log.warn("payment 결제 승인 응답 없음 orderId={}", command.orderId(), e);
            throw new BusinessException(OrderErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    // 결제 요청 만료 (1건). 키 있는 결제는 payment 가 토스를 조회한다
    public PaymentExpireResult expire(Long orderId) {
        try {
            List<PaymentExpireResult> results = expireClient.post()
                    .uri("/internal/payments/expire")
                    .body(Map.of("orderIds", List.of(orderId)))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PaymentExpireResult>>() {
                    });
            if (results == null || results.isEmpty()) {
                log.warn("payment 결제 요청 만료 응답 비어 있음 orderId={}", orderId);
                throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            return results.getFirst();
        } catch (RestClientResponseException e) {
            throw relayed(e, CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("payment 결제 요청 만료 실패 orderId={}", orderId, e);
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private PaymentReady postCreate(CreatePaymentCommand command) {
        return createClient.post()
                .uri("/internal/payments")
                .body(command)
                .retrieve()
                .body(PaymentReady.class);
    }

    // 공통 에러 형식이 아니면(프록시 응답 등) fallback 코드로 바꾼다
    private BusinessException relayed(RestClientResponseException e, ErrorCode fallback) {
        ErrorResponse body = errorBody(e);
        if (body == null || body.code() == null || body.message() == null) {
            log.warn("payment 에러 응답 형식 다름 status={}", e.getStatusCode().value());
            return new BusinessException(fallback);
        }
        return new BusinessException(new RelayedErrorCode(body.code(), e.getStatusCode().value(), body.message()),
                body.errors());
    }

    private ErrorResponse errorBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(ErrorResponse.class);
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }
}
