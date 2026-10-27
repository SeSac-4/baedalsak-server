package com.baedalsak.payment.client.order;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * order [내부] API 클라이언트. 응답은 order OrderInternalResponse 와 같은 모양
 */
class OrderClientTest {

    MockRestServiceServer server;
    OrderClient orderClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://order");
        server = MockRestServiceServer.bindTo(builder).build();
        orderClient = new OrderClient(builder.build());
    }

    @Test
    void 주문_정보를_받는다() {
        server.expect(requestTo("http://order/internal/orders/1005")).andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"totalAmount":18000,"customerId":1,"status":"PENDING_PAYMENT",
                         "ownerId":10,"storeId":3,"storeName":"싹분식",
                         "customerEmail":"customer@test.com","ownerEmail":"owner@test.com",
                         "orderName":"참치김밥 외 1건"}
                        """, MediaType.APPLICATION_JSON));

        OrderInternal order = orderClient.getOrder(1005L).orElseThrow();

        assertThat(order.totalAmount()).isEqualTo(18000);
        assertThat(order.customerId()).isEqualTo(1L);
        assertThat(order.isPendingPayment()).isTrue();
        assertThat(order.ownerId()).isEqualTo(10L);
        assertThat(order.storeId()).isEqualTo(3L);
        assertThat(order.storeName()).isEqualTo("싹분식");
        assertThat(order.customerEmail()).isEqualTo("customer@test.com");
        assertThat(order.ownerEmail()).isEqualTo("owner@test.com");
        assertThat(order.orderName()).isEqualTo("참치김밥 외 1건");
    }

    @Test
    void 주문이_없으면_빈_값() {
        server.expect(requestTo("http://order/internal/orders/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"ORDER_NOT_FOUND\"}"));

        assertThat(orderClient.getOrder(999L)).isEmpty();
    }

    @Test
    void order_가_실패하면_503() {
        server.expect(requestTo("http://order/internal/orders/1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> orderClient.getOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void order_에_연결할_수_없으면_503() {
        server.expect(requestTo("http://order/internal/orders/1"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> orderClient.getOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    }
}
