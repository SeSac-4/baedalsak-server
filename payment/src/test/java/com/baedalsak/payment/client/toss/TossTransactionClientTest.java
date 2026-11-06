package com.baedalsak.payment.client.toss;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스 거래 조회 클라이언트 (#154). 기간은 한국 시각 yyyy-MM-dd'T'HH:mm:ss, 한 페이지가 가득 차면 마지막 transactionKey 로 다음 페이지,
 * 에러 응답 · 응답 없음은 TossApiException (토스 응답 모양은 노트 9절에서 테스트 키로 확인)
 */
class TossTransactionClientTest {

    static final LocalDateTime START = LocalDateTime.of(2026, 11, 6, 0, 0);
    static final LocalDateTime END = LocalDateTime.of(2026, 11, 6, 17, 30);
    static final String RANGE = "startDate=2026-11-06T00:00:00&endDate=2026-11-06T17:30:00";

    MockRestServiceServer server;

    TossTransactionClient client(int limit) {
        RestClient.Builder builder = new TossConfig().tossCancelRestClient(RestClient.builder(),
                new TossProperties("http://toss", "test_sk", Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofSeconds(15)), Duration.ofSeconds(60)).mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        return new TossTransactionClient(builder.build(), limit);
    }

    @Test
    void 기간_안의_승인과_취소_거래를_받는다() {
        TossTransactionClient client = client(5000);
        server.expect(requestTo("http://toss/v1/transactions?" + RANGE + "&limit=5000")).andExpect(method(GET))
                .andExpect(header("Authorization", "Basic dGVzdF9zazo="))
                .andRespond(withSuccess("""
                        [{"mId":"tvivarepublica","transactionKey":"tx_1","paymentKey":"pk_1","orderId":"BDS-12-1",
                          "method":"간편결제","status":"DONE","transactionAt":"2026-11-06T18:21:00+09:00","currency":"KRW","amount":11000},
                         {"mId":"tvivarepublica","transactionKey":"tx_2","paymentKey":"pk_1","orderId":"BDS-12-1",
                          "method":"간편결제","status":"CANCELED","transactionAt":"2026-11-06T18:55:27+09:00","currency":"KRW","amount":11000}]
                        """, MediaType.APPLICATION_JSON));

        List<TossTransaction> transactions = client.getTransactions(START, END);

        assertThat(transactions).extracting(TossTransaction::transactionKey).containsExactly("tx_1", "tx_2");
        assertThat(transactions.get(0).isApproval()).isTrue();
        assertThat(transactions.get(1).isCancel()).isTrue();
        assertThat(transactions.get(1).amount()).isEqualTo(11000);
        assertThat(transactions.get(1).transactionAt()).isEqualTo("2026-11-06T18:55:27+09:00");
        server.verify();
    }

    @Test
    void 한_페이지가_가득_차면_마지막_거래_키로_다음_페이지를_받는다() {
        TossTransactionClient client = client(2);
        server.expect(requestTo("http://toss/v1/transactions?" + RANGE + "&limit=2"))
                .andRespond(withSuccess("""
                        [{"transactionKey":"tx_1","paymentKey":"pk_1","status":"DONE","amount":11000},
                         {"transactionKey":"tx_2","paymentKey":"pk_2","status":"DONE","amount":21000}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://toss/v1/transactions?" + RANGE + "&limit=2&startingAfter=tx_2"))
                .andRespond(withSuccess("""
                        [{"transactionKey":"tx_3","paymentKey":"pk_1","status":"CANCELED","amount":11000}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getTransactions(START, END)).extracting(TossTransaction::transactionKey)
                .containsExactly("tx_1", "tx_2", "tx_3");
        server.verify();
    }

    @Test
    void 거래가_없으면_빈_목록() {
        TossTransactionClient client = client(5000);
        server.expect(requestTo("http://toss/v1/transactions?" + RANGE + "&limit=5000"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.getTransactions(START, END)).isEmpty();
    }

    @Test
    void 토스_에러_응답은_코드를_담아_던진다() {
        TossTransactionClient client = client(5000);
        server.expect(requestTo("http://toss/v1/transactions?" + RANGE + "&limit=5000"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"INVALID_DATE","message":"날짜 데이터가 잘못 되었습니다."}
                                """));

        assertThatThrownBy(() -> client.getTransactions(START, END))
                .isInstanceOfSatisfying(TossApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("INVALID_DATE");
                });
    }

    @Test
    void 게이트웨이_504_처럼_본문이_JSON_이_아니어도_상태를_담아_던진다() {
        TossTransactionClient client = client(5000);
        server.expect(requestTo("http://toss/v1/transactions?" + RANGE + "&limit=5000"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT).contentType(MediaType.TEXT_PLAIN).body("stream timeout"));

        assertThatThrownBy(() -> client.getTransactions(START, END))
                .isInstanceOfSatisfying(TossApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(504);
                    assertThat(e.getCode()).isNull();
                });
    }

    @Test
    void 응답이_없으면_결과_모름으로_던진다() {
        TossTransactionClient client = client(5000);
        server.expect(requestTo("http://toss/v1/transactions?" + RANGE + "&limit=5000"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.getTransactions(START, END))
                .isInstanceOfSatisfying(TossApiException.class, e -> assertThat(e.isNoResponse()).isTrue());
    }
}
