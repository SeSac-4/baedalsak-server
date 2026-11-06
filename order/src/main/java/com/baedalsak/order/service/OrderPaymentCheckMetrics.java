package com.baedalsak.order.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 점검이 찾은 사람이 봐야 할 건수. 누적 카운터가 아니라 현재 미해결 건수 게이지다 (사가 결정 7).
 * 파드마다 같은 값을 내므로 알림 규칙은 합계가 아니라 max 로 본다.
 */
@Component
public class OrderPaymentCheckMetrics {

    static final String GAUGE_NAME = "order.payment.check.unresolved";

    enum Unresolved {
        AMOUNT_MISMATCH,
        UNEXPECTED_STATUS,
        REFUND_UNDELIVERED
    }

    private final Map<Unresolved, AtomicInteger> counts = new EnumMap<>(Unresolved.class);

    public OrderPaymentCheckMetrics(MeterRegistry registry) {
        for (Unresolved type : Unresolved.values()) {
            AtomicInteger count = new AtomicInteger();
            counts.put(type, count);
            Gauge.builder(GAUGE_NAME, count, AtomicInteger::get)
                    .tag("type", type.name().toLowerCase(Locale.ROOT))
                    .register(registry);
        }
    }

    void set(Unresolved type, int count) {
        counts.get(type).set(count);
    }
}
