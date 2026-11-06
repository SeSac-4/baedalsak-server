package com.baedalsak.order.service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.order.client.PaymentClient;
import com.baedalsak.order.client.PaymentStatusResult;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderStatus;
import com.baedalsak.order.repository.OrderRepository;
import com.baedalsak.order.service.OrderPaymentCheckMetrics.Unresolved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 조정자 점검 (사가 결정 3-3). 놓친 승인 반영, 오래된 결제대기 만료, 환불 명령 도착 확인.
 * 외부 호출은 트랜잭션 밖에서 하고, 상태 변경은 조건부 UPDATE 라 파드가 겹쳐도 한 번만 반영된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentChecker {

    // 5분 + 1분 주기 = 놓친 승인을 약 6분 안에 찾는다. 10분 규칙보다 짧아 정상 결제가 환불되지 않는다
    static final Duration PENDING_CHECK_AFTER = Duration.ofMinutes(5);
    // 토스 결제창 30분 + 인증 후 승인 10분 + 여유 5분
    static final Duration EXPIRE_AFTER = Duration.ofMinutes(45);
    // 조정자의 확인 범위. 이후는 대사가 잡는다
    static final Duration REFUND_CHECK_WINDOW = Duration.ofHours(24);

    private static final List<OrderStatus> REFUND_TARGETS =
            List.of(OrderStatus.PAYMENT_EXPIRED, OrderStatus.REJECTED, OrderStatus.CANCELED);

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final OrderPaymentService orderPaymentService;
    private final OrderPaymentTransitions transitions;
    private final OrderPaymentCheckMetrics metrics;
    private final Clock clock;

    // 5분 지난 결제대기 주문을 payment 의 답에 따라 정리한다
    public void checkPendingOrders() {
        OffsetDateTime now = now();
        OffsetDateTime cutoff = now.minus(PENDING_CHECK_AFTER);
        scan(afterId -> orderRepository.findTop100ByStatusAndCreatedAtLessThanEqualAndIdGreaterThanOrderById(
                        OrderStatus.PENDING_PAYMENT, cutoff, afterId),
                (order, payment, counts) -> checkPending(order, payment, now, counts),
                List.of(Unresolved.AMOUNT_MISMATCH, Unresolved.UNEXPECTED_STATUS));
    }

    // 최근 24시간 안에 환불 대상이 된 주문의 결제가 아직 승인 상태면 명령을 다시 보낸다
    public void checkRefunds() {
        OffsetDateTime now = now();
        OffsetDateTime since = now.minus(REFUND_CHECK_WINDOW);
        scan(afterId -> orderRepository.findTop100ByStatusInAndUpdatedAtGreaterThanEqualAndIdGreaterThanOrderById(
                        REFUND_TARGETS, since, afterId),
                (order, payment, counts) -> checkRefund(order, payment, now, counts),
                List.of(Unresolved.REFUND_UNDELIVERED));
    }

    // 100건씩 끝까지 본다 (아무 일 없는 주문이 앞을 막아 뒤를 못 보는 일이 없게).
    // 상태 조회가 실패하면 이번 주기를 멈추고 게이지는 이전 값으로 둔다
    private void scan(Function<Long, List<Order>> nextPage, OrderCheck check, List<Unresolved> reported) {
        Map<Unresolved, Integer> counts = new EnumMap<>(Unresolved.class);
        reported.forEach(type -> counts.put(type, 0));
        long afterId = 0;
        for (List<Order> orders = nextPage.apply(afterId); !orders.isEmpty(); orders = nextPage.apply(afterId)) {
            Map<Long, PaymentStatusResult> payments;
            try {
                payments = paymentClient.findStatuses(orders.stream().map(Order::getId).toList()).stream()
                        .collect(Collectors.toMap(PaymentStatusResult::orderId, Function.identity()));
            } catch (BusinessException e) {
                log.warn("점검 결제 상태 조회 실패, 다음 주기에 다시 code={}", e.getErrorCode().name());
                return;
            }
            for (Order order : orders) {
                try {
                    check.run(order, payments.get(order.getId()), counts);
                } catch (RuntimeException e) {
                    // 한 건의 실패가 나머지 주문을 막지 않게 한다. 다음 주기에 다시 본다
                    log.error("점검 처리 실패 orderId={}", order.getId(), e);
                }
            }
            afterId = orders.getLast().getId();
        }
        counts.forEach(metrics::set);
    }

    private void checkPending(Order order, PaymentStatusResult payment, OffsetDateTime now,
                              Map<Unresolved, Integer> counts) {
        if (payment == null) {
            expireIfOld(order, now);
            return;
        }
        switch (payment.status()) {
            case PaymentStatusResult.APPROVED -> {
                if (order.getTotalAmount().equals(payment.amount())) {
                    transitions.applyApproval(order, payment.paymentId(), payment.approvedAt());
                } else {
                    log.error("점검 승인 금액 불일치 orderId={}, orderAmount={}, paidAmount={}",
                            order.getId(), order.getTotalAmount(), payment.amount());
                    counts.merge(Unresolved.AMOUNT_MISMATCH, 1, Integer::sum);
                }
            }
            // 같은 고객의 새 결제 요청에 밀려난 이전 주문
            case PaymentStatusResult.EXPIRED -> transitions.expire(order.getId());
            case PaymentStatusResult.REQUESTED -> expireIfOld(order, now);
            default -> {
                log.error("점검 예상 밖 결제 상태 orderId={}, status={}", order.getId(), payment.status());
                counts.merge(Unresolved.UNEXPECTED_STATUS, 1, Integer::sum);
            }
        }
    }

    private void expireIfOld(Order order, OffsetDateTime now) {
        if (!order.getCreatedAt().plus(EXPIRE_AFTER).isAfter(now)) {
            orderPaymentService.expire(order);
        }
    }

    private void checkRefund(Order order, PaymentStatusResult payment, OffsetDateTime now,
                             Map<Unresolved, Integer> counts) {
        if (payment == null || !PaymentStatusResult.APPROVED.equals(payment.status())) {
            return;
        }
        if (transitions.resendRefundIfDue(order.getId(), payment.paymentId())) {
            log.error("환불 명령 재전송 orderId={}, paymentId={}, orderStatus={}",
                    order.getId(), payment.paymentId(), order.getStatus());
        }
        // 방금 환불 대상이 된 주문은 payment 가 처리 중일 수 있어 세지 않는다
        if (order.getUpdatedAt().plus(OrderPaymentTransitions.REFUND_RESEND_INTERVAL).isBefore(now)) {
            counts.merge(Unresolved.REFUND_UNDELIVERED, 1, Integer::sum);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    @FunctionalInterface
    private interface OrderCheck {
        void run(Order order, PaymentStatusResult payment, Map<Unresolved, Integer> counts);
    }
}
