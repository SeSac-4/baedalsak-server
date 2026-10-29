package com.baedalsak.payment.mail;

/**
 * 다시 보내도 실패하는 메일 오류 (잘못된 이메일 주소, 템플릿 없음·변수 누락 등). 리스너가 재시도 없이 바로 DLQ 로 보낸다.
 */
public class PermanentMailFailureException extends RuntimeException {

  public PermanentMailFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
