package com.baedalsak.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 리스너 실패 처리기 (#186): 영구 오류 판단 · DLQ 로 보내는 예외 · 로그 한 줄 */
@ExtendWith(OutputCaptureExtension.class)
class ListenerFailureRecovererTest {

  private final ListenerFailureRecoverer recoverer = new ListenerFailureRecoverer();

  private static Message message() {
    MessageProperties props = new MessageProperties();
    props.setConsumerQueue("member.sms.order-accepted");
    props.setReceivedRoutingKey("order.accepted");
    props.setMessageId("11111111-1111-1111-1111-111111111111");
    props.setHeader("eventType", "OrderAccepted");
    return new Message("{}".getBytes(), props);
  }

  /** 컨테이너가 감싸서 넘기는 모양 그대로 */
  private static Throwable wrapped(Throwable cause) {
    return new ListenerExecutionFailedException("Listener method threw exception", cause, message());
  }

  // ---------- 영구 오류 판단 (재시도 정책도 이걸 씀) ----------

  @Test
  void 서비스가_던진_영구_오류는_감싸져_있어도_영구() {
    assertThat(ListenerFailureRecoverer.isPermanent(
        wrapped(new AmqpRejectAndDontRequeueException("OrderAccepted without messageId")))).isTrue();
  }

  @Test
  void 본문_JSON_이_깨진_것도_영구() {
    assertThat(ListenerFailureRecoverer.isPermanent(
        wrapped(new MessageConversionException("bad json")))).isTrue();
  }

  @Test
  void 외부_장애_같은_일반_예외는_재시도_대상() {
    assertThat(ListenerFailureRecoverer.isPermanent(wrapped(new IllegalStateException("SNS 503")))).isFalse();
  }

  // ---------- 포기 처리 ----------

  @Test
  void 어떤_실패든_requeue_하지_말라는_표시를_붙여_던짐() {   // → 큐가 DLQ 로 보냄
    assertThatThrownBy(() -> recoverer.recover(message(), wrapped(new IllegalStateException("SNS 503"))))
        .isInstanceOf(ListenerExecutionFailedException.class)
        .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
  }

  @Test
  void 영구_오류는_ERROR_한_줄에_큐_이벤트_정보만_오류_내용은_없음(CapturedOutput output) {
    assertThatThrownBy(() -> recoverer.recover(message(),
        wrapped(new AmqpRejectAndDontRequeueException("OrderAccepted without messageId"))));

    assertThat(output.getOut())
        .contains("리스너 실패 → DLQ (영구 오류, 재시도 안 함)")
        .contains("queue=member.sms.order-accepted", "routingKey=order.accepted",
            "eventType=OrderAccepted", "eventId=11111111-1111-1111-1111-111111111111",
            "OrderAccepted without messageId")
        .doesNotContain("\tat ");   // 스택 줄 없음
  }

  @Test
  void 재시도_후_실패는_원인_오류_내용까지_한_번(CapturedOutput output) {
    assertThatThrownBy(() -> recoverer.recover(message(), wrapped(new IllegalStateException("SNS 503"))));

    assertThat(output.getOut())
        .contains("리스너 실패 → DLQ (재시도 후 실패)", "SNS 503")
        .contains("\tat ");   // 원인 찾기용 스택
  }
}
