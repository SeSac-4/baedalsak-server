package com.baedalsak.member.sms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SNS 는 국제 형식(+82...)만 받는다 (#164) */
class SnsSmsSenderTest {

  @Test
  void 하이픈_있는_번호를_국제_형식으로() {
    assertThat(SnsSmsSender.toE164("010-1234-5678")).isEqualTo("+821012345678");
  }

  @Test
  void 하이픈_없는_번호도_같은_결과() {
    assertThat(SnsSmsSender.toE164("01012345678")).isEqualTo("+821012345678");
  }
}
