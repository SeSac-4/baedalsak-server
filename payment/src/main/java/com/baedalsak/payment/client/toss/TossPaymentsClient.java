package com.baedalsak.payment.client.toss;

import com.baedalsak.payment.dto.request.ConfirmPaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 토스페이먼츠 결제 클라이언트 (설계서 '동기 호출' 탭).
 * 승인 요청 본문은 successUrl 로 받은 paymentKey·orderId·amount 그대로, 멱등 키는 paymentKey (#125).
 * 실패는 TossApiException 으로 던지고, 재전송·확정 실패 판단은 호출하는 쪽이 토스 에러 코드로 한다.
 */
@Slf4j
@Component
public class TossPaymentsClient {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RestClient restClient;

    public TossPaymentsClient(@Qualifier("tossRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 결제 승인. 같은 결제의 재호출은 같은 멱등 키(paymentKey)라 토스가 첫 결과를 돌려준다 (15일 유효).
     * @throws TossApiException 토스 에러 응답 또는 응답 없음
     */
    public TossConfirmResponse confirm(ConfirmPaymentRequest request) {
        try {
            TossConfirmResponse response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .header(IDEMPOTENCY_KEY, request.paymentKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TossConfirmResponse.class);
            if (response == null) {
                throw new RestClientException("empty confirm response");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw rejected("결제 승인", request.orderId(), e);
        } catch (RestClientException e) {
            log.warn("토스 결제 승인 응답 없음 tossOrderId={}", request.orderId(), e);
            throw TossApiException.noResponse(e);
        }
    }

    /**
     * 결제 조회 (GET /v1/payments/{paymentKey}). 결제위젯 연동 키는 orderId 조회가 막혀 paymentKey 로만 조회한다.
     * @throws TossApiException 토스 에러 응답 또는 응답 없음
     */
    public TossConfirmResponse getPayment(String paymentKey, String tossOrderId) {
        try {
            TossConfirmResponse response = restClient.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .body(TossConfirmResponse.class);
            if (response == null) {
                throw new RestClientException("empty payment response");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw rejected("결제 조회", tossOrderId, e);
        } catch (RestClientException e) {
            log.warn("토스 결제 조회 응답 없음 tossOrderId={}", tossOrderId, e);
            throw TossApiException.noResponse(e);
        }
    }

    // 토스 에러 본문 {code, message} 를 남긴다 (결제 키는 남기지 않음). 본문을 읽지 못하면 code 는 null
    private TossApiException rejected(String api, String tossOrderId, RestClientResponseException e) {
        log.warn("토스 {} 에러 응답 tossOrderId={}, status={}, body={}",
                api, tossOrderId, e.getStatusCode().value(), e.getResponseBodyAsString());
        TossErrorBody body = readErrorBody(e);
        return TossApiException.rejected(e.getStatusCode().value(), body == null ? null : body.code(),
                body == null ? null : body.message());
    }

    private static TossErrorBody readErrorBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(TossErrorBody.class);
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }

    // 토스 에러 응답 본문
    record TossErrorBody(String code, String message) {
    }
}
