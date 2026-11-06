package com.baedalsak.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 조정자 점검 1분 주기 (사가 결정 3-3). order.payment-check.enabled=false 로 끌 수 있다 (테스트).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.payment-check.enabled", havingValue = "true", matchIfMissing = true)
public class OrderPaymentCheckScheduler {

    private static final long PERIOD_MILLIS = 60_000;

    private final OrderPaymentChecker checker;

    @Scheduled(initialDelay = PERIOD_MILLIS, fixedDelay = PERIOD_MILLIS)
    public void run() {
        checker.checkPendingOrders();
        checker.checkRefunds();
    }
}
