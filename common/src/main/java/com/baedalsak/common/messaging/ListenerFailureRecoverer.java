package com.baedalsak.common.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;

import lombok.extern.slf4j.Slf4j;

/**
 * 리스너가 포기한 메시지를 DLQ 로 보내며 로그 한 줄을 남긴다 (#186).
 * 기본 RejectAndDontRequeueRecoverer 대신 쓴다 — 기본은 WARN "Retries exhausted" 에 본문이 바이트 주소로 찍힘.
 * ListenerErrorHandler 와 함께 쓸 것 (같은 실패를 두 번 찍지 않게).
 */
@Slf4j
public class ListenerFailureRecoverer implements MessageRecoverer {

  @Override
  public void recover(Message message, Throwable cause) {
    MessageProperties props = message.getMessageProperties();
    Throwable root = rootCause(cause);
    if (isPermanent(cause)) {
      // 다시 해도 소용없는 오류 (이벤트 ID 없음 · JSON 깨짐 등) → 원인 문구로 충분, 긴 오류 내용 없이
      log.error("리스너 실패 → DLQ (영구 오류, 재시도 안 함) queue={} routingKey={} eventType={} eventId={} cause={}",
          props.getConsumerQueue(), props.getReceivedRoutingKey(), props.getHeader("eventType"),
          props.getMessageId(), root.toString());
    } else {
      // 재시도를 다 썼는데도 실패 (외부 장애 · 버그) → 원인을 찾게 오류 내용까지 한 번만
      log.error("리스너 실패 → DLQ (재시도 후 실패) queue={} routingKey={} eventType={} eventId={} cause={}",
          props.getConsumerQueue(), props.getReceivedRoutingKey(), props.getHeader("eventType"),
          props.getMessageId(), root.toString(), root);
    }
    // requeue 하지 말라는 표시를 붙여 던짐 → 큐가 DLX 로 보냄 (기본 처리기와 같은 동작)
    throw new ListenerExecutionFailedException("리스너 실패 → DLQ", new AmqpRejectAndDontRequeueException(cause), message);
  }

  /**
   * 다시 해도 소용없는 오류인가. 재시도 정책(predicate)에서도 이걸 써서 판단을 한 곳에 둔다.
   * AmqpRejectAndDontRequeueException: 서비스가 직접 "영구 오류"라고 던진 것 / MessageConversionException: 본문 JSON 이 깨짐
   */
  public static boolean isPermanent(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof AmqpRejectAndDontRequeueException || cause instanceof MessageConversionException) {
        return true;
      }
    }
    return false;
  }

  private static Throwable rootCause(Throwable error) {
    Throwable root = error;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root;
  }
}