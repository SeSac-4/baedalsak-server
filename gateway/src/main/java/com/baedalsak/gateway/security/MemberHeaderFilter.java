package com.baedalsak.gateway.security;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 서비스로 넘기기 전에 회원 헤더를 다시 만든다 (#23 6단계).
 *   ① 클라이언트가 보낸 X-Member-Id / X-Member-Role 은 무조건 삭제 (위조 차단)
 *   ② 검증된 AT 가 있으면 username → X-Member-Id, cognito:groups[0] → X-Member-Role
 * 헤더 이름은 common LoginMember 와 같아야 한다 (gateway 는 common 을 쓰지 않음).
 */
@Component
public class MemberHeaderFilter implements GlobalFilter, Ordered {

  static final String ID_HEADER = "X-Member-Id";
  static final String ROLE_HEADER = "X-Member-Role";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return exchange.getPrincipal()
        .filter(JwtAuthenticationToken.class::isInstance)          // 익명이면 여기서 빈 값
        .map(auth -> rewrite(exchange, ((JwtAuthenticationToken) auth).getToken()))
        .switchIfEmpty(Mono.fromSupplier(() -> rewrite(exchange, null)))  // 익명: 지우기만
        .flatMap(chain::filter);
  }

  private ServerWebExchange rewrite(ServerWebExchange exchange, Jwt jwt) {
    return exchange.mutate()
        .request(req -> req.headers(headers -> {
          headers.remove(ID_HEADER);
          headers.remove(ROLE_HEADER);
          if (jwt == null) {
            return;
          }
          headers.set(ID_HEADER, jwt.getClaimAsString("username"));  // B안: username = member_id
          List<String> groups = jwt.getClaimAsStringList("cognito:groups");
          if (groups != null && !groups.isEmpty()) {
            headers.set(ROLE_HEADER, groups.get(0));                 // CUSTOMER / OWNER / RIDER
          }
        }))
        .build();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;   // 라우팅(전달)보다 먼저
  }
}