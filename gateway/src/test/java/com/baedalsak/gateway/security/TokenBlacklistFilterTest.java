package com.baedalsak.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
// import 추가
import com.baedalsak.gateway.error.GatewayErrorWriter;
import tools.jackson.databind.json.JsonMapper;

import reactor.core.publisher.Mono;

/** 토큰 블랙리스트 필터 (#111): 등록된 jti 는 401, 그 외·Redis 장애는 통과 */
class TokenBlacklistFilterTest {

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final GatewayFilterChain chain = mock(GatewayFilterChain.class);
  private final TokenBlacklistFilter filter = new TokenBlacklistFilter(redis, new GatewayErrorWriter(JsonMapper.builder().build()));

  @BeforeEach
  void setUp() {
    when(chain.filter(any())).thenReturn(Mono.empty());
  }

  @Test
  void 블랙리스트에_있는_토큰은_401() {
    when(redis.hasKey("blacklist:jti-1")).thenReturn(Mono.just(true));
    ServerWebExchange exchange = loggedIn("jti-1");

    filter.filter(exchange, chain).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(chain, never()).filter(any());
  }

  @Test
  void 블랙리스트에_없는_토큰은_통과() {
    when(redis.hasKey("blacklist:jti-1")).thenReturn(Mono.just(false));

    filter.filter(loggedIn("jti-1"), chain).block();

    verify(chain).filter(any());
  }

  @Test
  void Redis_장애면_통과_fail_open() {
    when(redis.hasKey(anyString())).thenReturn(Mono.error(new RedisConnectionFailureException("down")));

    filter.filter(loggedIn("jti-1"), chain).block();

    verify(chain).filter(any());
  }

  @Test
  void 비로그인_요청은_Redis를_조회하지_않고_통과() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/categories"));

    filter.filter(exchange, chain).block();

    verify(redis, never()).hasKey(anyString());
    verify(chain).filter(any());
  }

  // JWT 경비원이 검증을 마친 상태를 흉내 낸다 (principal 에 JwtAuthenticationToken)
  private ServerWebExchange loggedIn(String tokenId) {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").claim("jti", tokenId).build();
    return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/members/me"))
        .mutate()
        .principal(Mono.just(new JwtAuthenticationToken(jwt)))
        .build();
  }
}
