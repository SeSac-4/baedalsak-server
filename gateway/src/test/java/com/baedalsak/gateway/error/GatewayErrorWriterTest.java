package com.baedalsak.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.gateway.trace.TraceIdHeaderFilter;

import tools.jackson.databind.json.JsonMapper;

class GatewayErrorWriterTest {

  private final GatewayErrorWriter writer = new GatewayErrorWriter(JsonMapper.builder().build());

  @Test
  void 공통_에러_포맷으로_본문을_쓴다() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/members/me"));
    exchange.getAttributes().put(TraceIdHeaderFilter.ATTR, "abc123");

    writer.write(exchange, CommonErrorCode.UNAUTHORIZED).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(exchange.getResponse().getBodyAsString().block())
        .isEqualTo("{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\",\"traceId\":\"abc123\"}");
  }

  @Test
  void 이미_응답이_시작됐으면_아무것도_안_쓴다() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
    exchange.getResponse().setComplete().block();

    writer.write(exchange, CommonErrorCode.FORBIDDEN).block();

    assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
  }
}