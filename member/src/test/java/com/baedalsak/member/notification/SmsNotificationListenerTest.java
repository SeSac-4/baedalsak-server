package com.baedalsak.member.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 사장님 새 주문 문자 (#217): 이벤트의 ownerId 와 문구를 서비스에 제대로 넘기는지 */
class SmsNotificationListenerTest {

  private final SmsNotificationService service = mock(SmsNotificationService.class);
  private final SmsNotificationListener listener = new SmsNotificationListener(service);

  @Test
  void 결제_완료면_사장님_번호로_새_주문_문자() {
    UUID eventId = UUID.randomUUID();

    listener.onPaymentApproved(new PaymentApprovedEvent(5000L, 101L, "시드분식", 18000), eventId.toString());

    verify(service).send(eq(eventId), eq(PaymentApprovedEvent.TYPE), eq(101L),
        eq("[배달싹] 시드분식 새 주문이 들어왔어요 (18,000원). 앱에서 주문을 수락해 주세요"));
  }

  @Test
  void 금액이_없으면_괄호_없이_보냄() {
    assertThat(SmsNotificationListener.ownerNewOrderMessage(new PaymentApprovedEvent(5000L, 101L, "시드분식", null)))
        .isEqualTo("[배달싹] 시드분식 새 주문이 들어왔어요. 앱에서 주문을 수락해 주세요");
  }

  @Test
  void ownerId_가_없어도_서비스로_넘김() {   // 서비스가 DLQ 로 보냄 (SmsNotificationServiceTest 에서 확인)
    UUID eventId = UUID.randomUUID();

    listener.onPaymentApproved(new PaymentApprovedEvent(5000L, null, "시드분식", 18000), eventId.toString());

    verify(service).send(eq(eventId), eq(PaymentApprovedEvent.TYPE), eq(null), anyString());
  }
}
