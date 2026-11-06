package com.baedalsak.payment.event;

import com.baedalsak.payment.service.PaymentCancelService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 결제 취소 명령 리스너 (#127). messageId 가 없거나 UUID 가 아니면 멱등 처리를 할 수 없어 바로 DLQ
 */
class CancelPaymentListenerTest {

    static final CancelPaymentCommand COMMAND = new CancelPaymentCommand(1005L, 7L, "재료 소진", "OWNER");

    final PaymentCancelService service = mock(PaymentCancelService.class);
    final CancelPaymentListener listener = new CancelPaymentListener(service);

    @Test
    void messageId_를_명령_ID_로_넘긴다() {
        UUID id = UUID.randomUUID();

        listener.onCancelPayment(COMMAND, id.toString());

        verify(service).handle(id, COMMAND);
    }

    @Test
    void messageId_가_없거나_UUID_가_아니면_DLQ() {
        assertThatThrownBy(() -> listener.onCancelPayment(COMMAND, null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onCancelPayment(COMMAND, "not-a-uuid"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(service);
    }
}
