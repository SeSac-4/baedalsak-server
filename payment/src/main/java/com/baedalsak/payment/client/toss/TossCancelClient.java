package com.baedalsak.payment.client.toss;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

/**
 * 토스 결제 취소 클라이언트 (#127, 설계서 '동기 호출' 탭 CancelPayment 명령 처리).
 * 전액 취소라 cancelAmount 는 보내지 않고 cancelReason(200자) 만 보낸다. 멱등 키는 payment_cancel.idempotency_key (재시도 때 같은 값).
 * 결제 처리 API 라 읽기 제한 60초 (승인 · 조회의 5초와 다른 RestClient). 실패는 TossApiException 으로 던지고 분류는 호출하는 쪽이 한다.
 */
@Slf4j
@Component
public class TossCancelClient {

  private final RestClient restClient;

  public TossCancelClient(@Qualifier("tossCancelRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * 전액 취소.
   * @throws TossApiException 토스 에러 응답 또는 응답 없음
   */
  public TossCancelResponse cancel(String paymentKey, String reason, String idempotencyKey, Long paymentId) {
    try {
      TossCancelResponse response = restClient.post()
          .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
          .header(TossPaymentsClient.IDEMPOTENCY_KEY, idempotencyKey)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new CancelBody(reason))
          .retrieve()
          .body(TossCancelResponse.class);
      if (response == null) {
        throw new RestClientException("empty cancel response");
      }
      return response;
    } catch (RestClientResponseException e) {
      throw rejected("결제 취소", paymentId, e);
    } catch (RestClientException e) {
      log.warn("토스 결제 취소 응답 없음 paymentId={}", paymentId, e);
      throw TossApiException.noResponse(e);
    }
  }

  /**
   * 결제 조회 (취소됐는지 확인). 취소 거래 목록(cancels)까지 받는다.
   * @throws TossApiException 토스 에러 응답 또는 응답 없음
   */
  public TossCancelResponse getPayment(String paymentKey, Long paymentId) {
    try {
      TossCancelResponse response = restClient.get()
          .uri("/v1/payments/{paymentKey}", paymentKey)
          .retrieve()
          .body(TossCancelResponse.class);
      if (response == null) {
        throw new RestClientException("empty payment response");
      }
      return response;
    } catch (RestClientResponseException e) {
      throw rejected("취소 확인 조회", paymentId, e);
    } catch (RestClientException e) {
      log.warn("토스 취소 확인 조회 응답 없음 paymentId={}", paymentId, e);
      throw TossApiException.noResponse(e);
    }
  }

  // 토스 에러 본문 {code, message} 를 남긴다 (결제 키 · 멱등 키는 남기지 않음)
  private TossApiException rejected(String api, Long paymentId, RestClientResponseException e) {
    log.warn("토스 {} 에러 응답 paymentId={}, status={}, body={}",
        api, paymentId, e.getStatusCode().value(), e.getResponseBodyAsString());
    TossPaymentsClient.TossErrorBody body;
    try {
      body = e.getResponseBodyAs(TossPaymentsClient.TossErrorBody.class);
    } catch (RuntimeException parseFailure) {
      body = null;
    }
    return TossApiException.rejected(e.getStatusCode().value(), body == null ? null : body.code(),
        body == null ? null : body.message());
  }

  record CancelBody(String cancelReason) {
  }
}
