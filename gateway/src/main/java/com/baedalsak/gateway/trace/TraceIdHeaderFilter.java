package com.baedalsak.gateway.trace;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * 모든 응답에 X-Trace-Id 헤더를 붙인다 (#120, traceId 적용 요청 3-5).
 * Security(-100)가 끊는 401·403 에도 붙도록 GlobalFilter 가 아니라 그보다 앞단 WebFilter 로 둔다.
 * gateway 에러 본문(GatewayErrorWriter)도 같은 값을 쓰도록 exchange 속성에 넣어 둔다.
 */
@Component
@RequiredArgsConstructor
public class TraceIdHeaderFilter implements WebFilter, Ordered {

  public static final String HEADER = "X-Trace-Id";
  public static final String ATTR = TraceIdHeaderFilter.class.getName() + ".traceId";

  private final Tracer tracer;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    Span span = tracer.currentSpan();
    if (span == null) {
      return chain.filter(exchange);   // 추적이 꺼져 있으면 헤더 없이 통과
    }
    String traceId = span.context().traceId();
    exchange.getAttributes().put(ATTR, traceId);
    // 응답 헤더가 나가기 직전에 실행 → 서비스 응답·gateway 에러 응답 모두에 붙음
    exchange.getResponse().beforeCommit(() -> Mono.fromRunnable(() ->
        exchange.getResponse().getHeaders().set(HEADER, traceId)));
    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return -200;   // Spring Security WebFilter(-100)보다 먼저
  }
}