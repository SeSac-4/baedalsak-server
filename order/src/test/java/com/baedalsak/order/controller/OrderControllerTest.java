package com.baedalsak.order.controller;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.order.client.MemberClient;
import com.baedalsak.order.client.MemberInternal;
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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

    // 장바구니 담기·주문 생성 때 store 가 돌려줄 메뉴 (테스트마다 바꿀 수 있음)
    List<MenuInternal> storeMenus;

    @BeforeEach
    void setUp() {
        storeMenus = List.of(
                new MenuInternal(1L, "참치김밥", 4500, MenuInternal.SaleStatus.ON_SALE),
                new MenuInternal(2L, "라볶이", 6000, MenuInternal.SaleStatus.ON_SALE));
        openStore(3000, 12000);
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
        void 장바구니로_결제대기_주문을_만들고_장바구니를_비운다() throws Exception {
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
                    // 고객 연락처는 내려주지 않는다 (O-03)
                    .andExpect(jsonPath("$.customerPhone").doesNotExist());

            mockMvc.perform(customer(get("/v1/cart")))
                    .andExpect(jsonPath("$.cannotOrderReason").value("CART_EMPTY"));
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
    class 내_주문_목록 {

        @Test
        void 최신순으로_내_주문만_페이지로_조회한다() throws Exception {
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);
            placeOrder(8L);

            mockMvc.perform(customer(get("/v1/orders")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(2))
                    .andExpect(jsonPath("$.content[1].id").value(1))
                    .andExpect(jsonPath("$.content[0].storeName").value("싹분식"))
                    .andExpect(jsonPath("$.content[0].status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.content[0].items[0].menuName").value("참치김밥"))
                    .andExpect(jsonPath("$.content[0].totalAmount").value(16500))
                    .andExpect(jsonPath("$.content[0].createdAt").exists())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        void 페이지_크기를_지정한다() throws Exception {
            placeOrder(CUSTOMER_ID);
            placeOrder(CUSTOMER_ID);

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

            mockMvc.perform(customer(get("/v1/orders/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.riderName").value(nullValue()));
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
                    .andExpect(jsonPath("$.ownerEmail").value("owner@baedalsak.click"));
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

    private ResultActions owner(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(member(request, OWNER_ID, "OWNER"));
    }

    private void openStore(int deliveryFee, int minOrderAmount) {
        given(storeClient.getStore(1L)).willReturn(Optional.of(
                new StoreInternal("싹분식", "서울시 마포구 1", OWNER_ID, deliveryFee, minOrderAmount, true)));
    }

    // 참치김밥 3개(13500) + 배달비 3000 = 16500
    private void placeOrder(long customerId) throws Exception {
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
