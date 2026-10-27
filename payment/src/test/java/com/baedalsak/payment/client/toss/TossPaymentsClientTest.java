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
                         "receipt":{"url":"https://dashboard.tosspayments.com/receipt/1"},
                         "card":{"issuerCode":"41","number":"51073761****913*","approveNo":"30024234"},
                         "easyPay":null}
                        """, MediaType.APPLICATION_JSON));

        TossConfirmResponse response = client.confirm(REQUEST);

        assertThat(response.paymentKey()).isEqualTo("tgen_key");
        assertThat(response.method()).isEqualTo("카드");
        assertThat(response.approvedAt()).isEqualTo("2026-10-26T15:30:00+09:00");
        assertThat(response.receiptUrl()).isEqualTo("https://dashboard.tosspayments.com/receipt/1");
        assertThat(response.approvalNumber()).isEqualTo("30024234");
    }

    @Test
    void 간편결제를_등록_카드로_결제하면_카드_승인번호를_받는다() {
        // 토스 코어 API 응답 예시: method 간편결제 + easyPay.provider + card.approveNo
        server.expect(requestTo("http://toss/v1/payments/confirm"))
                .andRespond(withSuccess("""
                        {"paymentKey":"tgen_key","orderId":"BDS-1005-1","status":"DONE","method":"간편결제",
                         "approvedAt":"2026-10-26T15:30:00+09:00",
                         "card":{"approveNo":"47042234","amount":18000},
                         "easyPay":{"provider":"토스페이","amount":0,"discountAmount":0}}
                        """, MediaType.APPLICATION_JSON));

        TossConfirmResponse response = client.confirm(REQUEST);

        assertThat(response.method()).isEqualTo("간편결제");
        assertThat(response.approvalNumber()).isEqualTo("47042234");
    }

    @Test
    void 카드_정보가_없으면_승인번호는_null() {
        // 간편결제를 계좌·포인트로 결제하거나 계좌이체로 결제하면 card 가 없다
        server.expect(requestTo("http://toss/v1/payments/confirm"))
                .andRespond(withSuccess("""
                        {"paymentKey":"tgen_key","orderId":"BDS-1005-1","status":"DONE","method":"간편결제",
                         "approvedAt":"2026-10-26T15:30:00+09:00","card":null,
                         "easyPay":{"provider":"토스페이","amount":18000,"discountAmount":0}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.confirm(REQUEST).approvalNumber()).isNull();
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
