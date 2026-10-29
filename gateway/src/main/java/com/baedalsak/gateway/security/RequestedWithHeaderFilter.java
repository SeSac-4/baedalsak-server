package com.baedalsak.gateway.security;

import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * CSRF 방어 (#23 8단계, 설계서 v6: 상태 변경 요청은 X-Requested-With 필수).
 * 다른 사이트는 이 헤더를 붙여 우리 API 를 부를 수 없다(브라우저 CORS 사전 확인에서 막힘).
 * 헤더가 없으면 403, 본문 없음. 값은 따지지 않고 있는지만 본다(프론트는 XMLHttpRequest).
 */
public class RequestedWithHeaderFilter implements WebFilter {

  static final String HEADER = "X-Requested-With";
  private static final Set<HttpMethod> STATE_CHANGING =
      Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    if (STATE_CHANGING.contains(request.getMethod())
        && !StringUtils.hasText(request.getHeaders().getFirst(HEADER))) {
      exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
      return exchange.getResponse().setComplete();   // 응답을 바로 끝냄 → 뒤 필터·서비스로 안 감
    }
    return chain.filter(exchange);
  }
}