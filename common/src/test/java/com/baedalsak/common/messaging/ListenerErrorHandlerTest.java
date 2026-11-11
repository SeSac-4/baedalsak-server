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

/** 리스너 에러 처리기 (#186): recoverer 가 이미 남긴 실패는 다시 안 찍고, 나머지는 기본대로 */
@ExtendWith(OutputCaptureExtension.class)
class ListenerErrorHandlerTest {

  private static final String DEFAULT_WARN = "Execution of Rabbit message listener failed";

  private final ListenerErrorHandler handler = new ListenerErrorHandler();
  private final Message message = new Message(new byte[0], new MessageProperties());

  @Test
  void recoverer_가_던진_실패는_WARN_을_다시_찍지_않음(CapturedOutput output) {
    handler.handleError(new ListenerExecutionFailedException("리스너 실패 → DLQ",
        new AmqpRejectAndDontRequeueException(new IllegalStateException("SNS 503")), message));

    assertThat(output.getOut()).doesNotContain(DEFAULT_WARN);
  }

  @Test
  void 그_밖의_실패는_기본대로_WARN_과_DLQ_처리(CapturedOutput output) {
    // 재시도 묶음을 거치지 않은 변환 실패 → 기본 처리기처럼 경고 후 requeue 금지(DLQ)
    assertThatThrownBy(() -> handler.handleError(new ListenerExecutionFailedException("변환 실패",
        new MessageConversionException("bad json"), message)))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);

    assertThat(output.getOut()).contains(DEFAULT_WARN);
  }
}
