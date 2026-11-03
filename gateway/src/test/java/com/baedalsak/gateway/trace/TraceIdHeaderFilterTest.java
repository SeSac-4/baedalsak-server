package com.baedalsak.gateway.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

class TraceIdHeaderFilterTest {

  private final Tracer tracer = mock(Tracer.class);
  private final TraceIdHeaderFilter filter = new TraceIdHeaderFilter(tracer);
  private final MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
  private final WebFilterChain chain = ex -> ex.getResponse().setComplete();   // 응답을 끝내 beforeCommit 실행

  @Test
  void span이_있으면_헤더와_속성에_traceId를_넣는다() {
    Span span = mock(Span.class);
    TraceContext context = mock(TraceContext.class);
    when(tracer.currentSpan()).thenReturn(span);
    when(span.context()).thenReturn(context);
    when(context.traceId()).thenReturn("abc123");

    filter.filter(exchange, chain).block();

    assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdHeaderFilter.HEADER)).isEqualTo("abc123");
    assertThat((String) exchange.getAttribute(TraceIdHeaderFilter.ATTR)).isEqualTo("abc123");
  }

  @Test
  void span이_없으면_헤더를_넣지_않는다() {
    when(tracer.currentSpan()).thenReturn(null);

    filter.filter(exchange, chain).block();

    assertThat(exchange.getResponse().getHeaders().containsHeader(TraceIdHeaderFilter.HEADER)).isFalse();
  }
}