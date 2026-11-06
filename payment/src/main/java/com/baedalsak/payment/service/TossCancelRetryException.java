package com.baedalsak.payment.service;

/**
 * 토스 취소 결과를 모름 · 잠깐의 문제 (#127). 리스너 컨테이너가 같은 메시지를 다시 처리하고(최대 3회 시도),
 * 다시 처리할 때는 환불 처리 중 + 진행 중 취소 행의 같은 멱등 키로 이어서 부른다. 다 써도 실패하면 DLQ (PENDING 유지).
 */
public class TossCancelRetryException extends RuntimeException {

  public TossCancelRetryException(String message, Throwable cause) {
    super(message, cause);
  }
}
