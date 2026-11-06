package com.baedalsak.payment.event;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * 커밋 직후 즉시 발행 (#151). 커밋되면 별도 스레드에서 OutboxPublisher.publishPending 을 부르고, 롤백이면 부르지 않는다.
 * 커밋이 몰리면 실행 중 1개 + 대기 1개로 합치고, 즉시 발행이 실패해도 예외가 퍼지지 않는다(폴링이 재시도).
 */
class OutboxImmediatePublisherTest {

    final OutboxPublisher outboxPublisher = mock(OutboxPublisher.class);

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TxConfig.class)
            .withBean(OutboxPublisher.class, () -> outboxPublisher)
            .withBean(OutboxImmediatePublisher.class);

    @Test
    void 커밋되면_발행을_시작한다() {
        CountDownLatch called = new CountDownLatch(1);
        willAnswer(inv -> { called.countDown(); return null; }).given(outboxPublisher).publishPending();

        runner.run(context -> {
            inTransaction(context.getBean(PlatformTransactionManager.class), context, false);

            assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
        });
    }

    @Test
    void 롤백되면_발행하지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        willAnswer(inv -> { calls.incrementAndGet(); return null; }).given(outboxPublisher).publishPending();

        runner.run(context -> {
            inTransaction(context.getBean(PlatformTransactionManager.class), context, true);

            Thread.sleep(300);
            assertThat(calls).hasValue(0);
        });
    }

    @Test
    void 커밋이_몰리면_실행_중_1개와_대기_1개로_합친다() {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        willAnswer(inv -> {
            calls.incrementAndGet();
            firstStarted.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).given(outboxPublisher).publishPending();

        runner.run(context -> {
            PlatformTransactionManager tm = context.getBean(PlatformTransactionManager.class);
            inTransaction(tm, context, false);
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 10; i++) {
                inTransaction(tm, context, false);   // 첫 실행이 막혀 있는 동안 신호 10개
            }
            release.countDown();

            Thread.sleep(500);
            // 실행 중 1개 + 대기 1개 → 대기 실행이 그동안 커밋된 행까지 모두 보낸다
            assertThat(calls).hasValue(2);
        });
    }

    @Test
    void 즉시_발행이_실패해도_예외가_퍼지지_않고_다음_신호는_다시_발행한다() {
        CountDownLatch second = new CountDownLatch(2);
        willAnswer(inv -> {
            second.countDown();
            if (second.getCount() == 1) {
                throw new IllegalStateException("broker down");
            }
            return null;
        }).given(outboxPublisher).publishPending();

        runner.run(context -> {
            PlatformTransactionManager tm = context.getBean(PlatformTransactionManager.class);
            inTransaction(tm, context, false);
            Thread.sleep(200);
            inTransaction(tm, context, false);

            assertThat(second.await(5, TimeUnit.SECONDS)).isTrue();
        });
    }

    private static void inTransaction(PlatformTransactionManager tm, ApplicationEventPublisher publisher, boolean rollback) {
        new TransactionTemplate(tm).executeWithoutResult(status -> {
            publisher.publishEvent(new OutboxAppended());
            if (rollback) {
                status.setRollbackOnly();
            }
        });
    }

    @Configuration
    @EnableTransactionManagement
    static class TxConfig {

        // DB 없이 트랜잭션 동기화(커밋 · 롤백 뒤 리스너 호출)만 일으키는 트랜잭션 매니저
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }
}
