package com.baedalsak.common.messaging;

import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;

/**
 * 리스너 에러 처리기 (#186). 기본 처리기와 같지만, ListenerFailureRecoverer 가 이미 로그를 남긴 실패
 * (원인에 AmqpRejectAndDontRequeueException 이 있음)는 다시 WARN 으로 찍지 않는다 → 실패 1건 = 로그 1줄.
 */
public class ListenerErrorHandler extends ConditionalRejectingErrorHandler {

  @Override
  protected void log(Throwable t) {
    if (causeChainContainsARADRE(t)) {
      logger.debug("이미 기록한 리스너 실패 (ListenerFailureRecoverer)", t);
      return;
    }
    super.log(t);   // 그 밖의 실패는 기본대로 WARN
  }
}