package com.baedalsak.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RateLimitKeyResolverTest {

  // yml 대신 값을 직접 넣어 만든다: 10, 10, 100, 뒤에서 2번째, 100ms
  private final RateLimitKeyResolver resolver =
      new RateLimitKeyResolver(new RateLimitProperties(true, 10, 10, 10, 100, 2, Duration.ofMillis(100)));

  @Test
  void 로그인한_회원은_회원_ID로() {
    // 가짜 AT: username 클레임만 있으면 됨 (서명 검증은 이 테스트 범위가 아님)
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("username", "1")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    MockServerWebExchange exchange = MockServerWebExchange.builder(MockServerHttpRequest.get("/api/v1/members/me"))
        .principal(new JwtAuthenticationToken(jwt))   // "Security 가 이 AT 로 로그인 확인함" 상태를 흉내
        .build();

    assertThat(resolver.memberOrIp(exchange).block()).isEqualTo("member:1");
  }

  @Test
  void 로그인_전에는_IP로() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/stores")
        .remoteAddress(new InetSocketAddress("10.0.0.5", 1234)));   // 접속한 주소

    assertThat(resolver.memberOrIp(exchange).block()).isEqualTo("ip:10.0.0.5");
  }

  @Test
  void X_Forwarded_For_는_뒤에서_두번째를_믿는다() {
    // 사용자가 꾸민 값(1.1.1.1) , CloudFront 가 붙인 실제 IP(2.2.2.2) , ALB 가 붙인 CloudFront IP(3.3.3.3)
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
        .header("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3")
        .remoteAddress(new InetSocketAddress("10.0.0.5", 1234)));

    assertThat(resolver.ip(exchange)).isEqualTo("ip:2.2.2.2");
  }
}