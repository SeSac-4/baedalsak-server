package com.baedalsak.gateway.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.baedalsak.common.error.ErrorCode;
import com.baedalsak.common.error.ErrorResponse;
import com.baedalsak.gateway.trace.TraceIdHeaderFilter;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * gateway 가 직접 내보내는 에러(401·403·404·5xx)를 공통 에러 포맷 {code, message, traceId} 로 쓴다 (#112 T43).
 * gateway 는 WebFlux 라 common 의 MVC 핸들러(GlobalExceptionHandler)를 쓸 수 없어 따로 둔다.
 * traceId 는 TraceIdHeaderFilter 가 넣어 둔 값 → X-Trace-Id 헤더와 항상 같다.
 */
@Component
@RequiredArgsConstructor
public class GatewayErrorWriter {

  private final JsonMapper jsonMapper;

  public Mono<Void> write(ServerWebExchange exchange, ErrorCode code) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();   // 이미 응답이 나가기 시작했으면 덮어쓸 수 없음
    }
    String traceId = exchange.getAttribute(TraceIdHeaderFilter.ATTR);
    byte[] body = jsonMapper.writeValueAsBytes(ErrorResponse.of(code, traceId));
    response.setStatusCode(HttpStatusCode.valueOf(code.getStatus()));
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
  }
}