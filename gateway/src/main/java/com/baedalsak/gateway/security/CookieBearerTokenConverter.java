package com.baedalsak.gateway.security;

import org.springframework.http.HttpCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 쿠키 __Host-access 에서 액세스 토큰을 꺼내 JWT 검사로 넘긴다 (#23).
 * Spring 기본은 Authorization 헤더만 보는데, 우리 토큰은 HttpOnly 쿠키로만 오기 때문.
 */
public class CookieBearerTokenConverter implements ServerAuthenticationConverter {

    // member AuthCookies.ACCESS 와 같은 이름이어야 한다
    static final String ACCESS_COOKIE = "__Host-access";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(ACCESS_COOKIE);
        if (cookie == null || cookie.getValue().isBlank()) {
            return Mono.empty();   // 토큰 없음 → 검사하지 않음 (들어가도 되는지는 authorizeExchange 가 판단)
        }
        // "이 문자열을 검사해 주세요" 상자 → JwtDecoder 가 서명·만료·iss 검사
        return Mono.just(new BearerTokenAuthenticationToken(cookie.getValue()));
    }
}