package com.baedalsak.store.client;

import com.baedalsak.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * order [내부] API 클라이언트 (#176). 응답은 order OrderInternalController 의 active-count 와 같은 모양
 */
class OrderClientTest {

    static final String URL = "http://order/internal/orders/active-count?storeId=1";

    MockRestServiceServer server;
    OrderClient orderClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://order");
        server = MockRestServiceServer.bindTo(builder).build();
        orderClient = new OrderClient(builder.build());
    }

    @Test
    void 진행_중_주문_수를_조회한다() {
        server.expect(requestTo(URL)).andExpect(method(GET))
                .andRespond(withSuccess("{\"count\":3}", MediaType.APPLICATION_JSON));

        assertThat(orderClient.countActiveOrders(1L)).isEqualTo(3);
    }

    @Test
    void 응답하지_않으면_503() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new ResourceAccessException("timeout", new IOException("timeout"));
        });

        assertDependencyUnavailable();
    }

    @Test
    void 오류_응답이면_503() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertDependencyUnavailable();
    }

    private void assertDependencyUnavailable() {
        assertThatThrownBy(() -> orderClient.countActiveOrders(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().name())
                .isEqualTo("DEPENDENCY_UNAVAILABLE");
    }
}
