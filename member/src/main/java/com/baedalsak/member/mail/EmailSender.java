package com.baedalsak.member.mail;

/**
 * 메일 발송 (#207). 서비스는 이 인터페이스만 쓴다 → 로컬은 로그, AWS 는 SES.
 * 고르는 값: application.yml 의 email.sender (log | ses)
 */
public interface EmailSender {

  /** 글자만 있는 메일 한 통. 실패하면 BusinessException(503) */
  void send(String to, String subject, String body);
}
