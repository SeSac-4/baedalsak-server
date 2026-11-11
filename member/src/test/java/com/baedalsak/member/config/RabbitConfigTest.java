package com.baedalsak.member.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.support.converter.MessageConversionException;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;

/** 문자 리스너 재시도 규칙 (#182 · #186): 문자 장애는 재시도, 다시 해도 소용없는 오류는 바로 DLQ */
class RabbitConfigTest {

  @Test
  void 문자_발송_실패_503_은_재시도() {
    assertThat(RabbitConfig.isRetryable(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE))).isTrue();
  }

  @Test
  void 이벤트_ID_없음과_깨진_JSON_은_재시도_안_함() {
    assertThat(RabbitConfig.isRetryable(new AmqpRejectAndDontRequeueException("without messageId"))).isFalse();
    assertThat(RabbitConfig.isRetryable(new MessageConversionException("bad json"))).isFalse();
  }
}
