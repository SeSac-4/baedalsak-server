package com.baedalsak.order.controller;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.order.client.MenuInternal;
import com.baedalsak.order.client.StoreClient;
import com.baedalsak.order.client.StoreInternal;
import com.baedalsak.order.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 장바구니 API (#28). store 내부 API 는 응답을 고정하고, DB 는 실제 PostgreSQL.
 * 가게 1(싹분식, 배달비 3000, 최소 12000) 메뉴: 1 참치김밥 4500 / 2 라볶이 6000 / 3 모둠튀김 4000(품절)
 * 가게 2(싹치킨, 배달비 0, 최소 15000) 메뉴: 5 후라이드 18000
 */
class CartControllerTest extends IntegrationTest {

    static final long CUSTOMER_ID = 7L;

    @MockitoBean
    StoreClient storeClient;

    @BeforeEach
    void setUpStores() {
        given(storeClient.getStore(1L)).willReturn(Optional.of(
                new StoreInternal("싹분식", "서울시 마포구 1", 10L, 3000, 12000, true)));
        given(storeClient.getStore(2L)).willReturn(Optional.of(
                new StoreInternal("싹치킨", "서울시 용산구 2", 20L, 0, 15000, true)));
        given(storeClient.getStore(999L)).willReturn(Optional.empty());

        given(storeClient.getMenus(eq(1L), anyCollection())).willAnswer(call -> {
            List<Long> ids = List.copyOf(call.<java.util.Collection<Long>>getArgument(1));
            return Optional.of(List.of(
                            menu(1L, "참치김밥", 4500, MenuInternal.SaleStatus.ON_SALE),
                            menu(2L, "라볶이", 6000, MenuInternal.SaleStatus.ON_SALE),
                            menu(3L, "모둠튀김", 4000, MenuInternal.SaleStatus.SOLD_OUT)).stream()
                    .filter(m -> ids.contains(m.id())).toList());
        });
        given(storeClient.getMenus(eq(2L), anyCollection())).willReturn(Optional.of(List.of(
                menu(5L, "후라이드", 18000, MenuInternal.SaleStatus.ON_SALE))));
    }

    @Nested
    class 조회 {

        @Test
        void 비어_있으면_CART_EMPTY() throws Exception {
            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.storeId").value(nullValue()))
                    .andExpect(jsonPath("$.storeName").value(nullValue()))
                    .andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.itemsAmount").value(0))
                    .andExpect(jsonPath("$.totalAmount").value(0))
                    .andExpect(jsonPath("$.canOrder").value(false))
                    .andExpect(jsonPath("$.cannotOrderReason").value("CART_EMPTY"));
        }

        @Test
        void 담은_뒤에는_store_를_부르지_않고_스냅샷으로_응답한다() throws Exception {
            add(1L, 1L, 2).andExpect(status().isOk());
            add(1L, 2L, 1).andExpect(status().isOk());
            // store 가 응답하지 않아도 조회는 된다
            given(storeClient.getStore(1L)).willThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE));

            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.storeId").value(1))
                    .andExpect(jsonPath("$.storeName").value("싹분식"))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].menuName").value("참치김밥"))
                    .andExpect(jsonPath("$.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.itemsAmount").value(15000))
                    .andExpect(jsonPath("$.deliveryFee").value(3000))
                    .andExpect(jsonPath("$.totalAmount").value(18000))
                    .andExpect(jsonPath("$.minOrderAmount").value(12000))
                    .andExpect(jsonPath("$.canOrder").value(true))
                    .andExpect(jsonPath("$.cannotOrderReason").value(nullValue()));
        }

        @Test
        void 최소주문금액에_못_미치면_MIN_ORDER_AMOUNT_NOT_MET() throws Exception {
            add(1L, 1L, 1);

            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(jsonPath("$.itemsAmount").value(4500))
                    .andExpect(jsonPath("$.canOrder").value(false))
                    .andExpect(jsonPath("$.cannotOrderReason").value("MIN_ORDER_AMOUNT_NOT_MET"));
        }

        @Test
        void 요청자_헤더가_없으면_401() throws Exception {
            mockMvc.perform(get("/v1/cart"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    class 담기 {

        @Test
        void 같은_메뉴를_다시_담으면_수량을_더한다() throws Exception {
            add(1L, 1L, 2);

            add(1L, 1L, 3)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].quantity").value(5))
                    .andExpect(jsonPath("$.itemsAmount").value(22500));
        }

        @Test
        void 다른_가게_메뉴면_409_CART_DIFFERENT_STORE() throws Exception {
            add(1L, 1L, 2);

            add(2L, 5L, 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CART_DIFFERENT_STORE"));
            // 기존 장바구니는 그대로
            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(jsonPath("$.storeId").value(1));
        }

        @Test
        void replace_true_면_비우고_새_가게_메뉴를_담는다() throws Exception {
            add(1L, 1L, 2);

            mockMvc.perform(customer(post("/v1/cart/items").param("replace", "true"))
                            .content(body(2L, 5L, 1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.storeId").value(2))
                    .andExpect(jsonPath("$.storeName").value("싹치킨"))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].menuName").value("후라이드"))
                    .andExpect(jsonPath("$.deliveryFee").value(0))
                    .andExpect(jsonPath("$.canOrder").value(true));
        }

        @Test
        void 영업_중이_아니면_400_STORE_CLOSED() throws Exception {
            given(storeClient.getStore(1L)).willReturn(Optional.of(
                    new StoreInternal("싹분식", "서울시 마포구 1", 10L, 3000, 12000, false)));

            add(1L, 1L, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("STORE_CLOSED"));
        }

        @Test
        void 품절_메뉴면_400_MENU_NOT_ON_SALE() throws Exception {
            add(1L, 3L, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MENU_NOT_ON_SALE"));
        }

        @Test
        void 없거나_삭제된_메뉴면_400_MENU_NOT_ON_SALE() throws Exception {
            add(1L, 999L, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MENU_NOT_ON_SALE"));
        }

        @Test
        void 없는_가게면_404_STORE_NOT_FOUND() throws Exception {
            add(999L, 1L, 1)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }

        @Test
        void store_가_응답하지_않으면_503() throws Exception {
            given(storeClient.getStore(any())).willThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE));

            add(1L, 1L, 1)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        }

        @Test
        void 수량이_1_미만이면_400() throws Exception {
            add(1L, 1L, 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("quantity"));
        }

        @Test
        void 가게_스냅샷은_담을_때마다_최신_값으로_갱신된다() throws Exception {
            add(1L, 1L, 1);
            given(storeClient.getStore(1L)).willReturn(Optional.of(
                    new StoreInternal("싹분식 본점", "서울시 마포구 1", 10L, 2000, 10000, true)));

            add(1L, 2L, 1)
                    .andExpect(jsonPath("$.storeName").value("싹분식 본점"))
                    .andExpect(jsonPath("$.deliveryFee").value(2000))
                    .andExpect(jsonPath("$.minOrderAmount").value(10000))
                    .andExpect(jsonPath("$.canOrder").value(true));
        }
    }

    @Nested
    class 수정_삭제 {

        @Test
        void 수량을_바꾼다() throws Exception {
            add(1L, 1L, 1);

            mockMvc.perform(customer(patch("/v1/cart/items/1")).content("{\"quantity\":3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].quantity").value(3))
                    .andExpect(jsonPath("$.itemsAmount").value(13500))
                    .andExpect(jsonPath("$.canOrder").value(true));
        }

        @Test
        void 장바구니에_없는_메뉴면_404_NOT_FOUND() throws Exception {
            add(1L, 1L, 1);

            mockMvc.perform(customer(patch("/v1/cart/items/2")).content("{\"quantity\":3}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            mockMvc.perform(customer(delete("/v1/cart/items/2")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 항목을_지운다() throws Exception {
            add(1L, 1L, 2);
            add(1L, 2L, 1);

            mockMvc.perform(customer(delete("/v1/cart/items/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].menuName").value("라볶이"));
        }

        @Test
        void 마지막_항목을_지우면_빈_장바구니() throws Exception {
            add(1L, 1L, 1);

            mockMvc.perform(customer(delete("/v1/cart/items/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.storeId").value(nullValue()))
                    .andExpect(jsonPath("$.cannotOrderReason").value("CART_EMPTY"));
            // 빈 장바구니가 됐으니 다른 가게 메뉴도 바로 담을 수 있다
            add(2L, 5L, 1).andExpect(status().isOk());
        }

        @Test
        void 비우면_204_이고_빈_장바구니가_된다() throws Exception {
            add(1L, 1L, 1);

            mockMvc.perform(customer(delete("/v1/cart")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(jsonPath("$.cannotOrderReason").value("CART_EMPTY"));
        }

        @Test
        void 빈_장바구니를_비워도_204() throws Exception {
            mockMvc.perform(customer(delete("/v1/cart")))
                    .andExpect(status().isNoContent());
        }

        @Test
        void 다른_고객의_장바구니에는_영향이_없다() throws Exception {
            add(1L, 1L, 1);

            mockMvc.perform(member(get("/v1/cart"), 8L))
                    .andExpect(jsonPath("$.cannotOrderReason").value("CART_EMPTY"));
        }
    }

    private ResultActions add(long storeId, long menuId, int quantity) throws Exception {
        return mockMvc.perform(customer(post("/v1/cart/items")).content(body(storeId, menuId, quantity)));
    }

    private String body(long storeId, long menuId, int quantity) {
        return "{\"storeId\":" + storeId + ",\"menuId\":" + menuId + ",\"quantity\":" + quantity + "}";
    }

    private static MenuInternal menu(long id, String name, int price, MenuInternal.SaleStatus status) {
        return new MenuInternal(id, name, price, status);
    }

    private MockHttpServletRequestBuilder customer(MockHttpServletRequestBuilder request) {
        return member(request, CUSTOMER_ID);
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder request, long memberId) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header(LoginMember.ID_HEADER, String.valueOf(memberId))
                .header(LoginMember.ROLE_HEADER, "CUSTOMER");
    }
}
