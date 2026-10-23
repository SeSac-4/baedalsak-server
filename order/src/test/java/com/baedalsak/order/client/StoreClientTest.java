package com.baedalsak.order.client;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * store [내부] API 클라이언트. 응답은 store #26 구현(StoreInternalResponse·MenuInternalResponse)과 같은 모양
 */
class StoreClientTest {

    MockRestServiceServer server;
    StoreClient storeClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://store");
        server = MockRestServiceServer.bindTo(builder).build();
        storeClient = new StoreClient(builder.build());
    }

    @Test
    void 가게_정보를_받는다() {
        server.expect(requestTo("http://store/internal/stores/1")).andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"name":"싹분식","address":"서울시 마포구 1","ownerId":10,
                         "deliveryFee":3000,"minOrderAmount":12000,"isOpenNow":true}
                        """, MediaType.APPLICATION_JSON));

        StoreInternal store = storeClient.getStore(1L).orElseThrow();

        assertThat(store.name()).isEqualTo("싹분식");
        assertThat(store.ownerId()).isEqualTo(10L);
        assertThat(store.deliveryFee()).isEqualTo(3000);
        assertThat(store.minOrderAmount()).isEqualTo(12000);
        assertThat(store.isOpenNow()).isTrue();
    }

    @Test
    void 가게가_없으면_빈_값() {
        server.expect(requestTo("http://store/internal/stores/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"STORE_NOT_FOUND\"}"));

        assertThat(storeClient.getStore(999L)).isEmpty();
    }

    @Test
    void store_가_실패하면_503() {
        server.expect(requestTo("http://store/internal/stores/1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> storeClient.getStore(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void store_에_연결할_수_없으면_503() {
        server.expect(requestTo("http://store/internal/stores/1"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> storeClient.getStore(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void 메뉴를_ids_로_한_번에_조회한다() {
        server.expect(requestTo("http://store/internal/stores/1/menus?ids=1&ids=3"))
                .andRespond(withSuccess("""
                        [{"id":1,"name":"참치김밥","price":4500,"saleStatus":"ON_SALE"},
                         {"id":3,"name":"모둠튀김","price":4000,"saleStatus":"SOLD_OUT"}]
                        """, MediaType.APPLICATION_JSON));

        List<MenuInternal> menus = storeClient.getMenus(1L, List.of(1L, 3L)).orElseThrow();

        assertThat(menus).extracting(MenuInternal::id).containsExactly(1L, 3L);
        assertThat(menus.get(0).name()).isEqualTo("참치김밥");
        assertThat(menus.get(0).isOnSale()).isTrue();
        assertThat(menus.get(1).isOnSale()).isFalse();
    }

    @Test
    void 메뉴_조회에서_가게가_없으면_빈_값() {
        server.expect(requestTo("http://store/internal/stores/999/menus?ids=1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(storeClient.getMenus(999L, List.of(1L))).isEmpty();
    }
}
