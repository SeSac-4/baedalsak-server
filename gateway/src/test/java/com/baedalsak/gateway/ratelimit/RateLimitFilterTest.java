package com.baedalsak.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.baedalsak.gateway.error.GatewayErrorWriter;
import tools.jackson.databind.json.JsonMapper;

import reactor.core.publisher.Mono;

/** 요청 제한 필터 (#24): 경로별 묶음·한도, 초과 시 429 + Retry-After, 스위치 꺼짐이면 통과 */
class RateLimitFilterTest {

  // 묶음마다 한도를 다르게 줘서 어느 한도로 셌는지 구분한다: auth 11, payment 12, ai-image 13, api 100
  private static final RateLimitProperties ON = new RateLimitProperties(true, 11, 12, 13, 100, 2, Duration.ofMillis(100));

  private final RateLimitCounter counter = mock(RateLimitCounter.class);
  private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

  @BeforeEach
  void setUp() {
    when(chain.filter(any())).thenReturn(Mono.empty());
    when(counter.tryAcquire(anyString(), anyInt())).thenReturn(Mono.just(true));
  }

  private RateLimitFilter filter(RateLimitProperties properties) {
    return new RateLimitFilter(properties, new RateLimitKeyResolver(properties), counter,
        new GatewayErrorWriter(JsonMapper.builder().build()));
  }

  /** 회원 1 로 로그인한 요청 */
  private MockServerWebExchange member(MockServerHttpRequest.BaseBuilder<?> request) {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("username", "1")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    return MockServerWebExchange.builder(request.build()).principal(new JwtAuthenticationToken(jwt)).build();
  }

  /** 로그인 전 요청 (IP 10.0.0.5) */
  private MockServerWebExchange guest(MockServerHttpRequest.BaseBuilder<?> request) {
    return MockServerWebExchange.from(request.remoteAddress(new InetSocketAddress("10.0.0.5", 1234)).build());
  }

  @Test
  void 로그인은_IP로_auth_한도() {
    filter(ON).filter(guest(MockServerHttpRequest.post("/api/v1/auth/login")), chain).block();

    verify(counter).tryAcquire("auth:ip:10.0.0.5", 11);
    verify(chain).filter(any());
  }

  @Test
  void 비밀번호_찾기와_재설정도_IP로_auth_한도() {
    filter(ON).filter(guest(MockServerHttpRequest.post("/api/v1/auth/password/forgot")), chain).block();
    filter(ON).filter(guest(MockServerHttpRequest.post("/api/v1/auth/password/reset")), chain).block();

    verify(counter, times(2)).tryAcquire("auth:ip:10.0.0.5", 11);
  }

  @Test
  void 가입_전_휴대폰_인증도_IP로_auth_한도() {
    filter(ON).filter(guest(MockServerHttpRequest.post("/api/v1/auth/phone/code")), chain).block();
    filter(ON).filter(guest(MockServerHttpRequest.post("/api/v1/auth/phone/verify")), chain).block();

    verify(counter, times(2)).tryAcquire("auth:ip:10.0.0.5", 11);
  }

  @Test
  void 주문_생성과_결제_확인은_회원으로_payment_한도() {
    filter(ON).filter(member(MockServerHttpRequest.post("/api/v1/orders")), chain).block();
    filter(ON).filter(member(MockServerHttpRequest.post("/api/v1/orders/5/payment/confirm")), chain).block();

    verify(counter, times(2)).tryAcquire("payment:member:1", 12);
  }

  @Test
  void 배경_제거는_회원으로_ai_image_한도() {
    filter(ON).filter(member(MockServerHttpRequest.post("/api/v1/images/background-removal")), chain).block();

    verify(counter).tryAcquire("ai-image:member:1", 13);
  }

  @Test
  void 주문_목록_조회는_GET이라_일반_한도() {
    filter(ON).filter(member(MockServerHttpRequest.get("/api/v1/orders")), chain).block();

    verify(counter).tryAcquire("api:member:1", 100);
  }

  @Test
  void 한도를_넘으면_429와_Retry_After() {
    when(counter.tryAcquire(anyString(), anyInt())).thenReturn(Mono.just(false));
    MockServerWebExchange exchange = member(MockServerHttpRequest.get("/api/v1/members/me"));

    filter(ON).filter(exchange, chain).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    long retryAfter = Long.parseLong(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    assertThat(retryAfter).isBetween(1L, 60L);
    verify(chain, never()).filter(any());
  }

  @Test
  void 스위치가_꺼져_있으면_세지_않고_통과() {
    RateLimitProperties off = new RateLimitProperties(false, 11, 12, 13, 100, 2, Duration.ofMillis(100));

    filter(off).filter(guest(MockServerHttpRequest.post("/api/v1/auth/login")), chain).block();

    verify(counter, never()).tryAcquire(anyString(), anyInt());
    verify(chain).filter(any());
  }
}