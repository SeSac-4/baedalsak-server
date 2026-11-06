package com.baedalsak.payment.config;

import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossCancelClient;
import com.baedalsak.payment.config.OriginTraceIdAdviceTest.Listener;
import com.baedalsak.payment.domain.CancelRequester;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.event.CancelPaymentCommand;
import com.baedalsak.payment.repository.PaymentCancelRepository;
import com.baedalsak.payment.repository.PaymentRepository;
import com.baedalsak.payment.repository.ProcessedEventRepository;
import com.baedalsak.payment.service.PaymentCancelRecorder;
import com.baedalsak.payment.service.PaymentCancelService;
import com.baedalsak.payment.service.TossCancelRetryException;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 결제 취소 명령의 재시도 소진 (#127). 실제 취소 명령 컨테이너의 재시도 설정(3회 시도)으로 PaymentCancelService 를 감싸고
 * 토스가 계속 응답하지 않을 때: 첫 시도에만 진행 중 취소 행을 만들고, 이후 재시도는 결제 상태(환불 처리 중)로 같은 행 · 같은 멱등 키로
 * 이어서 부르며, 3번 모두 실패하면 DLQ 복구기로 넘기고 완료 · 실패 기록은 하지 않는다 (진행 중 유지 → 오래 머문 환불 감시 대상).
 */
class CancelCommandRetryExhaustionTest {

    static final List<String> recovered = new ArrayList<>();

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(RabbitConfig.class)
            .withBean(MessageRecoverer.class, () -> (message, cause) -> {
                // 복구기(운영은 DLQ)로 넘어온 원인 중 재시도 대상 예외를 기록
                for (Throwable t = cause; t != null; t = t.getCause()) {
                    if (t instanceof TossCancelRetryException) {
                        recovered.add(t.getClass().getSimpleName());
                        break;
                    }
                }
                throw new AmqpRejectAndDontRequeueException("DLQ", cause);
            })
            .withPropertyValues(
                    "spring.rabbitmq.listener.simple.retry.enabled=true",
                    "spring.rabbitmq.listener.simple.retry.max-retries=2",
                    "spring.rabbitmq.listener.simple.retry.initial-interval=10ms");

    @AfterEach
    void clear() {
        recovered.clear();
    }

    @Test
    void 토스가_계속_응답하지_않으면_같은_멱등_키로_3번_부르고_DLQ_진행_중은_유지한다() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentCancelRepository paymentCancelRepository = mock(PaymentCancelRepository.class);
        PaymentCancelRecorder recorder = mock(PaymentCancelRecorder.class);
        TossCancelClient tossCancelClient = mock(TossCancelClient.class);
        PaymentCancelService service = new PaymentCancelService(paymentRepository, paymentCancelRepository,
                mock(ProcessedEventRepository.class), recorder, tossCancelClient, "주문 취소에 따른 결제 취소");

        Payment approved = payment(PaymentStatus.APPROVED);
        Payment cancelRequested = payment(PaymentStatus.CANCEL_REQUESTED);
        PaymentCancel pending = PaymentCancel.fullCancel(approved, "재료 소진", CancelRequester.OWNER);
        ReflectionTestUtils.setField(pending, "id", 3L);
        // 첫 시도: 승인 완료 → 환불 처리 중 + 진행 중 행. 재시도: 이미 환불 처리 중 → 같은 진행 중 행
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(approved), Optional.of(cancelRequested));
        given(recorder.start(approved, "재료 소진", CancelRequester.OWNER)).willReturn(Optional.of(pending));
        given(paymentCancelRepository.findByPaymentIdAndStatus(7L, PaymentCancelStatus.PENDING))
                .willReturn(Optional.of(pending));
        List<String> idempotencyKeys = new ArrayList<>();
        given(tossCancelClient.cancel(anyString(), anyString(), anyString(), anyLong())).willAnswer(invocation -> {
            idempotencyKeys.add(invocation.getArgument(2));
            throw TossApiException.noResponse(new SocketTimeoutException("Read timed out"));
        });

        runner.run(context -> {
            Listener listener = proxy(context.getBean(RabbitConfig.CANCEL_COMMAND_CONTAINER_FACTORY,
                    SimpleRabbitListenerContainerFactory.class),
                    data -> service.handle(UUID.randomUUID(), new CancelPaymentCommand(1005L, 7L, "재료 소진", "OWNER")));

            assertThatThrownBy(() -> listener.invokeListener(null, OriginTraceIdAdviceTest.message("abc123")));

            assertThat(idempotencyKeys).hasSize(3).containsOnly(pending.getIdempotencyKey());
            assertThat(recovered).containsExactly(TossCancelRetryException.class.getSimpleName());
            verify(recorder, times(1)).start(any(), anyString(), any());
            verify(recorder, times(3)).countAttempt(3L);
            verify(recorder, never()).complete(any(), any(), any(), any(), any());
            verify(recorder, never()).fail(any(), any(), any(), any(), any());
        });
    }

    // 컨테이너와 같은 방식(ProxyFactory, advice chain 순서대로)으로 리스너를 감싼다
    private static Listener proxy(SimpleRabbitListenerContainerFactory factory, java.util.function.Consumer<Object> body) {
        ProxyFactory proxyFactory = new ProxyFactory((Listener) (channel, data) -> body.accept(data));
        proxyFactory.addInterface(Listener.class);
        for (Advice advice : factory.getAdviceChain()) {
            proxyFactory.addAdvice(advice);
        }
        return (Listener) proxyFactory.getProxy();
    }

    private static Payment payment(PaymentStatus status) {
        Payment payment = Payment.request(1005L, 1L, 10L, 3L, "싹분식", "customer@test.com", "owner@test.com",
                "BDS-1005-1", 18000);
        ReflectionTestUtils.setField(payment, "id", 7L);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "tossPaymentKey", "tgen_key");
        return payment;
    }
}
