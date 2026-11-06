package com.baedalsak.order.controller;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorCode;
import com.baedalsak.order.client.ConfirmPaymentCommand;
import com.baedalsak.order.client.CreatePaymentCommand;
import com.baedalsak.order.client.MemberClient;
import com.baedalsak.order.client.MemberInternal;
import com.baedalsak.order.client.MenuInternal;
import com.baedalsak.order.client.PaymentClient;
import com.baedalsak.order.client.PaymentExpireResult;
import com.baedalsak.order.client.PaymentReady;
import com.baedalsak.order.client.StoreClient;
import com.baedalsak.order.client.StoreInternal;
import com.baedalsak.order.error.OrderErrorCode;
import com.baedalsak.order.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 생성·조회 API (#29), 가게 주문 목록·주문 수락 (#30). store·member 내부 API 는 응답을 고정하고, DB 는 실제 PostgreSQL.
 * 가게 1(싹분식, 사장님 10, 배달비 3000, 최소 12000) 메뉴: 1 참치김밥 4500 / 2 라볶이 6000
 * 고객 7(010-1234-5678, customer@…), 사장님 10(owner@…)
 */
class OrderControllerTest extends IntegrationTest {

    static final long CUSTOMER_ID = 7L;
    static final long OWNER_ID = 10L;

    static final String ORDER_BODY = """
            {"deliveryAddress":"서울시 마포구 2","deliveryDong":"연남동","storeRequest":"단무지 빼 주세요","riderRequest":"문 앞에 두세요"}
            """;

    @MockitoBean
    StoreClient storeClient;

    @MockitoBean
    MemberClient memberClient;

    @MockitoBean
    PaymentClient paymentClient;

    // 장바구니 담기·주문 생성 때 store 가 돌려줄 메뉴 (테스트마다 바꿀 수 있음)
    List<MenuInternal> storeMenus;

    @BeforeEach
    void setUp() {
        storeMenus = List.of(
                new MenuInternal(1L, "참치김밥", 4500, MenuInternal.SaleStatus.ON_SALE),
                new MenuInternal(2L, "라볶이", 6000, MenuInternal.SaleStatus.ON_SALE));
        openStore(3000, 12000);
        given(paymentClient.create(any())).willAnswer(call -> {
            CreatePaymentCommand command = call.getArgument(0);
            return new PaymentReady(100L + command.orderId(), "toss-" + command.orderId(), command.orderName(),
                    command.amount());
        });
        given(storeClient.getMenus(eq(1L), anyCollection())).willAnswer(call -> {
            Collection<Long> ids = call.getArgument(1);
            return Optional.of(storeMenus.stream().filter(m -> ids.contains(m.id())).toList());
        });
        given(memberClient.getMember(CUSTOMER_ID)).willReturn(Optional.of(
                new MemberInternal("김고객", "010-1234-5678", "customer@baedalsak.click", "CUSTOMER")));
        given(memberClient.getMember(OWNER_ID)).willReturn(Optional.of(
                new MemberInternal("박사장", "010-9999-0000", "owner@baedalsak.click", "OWNER")));
    }

    @Nested
    class 주문_생성 {

        @Test
        void 장바구니로_결제대기_주문과_결제_요청을_만들고_장바구니는_그대로() throws Exception {
            addToCart(1L, 2);
            addToCart(2L, 1);

            createOrder()
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.storeId").value(1))
                    .andExpect(jsonPath("$.storeName").value("싹분식"))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].menuName").value("참치김밥"))
                    .andExpect(jsonPath("$.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.itemsAmount").value(15000))
                    .andExpect(jsonPath("$.deliveryFee").value(3000))
                    .andExpect(jsonPath("$.totalAmount").value(18000))
                    .andExpect(jsonPath("$.deliveryAddress").value("서울시 마포구 2"))
                    .andExpect(jsonPath("$.storeRequest").value("단무지 빼 주세요"))
                    .andExpect(jsonPath("$.riderRequest").value("문 앞에 두세요"))
                    .andExpect(jsonPath("$.estimatedReadyAt").value(nullValue()))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.payment.paymentId").value(101))
                    .andExpect(jsonPath("$.payment.tossOrderId").value("toss-1"))
                    .andExpect(jsonPath("$.payment.orderName").value("참치김밥 외 1건"))
                    .andExpect(jsonPath("$.payment.amount").value(18000))
                    // 고객 연락처는 내려주지 않는다 (O-03)
                    .andExpect(jsonPath("$.customerPhone").doesNotExist());

            // 장바구니는 결제 완료 때 비운다
            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(jsonPath("$.items.length()").value(2));
            then(paymentClient).should().create(new CreatePaymentCommand(1L, 18000, CUSTOMER_ID, OWNER_ID, 1L, "싹분식",
                    "customer@baedalsak.click", "owner@baedalsak.click", "참치김밥 외 1건"));
        }

        @Test
        void 고객_연락처_고객과_사장님_이메일을_스냅샷한다() throws Exception {
            addToCart(1L, 3);

            createOrder().andExpect(status().isCreated());

            var row = jdbcTemplate.queryForMap(
                    "SELECT owner_id, customer_phone, customer_email, owner_email, delivery_dong, pickup_address "
                            + "FROM orders.orders WHERE id = 1");
            assertThat(row.get("owner_id")).isEqualTo(OWNER_ID);
            assertThat(row.get("customer_phone")).isEqualTo("010-1234-5678");
            assertThat(row.get("customer_email")).isEqualTo("customer@baedalsak.click");
            assertThat(row.get("owner_email")).isEqualTo("owner@baedalsak.click");
            // 배달 생성(order.accepted)에 쓸 동·픽업 주소. 픽업 주소는 주문 시점 가게 주소
            assertThat(row.get("delivery_dong")).isEqualTo("연남동");
            assertThat(row.get("pickup_address")).isEqualTo("서울시 마포구 1");
        }

        @Test
        void 금액은_장바구니가_아니라_지금_store_가격으로_계산한다() throws Exception {
            addToCart(1L, 3);
            // 담은 뒤 가격·배달비가 바뀜
            storeMenus = List.of(new MenuInternal(1L, "참치마요김밥", 5000, MenuInternal.SaleStatus.ON_SALE));
            openStore(2000, 12000);

            createOrder()
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.items[0].menuName").value("참치마요김밥"))
                    .andExpect(jsonPath("$.items[0].price").value(5000))
                    .andExpect(jsonPath("$.itemsAmount").value(15000))
                    .andExpect(jsonPath("$.deliveryFee").value(2000))
                    .andExpect(jsonPath("$.totalAmount").value(17000));
        }

        @Test
        void 장바구니가_비어_있으면_400_CART_EMPTY() throws Exception {
            createOrder()
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CART_EMPTY"));
        }

        @Test
        void 영업이_끝났으면_400_STORE_CLOSED() throws Exception {
            addToCart(1L, 3);
            given(storeClient.getStore(1L)).willReturn(Optional.of(
                    new StoreInternal("싹분식", "서울시 마포구 1", OWNER_ID, 3000, 12000, false)));

            createOrder()
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("STORE_CLOSED"));
        }

        @Test
        void 담은_뒤_품절되거나_삭제된_메뉴가_있으면_400_MENU_NOT_ON_SALE() throws Exception {
            addToCart(1L, 2);
            addToCart(2L, 1);
            // 1번 품절, 2번 삭제(응답에서 빠짐)
            storeMenus = List.of(new MenuInternal(1L, "참치김밥", 4500, MenuInternal.SaleStatus.SOLD_OUT));

            createOrder()
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MENU_NOT_ON_SALE"));
        }

        @Test
        void 최소주문금액을_못_넘으면_400_MIN_ORDER_AMOUNT_NOT_MET() throws Exception {
            addToCart(1L, 3);
            openStore(3000, 20000);

            createOrder()
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MIN_ORDER_AMOUNT_NOT_MET"));
        }

        @Test
        void member_가_응답하지_않으면_503_이고_장바구니는_그대로() throws Exception {
            addToCart(1L, 3);
            given(memberClient.getMember(CUSTOMER_ID))
                    .willThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE));

            createOrder()
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(jsonPath("$.items.length()").value(1));
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders.orders", Integer.class)).isZero();
        }

        @Test
        void 결제_요청이_거절되면_같은_코드로_응답하고_주문은_결제만료_장바구니는_그대로() throws Exception {
            addToCart(1L, 3);
            willThrow(new BusinessException(TestErrorCode.PAYMENT_IN_PROGRESS)).given(paymentClient).create(any());
            given(paymentClient.expire(1L)).willReturn(new PaymentExpireResult(1L, PaymentExpireResult.Result.NONE, null));

            createOrder()
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));

            assertThat(orderStatus(1)).isEqualTo("PAYMENT_EXPIRED");
            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(jsonPath("$.items.length()").value(1));
        }

        @Test
        void 결제_요청_실패_뒤_만료_호출도_실패하면_결제대기로_둔다() throws Exception {
            addToCart(1L, 3);
            willThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE)).given(paymentClient).create(any());
            given(paymentClient.expire(1L))
                    .willThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE));

            createOrder().andExpect(status().isServiceUnavailable());

            assertThat(orderStatus(1)).isEqualTo("PENDING_PAYMENT");
        }

        @Test
        void 배달_주소가_없으면_400() throws Exception {
            addToCart(1L, 3);

            mockMvc.perform(customer(post("/v1/orders")).content("{\"deliveryAddress\":\" \",\"deliveryDong\":\"연남동\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("deliveryAddress"));
        }

        @Test
        void 배달_동이_없으면_400() throws Exception {
            addToCart(1L, 3);

            mockMvc.perform(customer(post("/v1/orders")).content("{\"deliveryAddress\":\"서울시 마포구 2\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("deliveryDong"));
        }
    }

    @Nested
    class 결제_확인 {

        static final String CONFIRM_BODY = "{\"paymentKey\":\"key-1\",\"amount\":16500}";

        @Test
        void 승인되면_결제완료이고_주문_항목만_장바구니에서_지운다() throws Exception {
            placeOrder(CUSTOMER_ID);
            addToCart(2L, 1);   // 결제창에 있는 동안 더 담은 메뉴

            confirm(1)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.items[0].menuName").value("참치김밥"));

            then(paymentClient).should().confirm(new ConfirmPaymentCommand(1L, CUSTOMER_ID, "key-1", 16500));
            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].menuName").value("라볶이"));
        }

        @Test
        void 주문_항목이_장바구니의_전부면_장바구니를_지운다() throws Exception {
            placeOrder(CUSTOMER_ID);

            confirm(1).andExpect(status().isOk());

            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders.cart", Integer.class)).isZero();
        }

        @Test
        void 가게가_마감했으면_승인을_부르지_않고_결제만료_400_STORE_CLOSED() throws Exception {
            placeOrder(CUSTOMER_ID);
            given(storeClient.getStore(1L)).willReturn(Optional.of(
                    new StoreInternal("싹분식", "서울시 마포구 1", OWNER_ID, 3000, 12000, false)));
            given(paymentClient.expire(1L)).willReturn(new PaymentExpireResult(1L, PaymentExpireResult.Result.EXPIRED, null));

            confirm(1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("STORE_CLOSED"));

            then(paymentClient).should(never()).confirm(any());
            assertThat(orderStatus(1)).isEqualTo("PAYMENT_EXPIRED");
            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(jsonPath("$.items.length()").value(1));
        }

        @Test
        void 품절이면_승인을_부르지_않고_결제만료_400_MENU_NOT_ON_SALE() throws Exception {
            placeOrder(CUSTOMER_ID);
            storeMenus = List.of(new MenuInternal(1L, "참치김밥", 4500, MenuInternal.SaleStatus.SOLD_OUT));
            given(paymentClient.expire(1L)).willReturn(new PaymentExpireResult(1L, PaymentExpireResult.Result.EXPIRED, null));

            confirm(1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MENU_NOT_ON_SALE"));

            then(paymentClient).should(never()).confirm(any());
            assertThat(orderStatus(1)).isEqualTo("PAYMENT_EXPIRED");
        }

        @Test
        void 마감이어도_앞선_시도의_승인이_10분_이내면_결제완료() throws Exception {
            placeOrder(CUSTOMER_ID);
            given(storeClient.getStore(1L)).willReturn(Optional.of(
                    new StoreInternal("싹분식", "서울시 마포구 1", OWNER_ID, 3000, 12000, false)));
            given(paymentClient.expire(1L)).willReturn(new PaymentExpireResult(1L, PaymentExpireResult.Result.APPROVED,
                    OffsetDateTime.now().minusMinutes(1)));

            confirm(1)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"));
        }

        @Test
        void 마감이고_앞선_시도의_승인이_10분을_넘었으면_자동_환불() throws Exception {
            placeOrder(CUSTOMER_ID);
            given(storeClient.getStore(1L)).willReturn(Optional.of(
                    new StoreInternal("싹분식", "서울시 마포구 1", OWNER_ID, 3000, 12000, false)));
            given(paymentClient.expire(1L)).willReturn(new PaymentExpireResult(1L, PaymentExpireResult.Result.APPROVED,
                    OffsetDateTime.now().minusMinutes(11)));

            confirm(1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("STORE_CLOSED"));

            assertThat(orderStatus(1)).isEqualTo("PAYMENT_EXPIRED");
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders.outbox WHERE event_type = 'CancelPayment'",
                    Integer.class)).isEqualTo(1);
        }

        @Test
        void store_가_응답하지_않으면_503_이고_주문은_그대로() throws Exception {
            placeOrder(CUSTOMER_ID);
            given(storeClient.getStore(1L))
                    .willThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE));

            confirm(1).andExpect(status().isServiceUnavailable());

            then(paymentClient).should(never()).confirm(any());
            assertThat(orderStatus(1)).isEqualTo("PENDING_PAYMENT");
        }

        @Test
        void 결과를_모르면_502_이고_결제대기로_남아_다시_시도할_수_있다() throws Exception {
            placeOrder(CUSTOMER_ID);
            willThrow(new BusinessException(OrderErrorCode.PAYMENT_GATEWAY_ERROR))
                    .willDoNothing()
                    .given(paymentClient).confirm(any());

            confirm(1)
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));
            assertThat(orderStatus(1)).isEqualTo("PENDING_PAYMENT");

            confirm(1)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"));
        }

        @Test
        void 이미_결제완료면_payment_를_부르지_않고_성공으로_응답한다() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            confirm(1)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"));

            then(paymentClient).should(never()).confirm(any());
        }

        @Test
        void 결제만료_주문이면_payment_를_부르지_않고_409_ORDER_INVALID_STATUS() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAYMENT_EXPIRED");

            confirm(1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATUS"));

            then(paymentClient).should(never()).confirm(any());
        }

        @Test
        void 없는_주문이거나_남의_주문이면_404_ORDER_NOT_FOUND() throws Exception {
            placeOrder(CUSTOMER_ID);

            confirm(99)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
            mockMvc.perform(member(post("/v1/orders/1/payment/confirm"), 8L, "CUSTOMER").content(CONFIRM_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

            then(paymentClient).should(never()).confirm(any());
        }

        @Test
        void 결제_키나_금액이_없으면_400() throws Exception {
            mockMvc.perform(customer(post("/v1/orders/1/payment/confirm")).content("{\"amount\":16500}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            mockMvc.perform(customer(post("/v1/orders/1/payment/confirm")).content("{\"paymentKey\":\"key-1\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        private ResultActions confirm(long orderId) throws Exception {
            return mockMvc.perform(customer(post("/v1/orders/" + orderId + "/payment/confirm")).content(CONFIRM_BODY));
        }
    }

    @Nested
    class 가게_주문_목록 {

        @Test
        void 결제대기를_뺀_내_가게_주문을_최신순으로_조회한다() throws Exception {
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");
            setStatus(2, "COOKING");

            owner(get("/v1/stores/1/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(2))
                    .andExpect(jsonPath("$.content[0].status").value("COOKING"))
                    .andExpect(jsonPath("$.content[0].items[0].menuName").value("참치김밥"))
                    .andExpect(jsonPath("$.content[0].storeRequest").value("단무지 빼 주세요"))
                    .andExpect(jsonPath("$.content[1].id").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void 상태를_여러_개_골라_조회한다() throws Exception {
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");
            setStatus(2, "COOKING");
            setStatus(3, "DELIVERING");

            // 진행 중 탭: 조리중 + 배달중
            owner(get("/v1/stores/1/orders").param("status", "COOKING", "DELIVERING"))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(3))
                    .andExpect(jsonPath("$.content[1].id").value(2));
        }

        @Test
        void 결제대기를_골라도_보이지_않는다() throws Exception {
            placeOrder(CUSTOMER_ID);

            owner(get("/v1/stores/1/orders").param("status", "PENDING_PAYMENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void 결제만료는_보이지_않고_거절과_취소는_보인다() throws Exception {
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAYMENT_EXPIRED");
            setStatus(2, "REJECTED");
            setStatus(3, "CANCELED");

            owner(get("/v1/stores/1/orders"))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].status").value("CANCELED"))
                    .andExpect(jsonPath("$.content[1].status").value("REJECTED"));
            owner(get("/v1/stores/1/orders").param("status", "PAYMENT_EXPIRED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        void 주문이_없으면_빈_페이지() throws Exception {
            owner(get("/v1/stores/1/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));
        }

        @Test
        void 다른_사장님의_가게이거나_사장님이_아니면_403() throws Exception {
            placeOrder(CUSTOMER_ID);

            mockMvc.perform(member(get("/v1/stores/1/orders"), 20L, "OWNER"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(customer(get("/v1/stores/1/orders")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 없는_상태값이면_400() throws Exception {
            owner(get("/v1/stores/1/orders").param("status", "DONE"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class 주문_수락 {

        @Test
        void 결제완료_주문을_조리중으로_바꾸고_조리_완료_예상_시각을_정한다() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            accept(1, "{\"estimatedMinutes\":20}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("COOKING"))
                    .andExpect(jsonPath("$.estimatedReadyAt").exists())
                    .andExpect(jsonPath("$.items.length()").value(1));

            var row = jdbcTemplate.queryForMap("SELECT status, "
                    + "EXTRACT(EPOCH FROM estimated_ready_at - now())::int AS seconds FROM orders.orders WHERE id = 1");
            assertThat(row.get("status")).isEqualTo("COOKING");
            assertThat((Integer) row.get("seconds")).isBetween(19 * 60, 20 * 60);
        }

        @Test
        void 같은_트랜잭션에_OrderAccepted_outbox_를_남긴다() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            accept(1, "{\"estimatedMinutes\":15}").andExpect(status().isOk());

            var row = jdbcTemplate.queryForMap("SELECT event_id, event_type, routing_key, aggregate_id, "
                    + "payload::text AS payload, published_at FROM orders.outbox");
            assertThat(row.get("event_id")).isNotNull();
            assertThat(row.get("event_type")).isEqualTo("OrderAccepted");
            assertThat(row.get("routing_key")).isEqualTo("order.accepted");
            assertThat(row.get("aggregate_id")).isEqualTo(1L);
            assertThat(row.get("published_at")).isNull();

            var payload = jdbcTemplate.queryForMap("""
                    SELECT payload->>'orderId' AS order_id, payload->>'customerId' AS customer_id,
                           payload->>'customerPhone' AS customer_phone, payload->>'ownerId' AS owner_id,
                           payload->>'storeId' AS store_id, payload->>'storeName' AS store_name,
                           payload->>'pickupAddress' AS pickup_address, payload->>'deliveryAddress' AS delivery_address,
                           payload->>'deliveryDong' AS delivery_dong, payload->>'deliveryFee' AS delivery_fee,
                           payload->>'riderRequest' AS rider_request, payload->>'estimatedReadyAt' AS estimated_ready_at,
                           payload->>'customerEmail' AS customer_email, payload->>'ownerEmail' AS owner_email
                      FROM orders.outbox
                    """);
            assertThat(payload.get("order_id")).isEqualTo("1");
            assertThat(payload.get("customer_id")).isEqualTo("7");
            assertThat(payload.get("customer_phone")).isEqualTo("010-1234-5678");
            assertThat(payload.get("owner_id")).isEqualTo("10");
            assertThat(payload.get("store_id")).isEqualTo("1");
            assertThat(payload.get("store_name")).isEqualTo("싹분식");
            assertThat(payload.get("pickup_address")).isEqualTo("서울시 마포구 1");
            assertThat(payload.get("delivery_address")).isEqualTo("서울시 마포구 2");
            assertThat(payload.get("delivery_dong")).isEqualTo("연남동");
            assertThat(payload.get("delivery_fee")).isEqualTo("3000");
            assertThat(payload.get("rider_request")).isEqualTo("문 앞에 두세요");
            assertThat((String) payload.get("estimated_ready_at")).endsWith("+09:00");
            assertThat(payload.get("customer_email")).isEqualTo("customer@baedalsak.click");
            assertThat(payload.get("owner_email")).isEqualTo("owner@baedalsak.click");
        }

        @Test
        void 이미_수락했거나_결제_전이면_409_ORDER_INVALID_STATUS() throws Exception {
            placeOrder(CUSTOMER_ID);

            accept(1, "{\"estimatedMinutes\":20}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATUS"));

            setStatus(1, "PAID");
            accept(1, "{\"estimatedMinutes\":20}").andExpect(status().isOk());
            accept(1, "{\"estimatedMinutes\":20}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATUS"));
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders.outbox", Integer.class)).isOne();
        }

        @Test
        void 다른_사장님_주문이거나_사장님이_아니면_403() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            mockMvc.perform(member(post("/v1/orders/1/accept"), 20L, "OWNER").content("{\"estimatedMinutes\":20}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(customer(post("/v1/orders/1/accept")).content("{\"estimatedMinutes\":20}"))
                    .andExpect(status().isForbidden());
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = 1", String.class))
                    .isEqualTo("PAID");
        }

        @Test
        void 없는_주문이면_404_ORDER_NOT_FOUND() throws Exception {
            accept(999, "{\"estimatedMinutes\":20}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }

        @Test
        void 조리_예상_시간이_없거나_0_이하면_400() throws Exception {
            accept(1, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("estimatedMinutes"));
            accept(1, "{\"estimatedMinutes\":0}")
                    .andExpect(status().isBadRequest());
        }

        private ResultActions accept(long orderId, String body) throws Exception {
            return mockMvc.perform(member(post("/v1/orders/" + orderId + "/accept"), OWNER_ID, "OWNER").content(body));
        }
    }

    @Nested
    class 주문_거절 {

        @Test
        void 결제완료_주문을_거절하고_같은_트랜잭션에_CancelPayment_를_남긴다() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            owner(post("/v1/orders/1/reject"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("REJECTED"));

            assertThat(orderStatus(1)).isEqualTo("REJECTED");
            assertCancelCommand("사장님 주문 거절", "OWNER");
        }

        @Test
        void 거절한_주문은_수락할_수_없다() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");
            owner(post("/v1/orders/1/reject")).andExpect(status().isOk());

            mockMvc.perform(member(post("/v1/orders/1/accept"), OWNER_ID, "OWNER").content("{\"estimatedMinutes\":20}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATUS"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"PENDING_PAYMENT", "PAYMENT_EXPIRED", "COOKING", "REJECTED", "CANCELED"})
        void 결제완료가_아니면_409_이고_환불_명령을_남기지_않는다(String current) throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, current);

            owner(post("/v1/orders/1/reject"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATUS"));

            assertThat(orderStatus(1)).isEqualTo(current);
            assertNoCancelCommand();
        }

        @Test
        void 사장님이_아니면_403() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            mockMvc.perform(customer(post("/v1/orders/1/reject")))
                    .andExpect(status().isForbidden());

            assertThat(orderStatus(1)).isEqualTo("PAID");
        }

        @Test
        void 다른_가게_사장님이면_403() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            mockMvc.perform(member(post("/v1/orders/1/reject"), 999L, "OWNER"))
                    .andExpect(status().isForbidden());

            assertThat(orderStatus(1)).isEqualTo("PAID");
            assertNoCancelCommand();
        }

        @Test
        void 없는_주문이면_404_ORDER_NOT_FOUND() throws Exception {
            owner(post("/v1/orders/999/reject"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }
    }

    @Nested
    class 주문_취소 {

        @Test
        void 수락_전_결제완료_주문을_취소하고_같은_트랜잭션에_CancelPayment_를_남긴다() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            mockMvc.perform(customer(post("/v1/orders/1/cancel")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELED"));

            assertThat(orderStatus(1)).isEqualTo("CANCELED");
            assertCancelCommand("고객 주문 취소", "CUSTOMER");
        }

        @ParameterizedTest
        @ValueSource(strings = {"PENDING_PAYMENT", "PAYMENT_EXPIRED", "COOKING", "DELIVERING", "DELIVERED", "CANCELED"})
        void 결제완료가_아니면_409_이고_환불_명령을_남기지_않는다(String current) throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, current);

            mockMvc.perform(customer(post("/v1/orders/1/cancel")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATUS"));

            assertThat(orderStatus(1)).isEqualTo(current);
            assertNoCancelCommand();
        }

        @Test
        void 남의_주문이면_404() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            mockMvc.perform(member(post("/v1/orders/1/cancel"), 999L, "CUSTOMER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

            assertThat(orderStatus(1)).isEqualTo("PAID");
            assertNoCancelCommand();
        }
    }

    private void assertCancelCommand(String reason, String requestedBy) {
        var row = jdbcTemplate.queryForMap("SELECT routing_key, aggregate_id, payload::text AS payload "
                + "FROM orders.outbox WHERE event_type = 'CancelPayment'");
        assertThat(row.get("routing_key")).isEqualTo("command.payment.cancel");
        assertThat(row.get("aggregate_id")).isEqualTo(1L);
        assertThat(row.get("payload").toString())
                .contains("\"orderId\": 1", "\"reason\": \"" + reason + "\"", "\"requestedBy\": \"" + requestedBy + "\"");
    }

    private void assertNoCancelCommand() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders.outbox WHERE event_type = 'CancelPayment'", Integer.class)).isZero();
    }

    @Nested
    class 내_주문_목록 {

        @Test
        void 최신순으로_내_주문만_페이지로_조회한다() throws Exception {
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            placeOrder(8L);
            setStatus(1, "PAID");
            setStatus(2, "PAID");
            setStatus(3, "PAID");

            mockMvc.perform(customer(get("/v1/orders")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(2))
                    .andExpect(jsonPath("$.content[1].id").value(1))
                    .andExpect(jsonPath("$.content[0].storeName").value("싹분식"))
                    .andExpect(jsonPath("$.content[0].status").value("PAID"))
                    .andExpect(jsonPath("$.content[0].items[0].menuName").value("참치김밥"))
                    .andExpect(jsonPath("$.content[0].totalAmount").value(16500))
                    .andExpect(jsonPath("$.content[0].createdAt").exists())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        void 결제대기_결제만료는_목록과_전체_건수에서_빠진다() throws Exception {
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            setStatus(2, "PAYMENT_EXPIRED");
            setStatus(3, "PAID");

            mockMvc.perform(customer(get("/v1/orders")))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(3))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void 거절_취소_주문은_목록에_보인다() throws Exception {
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            setStatus(1, "REJECTED");
            setStatus(2, "CANCELED");

            mockMvc.perform(customer(get("/v1/orders")))
                    .andExpect(jsonPath("$.content[0].status").value("CANCELED"))
                    .andExpect(jsonPath("$.content[1].status").value("REJECTED"));
        }

        @Test
        void 페이지_크기를_지정한다() throws Exception {
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");
            setStatus(2, "PAID");

            mockMvc.perform(customer(get("/v1/orders")).param("page", "1").param("size", "1"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }

        @Test
        void 페이지_크기가_100을_넘으면_400() throws Exception {
            mockMvc.perform(customer(get("/v1/orders")).param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    class 주문_상세 {

        @Test
        void 주문한_고객은_조회한다() throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, "PAID");

            mockMvc.perform(customer(get("/v1/orders/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.riderName").value(nullValue()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"PENDING_PAYMENT", "PAYMENT_EXPIRED"})
        void 고객에게_결제대기_결제만료는_404_ORDER_NOT_FOUND(String unpaid) throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, unpaid);

            mockMvc.perform(customer(get("/v1/orders/1")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"REJECTED", "CANCELED"})
        void 거절_취소_주문은_고객이_조회한다(String status) throws Exception {
            placeOrder(CUSTOMER_ID);
            setStatus(1, status);

            mockMvc.perform(customer(get("/v1/orders/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(status));
        }

        @Test
        void 가게_사장님은_조회한다() throws Exception {
            placeOrder(CUSTOMER_ID);

            mockMvc.perform(member(get("/v1/orders/1"), OWNER_ID, "OWNER"))
                    .andExpect(status().isOk());
        }

        @Test
        void 다른_고객_다른_사장님_라이더는_403() throws Exception {
            placeOrder(CUSTOMER_ID);

            mockMvc.perform(member(get("/v1/orders/1"), 8L, "CUSTOMER"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(member(get("/v1/orders/1"), 20L, "OWNER"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(member(get("/v1/orders/1"), CUSTOMER_ID, "RIDER"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 없는_주문이면_404_ORDER_NOT_FOUND() throws Exception {
            mockMvc.perform(customer(get("/v1/orders/999")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }
    }

    @Nested
    class 내부_주문_조회 {

        @Test
        void payment_가_검증과_스냅샷에_쓸_값을_돌려준다() throws Exception {
            placeOrder(CUSTOMER_ID);

            // 인증 헤더 없이 호출
            mockMvc.perform(get("/internal/orders/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAmount").value(16500))
                    .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.ownerId").value(OWNER_ID))
                    .andExpect(jsonPath("$.storeId").value(1))
                    .andExpect(jsonPath("$.storeName").value("싹분식"))
                    .andExpect(jsonPath("$.customerEmail").value("customer@baedalsak.click"))
                    .andExpect(jsonPath("$.ownerEmail").value("owner@baedalsak.click"))
                    // 항목이 하나면 메뉴명 그대로
                    .andExpect(jsonPath("$.orderName").value("참치김밥"));
        }

        @Test
        void 주문명은_첫_메뉴명_외_나머지_항목_수() throws Exception {
            addToCart(1L, 2);
            addToCart(2L, 1);
            createOrder().andExpect(status().isCreated());

            // 수량(참치김밥 2개)이 아니라 항목 수로 센다
            mockMvc.perform(get("/internal/orders/1"))
                    .andExpect(jsonPath("$.orderName").value("참치김밥 외 1건"));
        }

        @Test
        void 주문명이_100자를_넘으면_첫_메뉴명을_줄인다() throws Exception {
            String longName = "가".repeat(100);
            storeMenus = List.of(
                    new MenuInternal(1L, longName, 4500, MenuInternal.SaleStatus.ON_SALE),
                    new MenuInternal(2L, "라볶이", 6000, MenuInternal.SaleStatus.ON_SALE));
            addToCart(1L, 2);
            addToCart(2L, 1);
            createOrder().andExpect(status().isCreated());

            // " 외 1건" 5자 + 메뉴명 95자 = 100자 (토스 orderName 최대 길이)
            mockMvc.perform(get("/internal/orders/1"))
                    .andExpect(jsonPath("$.orderName").value("가".repeat(95) + " 외 1건"));
        }

        @Test
        void 없는_주문이면_404_ORDER_NOT_FOUND() throws Exception {
            mockMvc.perform(get("/internal/orders/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }
    }

    // 결제 완료 등은 payment 이벤트(#32)로 바뀌므로 테스트에서는 DB 를 직접 바꾼다
    private void setStatus(long orderId, String status) {
        jdbcTemplate.update("UPDATE orders.orders SET status = ? WHERE id = ?", status, orderId);
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = ?", String.class, orderId);
    }

    // payment 가 준 에러가 그대로 전달되는지 볼 때 쓴다
    private enum TestErrorCode implements ErrorCode {
        PAYMENT_IN_PROGRESS;

        @Override
        public int getStatus() {
            return 409;
        }

        @Override
        public String getMessage() {
            return "이전 결제를 확인하고 있어요";
        }
    }

    private ResultActions owner(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(member(request, OWNER_ID, "OWNER"));
    }

    private void openStore(int deliveryFee, int minOrderAmount) {
        given(storeClient.getStore(1L)).willReturn(Optional.of(
                new StoreInternal("싹분식", "서울시 마포구 1", OWNER_ID, deliveryFee, minOrderAmount, true)));
    }

    // 참치김밥 3개(13500) + 배달비 3000 = 16500
    private void placeOrder(long customerId) throws Exception {
        // 주문 생성은 장바구니를 비우지 않으므로 앞 주문의 항목을 치운다
        jdbcTemplate.update("DELETE FROM orders.cart WHERE customer_id = ?", customerId);
        if (customerId != CUSTOMER_ID) {
            given(memberClient.getMember(customerId)).willReturn(Optional.of(
                    new MemberInternal("다른고객", "010-0000-0000", "other@baedalsak.click", "CUSTOMER")));
        }
        mockMvc.perform(member(post("/v1/cart/items"), customerId, "CUSTOMER")
                        .content("{\"storeId\":1,\"menuId\":1,\"quantity\":3}"))
                .andExpect(status().isOk());
        mockMvc.perform(member(post("/v1/orders"), customerId, "CUSTOMER").content(ORDER_BODY))
                .andExpect(status().isCreated());
    }

    private void addToCart(long menuId, int quantity) throws Exception {
        mockMvc.perform(customer(post("/v1/cart/items"))
                        .content("{\"storeId\":1,\"menuId\":" + menuId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions createOrder() throws Exception {
        return mockMvc.perform(customer(post("/v1/orders")).content(ORDER_BODY));
    }

    private MockHttpServletRequestBuilder customer(MockHttpServletRequestBuilder request) {
        return member(request, CUSTOMER_ID, "CUSTOMER");
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder request, long memberId, String role) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header(LoginMember.ID_HEADER, String.valueOf(memberId))
                .header(LoginMember.ROLE_HEADER, role);
    }
}
