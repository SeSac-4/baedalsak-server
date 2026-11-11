package com.baedalsak.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * 블랙리스트에 올릴 AT 정보를 member 에 전달한다 (#111 로그아웃, #188 회원 탈퇴).
 *   ① 클라이언트가 보낸 X-Token-* 는 무조건 삭제 (위조 차단)
 *   ② 로그아웃 · 탈퇴 요청의 AT 가 유효하면 jti·만료 시각을 헤더로 전달
 * /auth/** 는 토큰 검사를 건너뛰므로(CookieBearerTokenConverter) 여기서 직접 검증한다.
 * AT 가 없거나 만료·위조면 헤더 없이 통과 — 로그아웃은 항상 성공해야 한다.
 */
@Component
@RequiredArgsConstructor
public class LogoutTokenFilter implements GlobalFilter, Ordered {

  static final String TOKEN_ID_HEADER = "X-Token-Id";
  static final String TOKEN_EXPIRES_HEADER = "X-Token-Expires-At";

  private static final PathPattern LOGOUT_PATH =
      PathPatternParser.defaultInstance.parse("/api/v*/auth/logout");
  private static final PathPattern WITHDRAW_PATH =
      PathPatternParser.defaultInstance.parse("/api/v*/members/me");   // DELETE 만 (회원 탈퇴, #188)

  private final ReactiveJwtDecoder jwtDecoder;   // JWT 경비원과 같은 검사(서명·만료·iss·token_use)

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerWebExchange cleaned = rewrite(exchange, null);
    HttpCookie cookie = exchange.getRequest().getCookies()
        .getFirst(CookieBearerTokenConverter.ACCESS_COOKIE);

    if (!needsToken(exchange) || cookie == null || cookie.getValue().isBlank()) {
      return chain.filter(cleaned);
    }

    return jwtDecoder.decode(cookie.getValue())
        .map(jwt -> rewrite(exchange, jwt))
        .onErrorResume(e -> Mono.just(cleaned))   // 만료·위조·JWKS 장애: 헤더 없이 통과
        .flatMap(chain::filter);
  }

  // 지금 AT 를 블랙리스트에 올리는 요청: 로그아웃, 회원 탈퇴(DELETE /members/me, #188)
  private boolean needsToken(ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest();
    PathContainer path = request.getPath().pathWithinApplication();
    return LOGOUT_PATH.matches(path)
        || (HttpMethod.DELETE.equals(request.getMethod()) && WITHDRAW_PATH.matches(path));
  }

  private ServerWebExchange rewrite(ServerWebExchange exchange, Jwt jwt) {
    return exchange.mutate()
        .request(req -> req.headers(headers -> {
          headers.remove(TOKEN_ID_HEADER);
          headers.remove(TOKEN_EXPIRES_HEADER);
          if (jwt == null) {
            return;
          }
          headers.set(TOKEN_ID_HEADER, jwt.getId());   // getId() = jti 클레임
          headers.set(TOKEN_EXPIRES_HEADER, String.valueOf(jwt.getExpiresAt().getEpochSecond()));
        }))
        .build();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;   // StripPrefix 전: 경로에 /api 가 남아 있을 때 검사
  }
}