package com.baedalsak.payment.client.toss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스 결제 취소 클라이언트 (#127). 전액 취소(cancelAmount 없음) · 멱등 키 = payment_cancel.idempotency_key,
 * 응답 모양은 토스 Payment 객체(cancels), 에러 본문은 {code, message}
 */
class TossCancelClientTest {

    MockRestServiceServer server;
    TossCancelClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = new TossConfig().tossCancelRestClient(RestClient.builder(),
                new TossProperties("http://toss", "test_sk", Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofSeconds(15)), Duration.ofSeconds(60)).mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossCancelClient(builder.build());
    }

    @Test
    void 전액_취소를_사유와_멱등_키로_보내고_취소_거래를_받는다() {
        server.expect(requestTo("http://toss/v1/payments/tgen_key/cancel")).andExpect(method(POST))
                .andExpect(header("Authorization", "Basic dGVzdF9zazo="))
                .andExpect(header("Idempotency-Key", "cancel-key-1"))
                // 전액 취소: cancelAmount 를 보내지 않음
                .andExpect(content().json("""
                        {"cancelReason":"재료 소진"}
                        """, true))
                .andRespond(withSuccess("""
                        {"paymentKey":"tgen_key","orderId":"BDS-1005-1","status":"CANCELED","totalAmount":18000,
                         "balanceAmount":0,
                         "cancels":[{"cancelAmount":18000,"cancelReason":"재료 소진","canceledAt":"2026-11-06T12:00:00+09:00",
                                     "transactionKey":"tx_cancel_1","cancelStatus":"DONE"}]}
                        """, MediaType.APPLICATION_JSON));

        TossCancelResponse response = client.cancel("tgen_key", "재료 소진", "cancel-key-1", 7L);

        assertThat(response.isCanceled()).isTrue();
        assertThat(response.lastCancel().transactionKey()).isEqualTo("tx_cancel_1");
        assertThat(response.lastCancel().cancelAmount()).isEqualTo(18000);
        assertThat(response.lastCancel().canceledAt()).isEqualTo("2026-11-06T12:00:00+09:00");
        server.verify();
    }

    @Test
    void 토스_에러_본문의_코드와_메시지를_예외에_담는다() {
        server.expect(requestTo("http://toss/v1/payments/tgen_key/cancel"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"ALREADY_CANCELED_PAYMENT","message":"이미 취소된 결제 입니다."}
                                """));

        assertThatThrownBy(() -> client.cancel("tgen_key", "재료 소진", "cancel-key-1", 7L))
                .isInstanceOfSatisfying(TossApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("ALREADY_CANCELED_PAYMENT");
                    assertThat(e.getTossMessage()).isEqualTo("이미 취소된 결제 입니다.");
                });
    }

    @Test
    void 응답이_없으면_결과_모름() {
        server.expect(requestTo("http://toss/v1/payments/tgen_key/cancel"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.cancel("tgen_key", "재료 소진", "cancel-key-1", 7L))
                .isInstanceOfSatisfying(TossApiException.class, e -> assertThat(e.isNoResponse()).isTrue());
    }

    @Test
    void 결제_조회로_취소_여부를_확인한다() {
        server.expect(requestTo("http://toss/v1/payments/tgen_key")).andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"paymentKey":"tgen_key","orderId":"BDS-1005-1","status":"CANCELED","totalAmount":18000,
                         "cancels":[{"cancelAmount":18000,"canceledAt":"2026-11-06T12:00:00+09:00","transactionKey":"tx_cancel_1"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getPayment("tgen_key", 7L).isCanceled()).isTrue();
    }
}
