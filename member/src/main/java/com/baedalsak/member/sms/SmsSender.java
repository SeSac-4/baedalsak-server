package com.baedalsak.member.sms;

/**
 * 문자 발송 (#164). 서비스는 이 인터페이스만 쓴다 → 로컬은 로그, AWS 는 SNS.
 * 고르는 값: application.yml 의 sms.sender (log | sns)
 */
public interface SmsSender {

  /** phone: 010-1234-5678 또는 01012345678. 실패하면 BusinessException(503) */
  void send(String phone, String message);
}