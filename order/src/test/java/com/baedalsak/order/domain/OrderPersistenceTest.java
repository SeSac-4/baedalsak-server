package com.baedalsak.order.domain;

import com.baedalsak.order.repository.OrderItemRepository;
import com.baedalsak.order.repository.OrderRepository;
import com.baedalsak.order.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V1__order_schema.sql 과 엔티티가 맞는지 (앱이 ddl-auto: validate 로 떠야 이 테스트가 실행된다),
 * 저장·조회와 DB 제약, 공통 모듈 연결을 확인한다.
 */
class OrderPersistenceTest extends IntegrationTest {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Test
    void 주문은_결제대기로_저장되고_총액은_항목_금액_더하기_배달비() {
        Order saved = orderRepository.save(order(13500, 3000));

        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(found.getTotalAmount()).isEqualTo(16500);
        assertThat(found.getCustomerEmail()).isEqualTo("customer@baedalsak.click");
        assertThat(found.getOwnerEmail()).isEqualTo("owner@baedalsak.click");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.isOrderedBy(7L)).isTrue();
        assertThat(found.isOwnedBy(10L)).isTrue();
    }

    @Test
    void 주문_항목을_주문별로_조회한다() {
        Order saved = orderRepository.save(order(13500, 3000));
        orderItemRepository.saveAll(List.of(
                OrderItem.create(saved.getId(), 1L, "참치김밥", 4500, 1),
                OrderItem.create(saved.getId(), 2L, "라볶이", 6000, 1)));

        assertThat(orderItemRepository.findAllByOrderIdOrderById(saved.getId()))
                .extracting(OrderItem::getMenuName)
                .containsExactly("참치김밥", "라볶이");
    }

    @Test
    void 총액이_항목_금액_더하기_배달비가_아니면_DB_가_거부한다() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO orders.orders (customer_id, store_id, owner_id, store_name, delivery_address,
                    delivery_dong, pickup_address, customer_phone, customer_email, owner_email,
                    items_amount, delivery_fee, total_amount)
                VALUES (7, 1, 10, '싹분식', '주소', '연남동', '가게 주소', '010-1234-5678', 'c@x', 'o@x',
                    10000, 3000, 9999)
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("orders_total_ck");
    }

    @Test
    void 장바구니는_고객당_하나() {
        String sql = "INSERT INTO orders.cart (customer_id, store_id, store_name, delivery_fee, min_order_amount) "
                + "VALUES (7, ?, '싹분식', 3000, 12000)";
        jdbcTemplate.update(sql, 1L);

        assertThatThrownBy(() -> jdbcTemplate.update(sql, 2L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("cart_customer_uq");
    }

    @Test
    void outbox_payload_는_JSONB_로_저장된다() {
        jdbcTemplate.update("""
                INSERT INTO orders.outbox (event_id, event_type, routing_key, aggregate_id, payload)
                VALUES (gen_random_uuid(), 'OrderAccepted', 'order.accepted', 1, '{"orderId":1}'::jsonb)
                """);

        Long orderId = jdbcTemplate.queryForObject(
                "SELECT (payload->>'orderId')::bigint FROM orders.outbox WHERE published_at IS NULL", Long.class);
        assertThat(orderId).isEqualTo(1L);
    }

    @Test
    void 같은_이벤트_같은_수신자에게_메일은_한_번만_기록된다() {
        String sql = "INSERT INTO orders.mail_log (event_id, recipient) VALUES ('00000000-0000-0000-0000-000000000001', 'c@x')";
        jdbcTemplate.update(sql);

        assertThatThrownBy(() -> jdbcTemplate.update(sql))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("mail_log_event_recipient_uq");
    }

    @Test
    void 공통_모듈_에러_응답이_적용된다() throws Exception {
        mockMvc.perform(get("/v1/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    private Order order(int itemsAmount, int deliveryFee) {
        return Order.builder()
                .customerId(7L)
                .storeId(1L)
                .ownerId(10L)
                .storeName("싹분식")
                .deliveryAddress("서울시 마포구 2")
                .deliveryDong("연남동")
                .pickupAddress("서울시 마포구 1")
                .customerPhone("010-1234-5678")
                .customerEmail("customer@baedalsak.click")
                .ownerEmail("owner@baedalsak.click")
                .storeRequest("단무지 빼 주세요")
                .itemsAmount(itemsAmount)
                .deliveryFee(deliveryFee)
                .build();
    }
}
