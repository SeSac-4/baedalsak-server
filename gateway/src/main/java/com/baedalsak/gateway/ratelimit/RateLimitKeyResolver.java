package com.baedalsak.gateway.ratelimit;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 요청 제한을 "누구 단위로" 셀지 정한다 (#24).
 *   로그인한 요청 → member:{회원ID}
 *   로그인 전 요청 → ip:{사용자 IP}
 */
@Component
public class RateLimitKeyResolver {

  // X-Forwarded-For 헤더를 읽어 사용자 IP 를 꺼내 주는 Spring Cloud Gateway 부품
  private final RemoteAddressResolver ipResolver;

  public RateLimitKeyResolver(RateLimitProperties properties) {
    // 헤더 값 목록에서 "뒤에서 N번째"를 믿는다 (N = trusted-proxies)
    this.ipResolver = XForwardedRemoteAddressResolver.maxTrustedIndex(properties.trustedProxies());
  }

  /** 회원이면 회원 ID, 아니면 IP. 로그인 여부를 꺼내는 일이 비동기라 Mono 로 돌려준다 */
  public Mono<String> memberOrIp(ServerWebExchange exchange) {
    return exchange.getPrincipal()                                   // 로그인 정보 (없으면 빈 Mono)
        .filter(JwtAuthenticationToken.class::isInstance)            // 검증된 AT 로 로그인한 경우만
        .map(auth -> ((JwtAuthenticationToken) auth).getToken().getClaimAsString("username"))  // username = member_id (B안)
        .map(memberId -> "member:" + memberId)
        .switchIfEmpty(Mono.fromSupplier(() -> ip(exchange)));       // 위에서 아무것도 안 나왔으면 IP
  }

  /** 사용자 IP. 로그인·가입은 로그인 여부와 상관없이 항상 이걸 쓴다 */
  public String ip(ServerWebExchange exchange) {
    InetSocketAddress address = ipResolver.resolve(exchange);
    String host = address == null ? null : address.getHostString();  // "2.2.2.2" 같은 IP 글자
    return "ip:" + (host == null || host.isBlank() ? "unknown" : host);
  }
}