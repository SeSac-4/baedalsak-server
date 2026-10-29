package com.baedalsak.payment.mail;

import java.util.Map;

/**
 * 템플릿 메일 발송. local 프로필은 로그만(LoggingMailSender), 그 외는 SES(SesMailSender).
 * 동기 호출 — 발송이 끝나야 리턴한다 (@Async 사용 금지, docs 10-2).
 */
public interface MailSender {

  /**
   * @throws PermanentMailFailureException 잘못된 주소 등 다시 보내도 실패하는 오류 (바로 DLQ)
   *                                       그 외 예외는 일시 오류로 보고 재시도
   */
  void send(String to, String templateName, Map<String, String> templateData);
}
