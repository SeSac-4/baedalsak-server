package com.baedalsak.payment.client.toss;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

/**
 * 토스 거래 조회 클라이언트 (#154, 설계서 '동기 호출' PG 대사 — 60초 타임아웃).
 * 기간 안의 승인 · 취소 거래를 모두 받는다. 한 페이지가 가득 차면 마지막 transactionKey 로 다음 페이지(startingAfter)를 이어 받는다.
 * 테스트 환경에서 limit 이 남은 거래 수보다 작으면 504 가 나서(노트 9절) 한 페이지를 크게(최대 5000) 받는다.
 * 읽기 60초는 결제 취소와 같은 RestClient. 실패는 TossApiException.
 */
@Slf4j
@Component
public class TossTransactionClient {

  static final int MAX_LIMIT = 5000;
  /** 토스 거래 조회 기간 형식 (yyyy-MM-dd'T'HH:mm:ss, 한국 시각) */
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
  private static final ParameterizedTypeReference<List<TossTransaction>> TRANSACTIONS = new ParameterizedTypeReference<>() {
  };

  private final RestClient restClient;
  private final int limit;

  public TossTransactionClient(@Qualifier("tossCancelRestClient") RestClient restClient,
      @Value("${payment-reconcile.page-size:" + MAX_LIMIT + "}") int limit) {
    this.restClient = restClient;
    this.limit = limit;
  }

  /**
   * 기간 안의 거래 전체 (한국 시각).
   * @throws TossApiException 토스 에러 응답 또는 응답 없음 (504 포함)
   */
  public List<TossTransaction> getTransactions(LocalDateTime start, LocalDateTime end) {
    List<TossTransaction> all = new ArrayList<>();
    String startingAfter = null;
    while (true) {
      List<TossTransaction> page = getPage(start, end, startingAfter);
      all.addAll(page);
      if (page.size() < limit) {
        return all;
      }
      startingAfter = page.get(page.size() - 1).transactionKey();
    }
  }

  private List<TossTransaction> getPage(LocalDateTime start, LocalDateTime end, String startingAfter) {
    try {
      List<TossTransaction> page = restClient.get()
          .uri(uri -> {
            uri.path("/v1/transactions")
                .queryParam("startDate", DATE_FORMAT.format(start))
                .queryParam("endDate", DATE_FORMAT.format(end))
                .queryParam("limit", limit);
            if (startingAfter != null) {
              uri.queryParam("startingAfter", startingAfter);
            }
            return uri.build();
          })
          .retrieve()
          .body(TRANSACTIONS);
      return page == null ? List.of() : page;
    } catch (RestClientResponseException e) {
      log.warn("토스 거래 조회 에러 응답 status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
      TossPaymentsClient.TossErrorBody body;
      try {
        body = e.getResponseBodyAs(TossPaymentsClient.TossErrorBody.class);
      } catch (RuntimeException parseFailure) {
        body = null;
      }
      throw TossApiException.rejected(e.getStatusCode().value(), body == null ? null : body.code(),
          body == null ? null : body.message());
    } catch (RestClientException e) {
      log.warn("토스 거래 조회 응답 없음", e);
      throw TossApiException.noResponse(e);
    }
  }
}
