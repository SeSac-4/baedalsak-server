package com.baedalsak.payment.client.toss;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.dto.request.ConfirmPaymentRequest;
import com.baedalsak.payment.error.PaymentErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스 결제 승인 클라이언트. 응답 모양은 토스 결제 승인 API(Payment 객체) 기준
 */
class TossPaymentsClientTest {

    static final ConfirmPaymentRequest REQUEST = new ConfirmPaymentRequest("tgen_key", "BDS-1005-1", 18000);

    MockRestServiceServer server;
    TossPaymentsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = new TossConfig().tossRestClient(RestClient.builder(),
                new TossProperties("http://toss", "test_sk", java.time.Duration.ofSeconds(5))).mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentsClient(builder.build());
    }

    @Test
    void 승인_요청을_보내고_결과를_받는다() {
        server.expect(requestTo("http://toss/v1/payments/confirm")).andExpect(method(POST))
                // "test_sk:" Base64
                .andExpect(header("Authorization", "Basic dGVzdF9zazo="))
                .andExpect(content().json("""
                        {"paymentKey":"tgen_key","orderId":"BDS-1005-1","amount":18000}
                        """))
                .andRespond(withSuccess("""
                        {"paymentKey":"tgen_key","orderId":"BDS-1005-1","status":"DONE","method":"카드",
                         "totalAmount":18000,"approvedAt":"2026-10-26T15:30:00+09:00",
                         "receipt":{"url":"https://dashboard.tosspayments.com/receipt/1"}}
                        """, MediaType.APPLICATION_JSON));

        TossConfirmResponse response = client.confirm(REQUEST);

        assertThat(response.paymentKey()).isEqualTo("tgen_key");
        assertThat(response.method()).isEqualTo("카드");
        assertThat(response.approvedAt()).isEqualTo("2026-10-26T15:30:00+09:00");
        assertThat(response.receiptUrl()).isEqualTo("https://dashboard.tosspayments.com/receipt/1");
    }

    @Test
    void 토스가_거절하면_502() {
        server.expect(requestTo("http://toss/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"ALREADY_PROCESSED_PAYMENT\",\"message\":\"이미 처리된 결제 입니다.\"}"));

        assertGatewayError();
    }

    @Test
    void 토스_서버_오류면_502() {
        server.expect(requestTo("http://toss/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertGatewayError();
    }

    @Test
    void 응답이_없으면_502() {
        server.expect(requestTo("http://toss/v1/payments/confirm"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertGatewayError();
    }

    @Test
    void 연결할_수_없으면_502() {
        server.expect(requestTo("http://toss/v1/payments/confirm"))
                .andRespond(withException(new IOException("connection refused")));

        assertGatewayError();
    }

    private void assertGatewayError() {
        assertThatThrownBy(() -> client.confirm(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
    }
}
