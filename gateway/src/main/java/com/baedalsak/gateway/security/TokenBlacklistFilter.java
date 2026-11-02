package com.baedalsak.gateway.security;

import java.time.Duration;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 로그아웃한 AT 차단 (#111). 검증된 AT 의 jti 가 blacklist:{jti} 에 있으면 401.
 * 등록은 member 가 로그아웃 때 한다 (TTL = AT 남은 수명).
 * Redis 장애·지연이면 통과(fail-open) — 블랙리스트 때문에 전체 API 가 멈추지 않게.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistFilter implements GlobalFilter, Ordered {

  // member TokenBlacklistStore 와 같은 접두사여야 한다
  private static final String KEY_PREFIX = "blacklist:";
  private static final Duration TIMEOUT = Duration.ofMillis(100);

  private final ReactiveStringRedisTemplate redis;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return exchange.getPrincipal()
        .filter(JwtAuthenticationToken.class::isInstance)               // 익명이면 빈 값 → 조회 안 함
        .mapNotNull(auth -> ((JwtAuthenticationToken) auth).getToken().getId())
        .flatMap(this::isBlacklisted)
        .defaultIfEmpty(false)
        .flatMap(blocked -> blocked ? reject(exchange) : chain.filter(exchange));
  }

  private Mono<Boolean> isBlacklisted(String tokenId) {
    return redis.hasKey(KEY_PREFIX + tokenId)
        .timeout(TIMEOUT)
        .onErrorResume(e -> {
          log.warn("토큰 블랙리스트 조회 실패 — 통과 처리 (fail-open): {}", e.toString());
          return Mono.just(false);
        });
  }

  private Mono<Void> reject(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();   // 서비스로 보내지 않고 바로 응답
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 2;   // 회원 헤더·로그아웃 필터 다음, 라우팅 전
  }
}