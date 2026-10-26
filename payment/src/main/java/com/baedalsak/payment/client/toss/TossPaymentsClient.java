package com.baedalsak.payment.client.toss;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.dto.request.ConfirmPaymentRequest;
import com.baedalsak.payment.error.PaymentErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 토스페이먼츠 결제 승인 클라이언트 (설계서 '동기 호출' 탭). 요청 본문은 successUrl 로 받은 paymentKey·orderId·amount 그대로.
 * 토스가 거절하거나(4xx·5xx) 5초 안에 응답이 없으면 502 PAYMENT_GATEWAY_ERROR. 재시도하지 않는다 (N1.4).
 */
@Slf4j
@Component
public class TossPaymentsClient {

    private final RestClient restClient;

    public TossPaymentsClient(@Qualifier("tossRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public TossConfirmResponse confirm(ConfirmPaymentRequest request) {
        try {
            TossConfirmResponse response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TossConfirmResponse.class);
            if (response == null) {
                throw new RestClientException("empty confirm response");
            }
            return response;
        } catch (RestClientResponseException e) {
            // 토스 에러 본문 {code, message} 를 남긴다 (결제 키는 남기지 않음)
            log.warn("토스 결제 승인 거절 tossOrderId={}, status={}, body={}",
                    request.orderId(), e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        } catch (RestClientException e) {
            log.warn("토스 결제 승인 실패 tossOrderId={}", request.orderId(), e);
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }
}
