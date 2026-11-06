package com.baedalsak.order.client;

import com.baedalsak.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * payment [내부] API 클라이언트. 응답은 payment #128 구현(PaymentInternalController)과 같은 모양
 */
class PaymentClientTest {

    static final CreatePaymentCommand CREATE = new CreatePaymentCommand(1L, 16500, 7L, 10L, 1L, "싹분식",
            "c@x", "o@x", "참치김밥");
    static final ConfirmPaymentCommand CONFIRM = new ConfirmPaymentCommand(1L, 7L, "key-1", 16500);
    static final String READY = """
            {"paymentId":3,"tossOrderId":"toss-1","orderName":"참치김밥","amount":16500}
            """;

    MockRestServiceServer server;
    PaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        paymentClient = new PaymentClient(restClient, restClient, restClient);
    }

    @Test
    void 결제_요청을_만든다() {
        server.expect(requestTo("http://payment/internal/payments")).andExpect(method(POST))
                .andExpect(content().json("""
                        {"orderId":1,"amount":16500,"customerId":7,"ownerId":10,"storeId":1,"storeName":"싹분식",
                         "customerEmail":"c@x","ownerEmail":"o@x","orderName":"참치김밥"}
                        """))
                .andRespond(withSuccess(READY, MediaType.APPLICATION_JSON));

        assertThat(paymentClient.create(CREATE))
                .isEqualTo(new PaymentReady(3L, "toss-1", "참치김밥", 16500));
    }

    @Test
    void 결제_요청_타임아웃이면_1회_다시_부른다() {
        server.expect(requestTo("http://payment/internal/payments")).andRespond(request -> {
            throw new IOException("read timeout");
        });
        server.expect(requestTo("http://payment/internal/payments"))
                .andRespond(withSuccess(READY, MediaType.APPLICATION_JSON));

        assertThat(paymentClient.create(CREATE).paymentId()).isEqualTo(3L);
        server.verify();
    }

    @Test
    void 결제_요청_재호출도_타임아웃이면_503() {
        server.expect(requestTo("http://payment/internal/payments")).andRespond(request -> {
            throw new IOException("read timeout");
        });
        server.expect(requestTo("http://payment/internal/payments")).andRespond(request -> {
            throw new IOException("read timeout");
        });

        assertThatThrownBy(() -> paymentClient.create(CREATE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode().name()).isEqualTo("DEPENDENCY_UNAVAILABLE"));
        server.verify();
    }

    @Test
    void 결제_요청_에러는_다시_부르지_않고_같은_코드_메시지로_전달한다() {
        server.expect(requestTo("http://payment/internal/payments"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"code":"PAYMENT_IN_PROGRESS","message":"이전 결제를 확인하고 있어요","traceId":"t"}
                        """));

        assertThatThrownBy(() -> paymentClient.create(CREATE))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode().name()).isEqualTo("PAYMENT_IN_PROGRESS");
                    assertThat(e.getErrorCode().getStatus()).isEqualTo(409);
                    assertThat(e.getErrorCode().getMessage()).isEqualTo("이전 결제를 확인하고 있어요");
                });
        server.verify();
    }

    @Test
    void 승인_요청을_보낸다() {
        server.expect(requestTo("http://payment/internal/payments/confirm")).andExpect(method(POST))
                .andExpect(content().json("""
                        {"orderId":1,"customerId":7,"paymentKey":"key-1","amount":16500}
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> paymentClient.confirm(CONFIRM)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void 이미_승인된_결제면_성공으로_본다() {
        server.expect(requestTo("http://payment/internal/payments/confirm"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"code":"PAYMENT_ALREADY_APPROVED","message":"이미 승인된 결제입니다."}
                        """));

        assertThatCode(() -> paymentClient.confirm(CONFIRM)).doesNotThrowAnyException();
    }

    @Test
    void 토스_거절은_토스_메시지_그대로_전달한다() {
        server.expect(requestTo("http://payment/internal/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
                        {"code":"PAYMENT_CONFIRM_REJECTED","message":"잔액이 부족합니다."}
                        """));

        assertThatThrownBy(() -> paymentClient.confirm(CONFIRM))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode().name()).isEqualTo("PAYMENT_CONFIRM_REJECTED");
                    assertThat(e.getErrorCode().getMessage()).isEqualTo("잔액이 부족합니다.");
                });
    }

    @Test
    void 승인_응답이_없으면_결과를_몰라_502() {
        server.expect(requestTo("http://payment/internal/payments/confirm")).andRespond(request -> {
            throw new IOException("read timeout");
        });

        assertThatThrownBy(() -> paymentClient.confirm(CONFIRM))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode().name()).isEqualTo("PAYMENT_GATEWAY_ERROR");
                    assertThat(e.getErrorCode().getStatus()).isEqualTo(502);
                });
    }

    @Test
    void 승인_5xx_가_공통_에러_형식이_아니면_502() {
        server.expect(requestTo("http://payment/internal/payments/confirm"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.TEXT_HTML)
                        .body("<html>unavailable</html>"));

        assertThatThrownBy(() -> paymentClient.confirm(CONFIRM))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode().name()).isEqualTo("PAYMENT_GATEWAY_ERROR"));
    }

    @Test
    void 만료_결과를_받는다() {
        server.expect(requestTo("http://payment/internal/payments/expire")).andExpect(method(POST))
                .andExpect(content().json("""
                        {"orderIds":[1]}
                        """))
                .andRespond(withSuccess("""
                        [{"orderId":1,"result":"APPROVED","approvedAt":"2026-11-06T12:00:00+09:00"}]
                        """, MediaType.APPLICATION_JSON));

        PaymentExpireResult result = paymentClient.expire(1L);

        assertThat(result.result()).isEqualTo(PaymentExpireResult.Result.APPROVED);
        assertThat(result.approvedAt()).isAtSameInstantAs(OffsetDateTime.parse("2026-11-06T12:00:00+09:00"));
    }

    @Test
    void 만료_호출이_실패하면_503() {
        server.expect(requestTo("http://payment/internal/payments/expire")).andRespond(request -> {
            throw new ResourceAccessException("read timeout");
        });

        assertThatThrownBy(() -> paymentClient.expire(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode().name()).isEqualTo("DEPENDENCY_UNAVAILABLE"));
    }
}
