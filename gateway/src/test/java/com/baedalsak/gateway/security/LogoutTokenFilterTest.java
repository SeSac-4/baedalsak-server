package com.baedalsak.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/** 로그아웃 토큰 필터 (#111): 유효한 AT 면 jti·만료 시각 전달, 클라이언트가 보낸 X-Token-* 는 삭제 */
class LogoutTokenFilterTest {

  private static final String LOGOUT = "/api/v1/auth/logout";

  private final ReactiveJwtDecoder jwtDecoder = mock(ReactiveJwtDecoder.class);
  private final GatewayFilterChain chain = mock(GatewayFilterChain.class);
  private final LogoutTokenFilter filter = new LogoutTokenFilter(jwtDecoder);

  @BeforeEach
  void setUp() {
    when(chain.filter(any())).thenReturn(Mono.empty());
  }

  @Test
  void 유효한_AT로_로그아웃하면_jti와_만료_시각을_전달() {
    Instant expiresAt = Instant.ofEpochSecond(2_000_000_000L);
    Jwt jwt = Jwt.withTokenValue("good").header("alg", "RS256")
        .claim("jti", "jti-1").expiresAt(expiresAt).build();
    when(jwtDecoder.decode("good")).thenReturn(Mono.just(jwt));

    HttpHeaders headers = forwardedHeaders(MockServerHttpRequest.post(LOGOUT)
        .cookie(new HttpCookie(CookieBearerTokenConverter.ACCESS_COOKIE, "good")));

    assertThat(headers.getFirst(LogoutTokenFilter.TOKEN_ID_HEADER)).isEqualTo("jti-1");
    assertThat(headers.getFirst(LogoutTokenFilter.TOKEN_EXPIRES_HEADER)).isEqualTo("2000000000");
  }

  @Test
  void 만료_위조_AT면_헤더_없이_통과() {
    when(jwtDecoder.decode("bad")).thenReturn(Mono.error(new BadJwtException("expired")));

    HttpHeaders headers = forwardedHeaders(MockServerHttpRequest.post(LOGOUT)
        .cookie(new HttpCookie(CookieBearerTokenConverter.ACCESS_COOKIE, "bad"))
        .header(LogoutTokenFilter.TOKEN_ID_HEADER, "forged"));

    assertThat(headers.containsHeader(LogoutTokenFilter.TOKEN_ID_HEADER)).isFalse();
    assertThat(headers.containsHeader(LogoutTokenFilter.TOKEN_EXPIRES_HEADER)).isFalse();
  }

  @Test
  void 다른_경로에서_보낸_X_Token_헤더는_지우고_토큰은_검사하지_않음() {
    HttpHeaders headers = forwardedHeaders(MockServerHttpRequest.get("/api/v1/members/me")
        .cookie(new HttpCookie(CookieBearerTokenConverter.ACCESS_COOKIE, "good"))
        .header(LogoutTokenFilter.TOKEN_ID_HEADER, "forged")
        .header(LogoutTokenFilter.TOKEN_EXPIRES_HEADER, "9999999999"));

    assertThat(headers.containsHeader(LogoutTokenFilter.TOKEN_ID_HEADER)).isFalse();
    assertThat(headers.containsHeader(LogoutTokenFilter.TOKEN_EXPIRES_HEADER)).isFalse();
    verify(jwtDecoder, never()).decode(anyString());
  }

  // 필터를 통과시킨 뒤, 다음 단계(서비스)로 넘어간 요청의 헤더를 꺼낸다
  private HttpHeaders forwardedHeaders(MockServerHttpRequest.BaseBuilder<?> request) {
    filter.filter(MockServerWebExchange.from(request), chain).block();

    ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
    verify(chain).filter(captor.capture());
    return captor.getValue().getRequest().getHeaders();
  }
}
