package com.baedalsak.order.service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.order.client.PaymentClient;
import com.baedalsak.order.client.PaymentExpireResult;
import com.baedalsak.order.client.PaymentStatusResult;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.repository.OrderRepository;
import com.baedalsak.order.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 조정자 점검 (사가 결정 3-3). 실제 DB 에 주문을 두고 payment 응답만 대신한다.
 */
class OrderPaymentCheckerTest extends IntegrationTest {

    static final int AMOUNT = 16500;

    @Autowired
    OrderPaymentChecker checker;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    MeterRegistry meterRegistry;

    @MockitoBean
    PaymentClient paymentClient;

    @Nested
    class 결제대기 {

        @Test
        void 만든_지_5분이_안_됐으면_조회하지_않는다() {
            insertOrder("PENDING_PAYMENT", 4);

            checker.checkPendingOrders();

            verify(paymentClient, never()).findStatuses(anyList());
        }

        @Test
        void 승인_후_10분_이내면_결제완료() {
            Long orderId = insertOrder("PENDING_PAYMENT", 6);
            givenPayment(orderId, "APPROVED", AMOUNT, minutesAgo(5));

            checker.checkPendingOrders();

            assertThat(status(orderId)).isEqualTo("PAID");
            assertThat(cancelCommands(orderId)).isZero();
        }

        @Test
        void 승인_후_10분이_지났으면_결제만료와_자동_환불() {
            Long orderId = insertOrder("PENDING_PAYMENT", 20);
            givenPayment(orderId, "APPROVED", AMOUNT, minutesAgo(11));

            checker.checkPendingOrders();

            assertThat(status(orderId)).isEqualTo("PAYMENT_EXPIRED");
            assertThat(cancelCommands(orderId)).isOne();
        }

        @Test
        void 승인_금액이_다르면_그대로_두고_알린다() {
            Long orderId = insertOrder("PENDING_PAYMENT", 6);
            givenPayment(orderId, "APPROVED", 9999, minutesAgo(1));

            checker.checkPendingOrders();

            assertThat(status(orderId)).isEqualTo("PENDING_PAYMENT");
            assertThat(unresolved("amount_mismatch")).isEqualTo(1);
        }

        @Test
        void 해결되면_알림_건수가_0으로_돌아온다() {
            Long orderId = insertOrder("PENDING_PAYMENT", 6);
            givenPayment(orderId, "APPROVED", 9999, minutesAgo(1));
            checker.checkPendingOrders();

            givenPayment(orderId, "APPROVED", AMOUNT, minutesAgo(1));
            checker.checkPendingOrders();

            assertThat(unresolved("amount_mismatch")).isZero();
        }

        @Test
        void 새_결제_요청에_밀려났으면_바로_결제만료() {
            Long orderId = insertOrder("PENDING_PAYMENT", 6);
            givenPayment(orderId, "EXPIRED", AMOUNT, null);

            checker.checkPendingOrders();

            assertThat(status(orderId)).isEqualTo("PAYMENT_EXPIRED");
            verify(paymentClient, never()).expire(anyLong());
        }

        @Test
        void 예상_밖_상태면_그대로_두고_알린다() {
            Long orderId = insertOrder("PENDING_PAYMENT", 6);
            givenPayment(orderId, "CANCEL_REQUESTED", AMOUNT, minutesAgo(1));

            checker.checkPendingOrders();

            assertThat(status(orderId)).isEqualTo("PENDING_PAYMENT");
            assertThat(unresolved("unexpected_status")).isEqualTo(1);
        }

        @Test
        void 결제요청됨_45분_미만이면_그대로() {
            Long orderId = insertOrder("PENDING_PAYMENT", 44);
            givenPayment(orderId, "REQUESTED", AMOUNT, null);

            checker.checkPendingOrders();

            assertThat(status(orderId)).isEqualTo("PENDING_PAYMENT");
            verify(paymentClient, never()).expire(anyLong());
        }

        @Test
        void 결제요청됨_45분이_지났으면_만료_호출_후_결제만료() {
            Long orderId = insertOrder("PENDING_PAYMENT", 46);
            givenPayment(orderId, "REQUESTED", AMOUNT, null);
            given(paymentClient.expire(orderId))
                    .willReturn(new PaymentExpireResult(orderId, PaymentExpireResult.Result.EXPIRED, null));

            checker.checkPendingOrders();

            assertThat(status(orderId)).isEqualTo("PAYMENT_EXPIRED");
        }

        @Test
        void 만료_호출에서_승인을_발견하면_10분_규칙을_따른다() {
            Long orderId = insertOrder("PENDING_PAYMENT", 46);
            givenPayment(orderId, "REQUESTED", AMOUNT, null);
            given(paymentClient.expire(orderId))
                    .willReturn(new PaymentExpireResult(orderId, PaymentExpireResult.Result.APPROVED, minutesAgo(1)));

            checker.checkPendingOrders();

            assertThat(status(orderId)).isEqualTo("PAID");
        }

        @Test
        void 만료_호출이_실패하면_결제대기로_두고_다음_주문을_본다() {
            Long failing = insertOrder("PENDING_PAYMENT", 46);
            Long next = insertOrder("PENDING_PAYMENT", 46);
            given(paymentClient.findStatuses(anyList())).willReturn(List.of(
                    payment(failing, "REQUESTED", AMOUNT, null), payment(next, "REQUESTED", AMOUNT, null)));
            willThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE)).given(paymentClient).expire(failing);
            given(paymentClient.expire(next))
                    .willReturn(new PaymentExpireResult(next, PaymentExpireResult.Result.EXPIRED, null));

            checker.checkPendingOrders();

            assertThat(status(failing)).isEqualTo("PENDING_PAYMENT");
            assertThat(status(next)).isEqualTo("PAYMENT_EXPIRED");
        }

        @Test
        void 결제가_없으면_45분_뒤_만료_호출_후_결제만료() {
            Long young = insertOrder("PENDING_PAYMENT", 6);
            Long old = insertOrder("PENDING_PAYMENT", 46);
            given(paymentClient.findStatuses(anyList())).willReturn(List.of());
            given(paymentClient.expire(old))
                    .willReturn(new PaymentExpireResult(old, PaymentExpireResult.Result.NONE, null));

            checker.checkPendingOrders();

            assertThat(status(young)).isEqualTo("PENDING_PAYMENT");
            assertThat(status(old)).isEqualTo("PAYMENT_EXPIRED");
        }

        @Test
        void 상태_조회가_실패하면_주문을_건드리지_않는다() {
            Long orderId = insertOrder("PENDING_PAYMENT", 46);
            willThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE))
                    .given(paymentClient).findStatuses(anyList());

            checker.checkPendingOrders();

            assertThat(status(orderId)).isEqualTo("PENDING_PAYMENT");
            verify(paymentClient, never()).expire(anyLong());
        }

        @Test
        void 백건이_넘으면_다음_100건도_본다() {
            for (int i = 0; i < 101; i++) {
                insertOrder("PENDING_PAYMENT", 6);
            }
            given(paymentClient.findStatuses(anyList())).willReturn(List.of());

            checker.checkPendingOrders();

            verify(paymentClient, times(2)).findStatuses(anyList());
        }
    }

    @Nested
    class 환불_확인 {

        @Test
        void 결제가_승인_상태인데_명령이_없으면_보낸다() {
            Long orderId = insertOrder("PAYMENT_EXPIRED", 60);
            givenPayment(orderId, "APPROVED", AMOUNT, minutesAgo(30));

            checker.checkRefunds();

            assertThat(cancelCommands(orderId)).isOne();
        }

        @Test
        void 거절_취소_주문도_확인한다() {
            Long rejected = insertOrder("REJECTED", 60);
            Long canceled = insertOrder("CANCELED", 60);
            given(paymentClient.findStatuses(anyList())).willReturn(List.of(
                    payment(rejected, "APPROVED", AMOUNT, minutesAgo(60)),
                    payment(canceled, "APPROVED", AMOUNT, minutesAgo(60))));

            checker.checkRefunds();

            assertThat(cancelCommands(rejected)).isOne();
            assertThat(cancelCommands(canceled)).isOne();
        }

        @Test
        void 발행_대기_중인_명령이_있으면_다시_쌓지_않고_알린다() {
            Long orderId = insertOrder("PAYMENT_EXPIRED", 60);
            givenPayment(orderId, "APPROVED", AMOUNT, minutesAgo(30));
            insertCancelCommand(orderId, 60, false);

            checker.checkRefunds();

            assertThat(cancelCommands(orderId)).isOne();
            assertThat(unresolved("refund_undelivered")).isEqualTo(1);
        }

        @Test
        void 최근에_보낸_명령이_있으면_다시_보내지_않는다() {
            Long orderId = insertOrder("PAYMENT_EXPIRED", 60);
            givenPayment(orderId, "APPROVED", AMOUNT, minutesAgo(30));
            insertCancelCommand(orderId, 5, true);

            checker.checkRefunds();

            assertThat(cancelCommands(orderId)).isOne();
        }

        @Test
        void 보낸_지_10분이_지났으면_다시_보낸다() {
            Long orderId = insertOrder("PAYMENT_EXPIRED", 60);
            givenPayment(orderId, "APPROVED", AMOUNT, minutesAgo(30));
            insertCancelCommand(orderId, 11, true);

            checker.checkRefunds();

            assertThat(cancelCommands(orderId)).isEqualTo(2);
        }

        @Test
        void 방금_환불_대상이_됐으면_명령이_있어도_알리지_않는다() {
            Long orderId = insertOrder("PAYMENT_EXPIRED", 1);
            givenPayment(orderId, "APPROVED", AMOUNT, minutesAgo(30));
            insertCancelCommand(orderId, 1, false);

            checker.checkRefunds();

            assertThat(unresolved("refund_undelivered")).isZero();
        }

        @Test
        void 승인_상태가_아니면_보내지_않는다() {
            Long expired = insertOrder("PAYMENT_EXPIRED", 60);
            Long canceling = insertOrder("REJECTED", 60);
            given(paymentClient.findStatuses(anyList())).willReturn(List.of(
                    payment(expired, "EXPIRED", AMOUNT, null),
                    payment(canceling, "CANCEL_REQUESTED", AMOUNT, minutesAgo(60))));

            checker.checkRefunds();

            assertThat(cancelCommands(expired)).isZero();
            assertThat(cancelCommands(canceling)).isZero();
        }

        @Test
        void 지난_24시간_밖의_주문과_결제완료_주문은_보지_않는다() {
            insertOrder("PAYMENT_EXPIRED", 24 * 60 + 1);
            insertOrder("PAID", 60);

            checker.checkRefunds();

            verify(paymentClient, never()).findStatuses(anyList());
        }
    }

    // 주문을 넣고 생성 · 변경 시각을 minutesAgo 분 전으로 되돌린다
    private Long insertOrder(String status, int minutesAgo) {
        Long orderId = orderRepository.save(Order.builder()
                .customerId(7L).storeId(1L).ownerId(10L).storeName("싹분식")
                .deliveryAddress("서울시 강남구 역삼동 1").deliveryDong("역삼동").pickupAddress("서울시 강남구 역삼동 2")
                .customerPhone("01012345678").customerEmail("c@x").ownerEmail("o@x")
                .itemsAmount(13500).deliveryFee(3000)
                .build()).getId();
        jdbcTemplate.update("""
                UPDATE orders.orders
                   SET status = ?, created_at = now() - make_interval(mins => ?), updated_at = now() - make_interval(mins => ?)
                 WHERE id = ?
                """, status, minutesAgo, minutesAgo, orderId);
        return orderId;
    }

    private void insertCancelCommand(Long orderId, int minutesAgo, boolean published) {
        jdbcTemplate.update("""
                INSERT INTO orders.outbox (event_id, event_type, routing_key, aggregate_id, payload, created_at, published_at)
                VALUES (gen_random_uuid(), 'CancelPayment', 'command.payment.cancel', ?, '{}'::jsonb,
                        now() - make_interval(mins => ?), CASE WHEN ? THEN now() END)
                """, orderId, minutesAgo, published);
    }

    private void givenPayment(Long orderId, String status, int amount, OffsetDateTime approvedAt) {
        given(paymentClient.findStatuses(any())).willReturn(List.of(payment(orderId, status, amount, approvedAt)));
    }

    private PaymentStatusResult payment(Long orderId, String status, int amount, OffsetDateTime approvedAt) {
        return new PaymentStatusResult(orderId, orderId + 100, status, amount, approvedAt);
    }

    private OffsetDateTime minutesAgo(int minutes) {
        return OffsetDateTime.now().minusMinutes(minutes);
    }

    private String status(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = ?", String.class, orderId);
    }

    private int cancelCommands(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders.outbox WHERE event_type = 'CancelPayment' AND aggregate_id = ?",
                Integer.class, orderId);
    }

    private double unresolved(String type) {
        return meterRegistry.get(OrderPaymentCheckMetrics.GAUGE_NAME).tag("type", type).gauge().value();
    }
}
