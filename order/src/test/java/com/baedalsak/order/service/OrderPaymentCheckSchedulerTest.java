package com.baedalsak.order.service;

import com.baedalsak.order.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 설정. 첫 실행은 1분 뒤라 이 테스트 중에는 점검이 돌지 않는다.
 */
@TestPropertySource(properties = "order.payment-check.enabled=true")
class OrderPaymentCheckSchedulerTest extends IntegrationTest {

    @Autowired
    OrderPaymentCheckScheduler scheduler;

    @Autowired
    ThreadPoolTaskScheduler taskScheduler;

    @Test
    void 점검이_outbox_발행을_막지_않게_스레드가_2개다() {
        assertThat(scheduler).isNotNull();
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
    }
}
