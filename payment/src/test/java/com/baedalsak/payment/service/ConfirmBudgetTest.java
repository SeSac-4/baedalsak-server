package com.baedalsak.payment.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 확인 시간 예산 (#125). 예산 15초, 토스 호출 1건 최대 7초(연결 2 + 읽기 5)
 */
class ConfirmBudgetTest {

    final PaymentConfirmServiceTest.MutableClock clock = new PaymentConfirmServiceTest.MutableClock();
    final ConfirmBudget budget = ConfirmBudget.start(clock, Duration.ofSeconds(15), Duration.ofSeconds(7));

    @Test
    void 시작하면_호출할_수_있다() {
        assertThat(budget.canCall()).isTrue();
        assertThat(budget.remaining()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void 남은_시간이_호출_1건_최대와_같으면_호출할_수_있다() {
        clock.advance(Duration.ofSeconds(8));

        assertThat(budget.canCall()).isTrue();
    }

    @Test
    void 남은_시간이_호출_1건_최대보다_적으면_호출하지_않는다() {
        clock.advance(Duration.ofMillis(8001));

        assertThat(budget.canCall()).isFalse();
    }
}
