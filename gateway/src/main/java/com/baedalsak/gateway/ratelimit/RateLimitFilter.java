package com.baedalsak.gateway.ratelimit;

import java.time.Instant;
import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.gateway.error.GatewayErrorWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 요청 제한 (#24, 설계서 API 명세 429). 요청을 묶음으로 나눠 1분 단위로 센다.
 *   auth     : POST 로그인·가입           → IP 당 authPerIp
 *   payment  : POST 주문 생성·결제 확인    → 회원 당 paymentPerMember (설계서 v10 결제 경로)
 *   ai-image : POST 메뉴 사진 배경 제거    → 회원 당 aiImagePerMember (#130)
 *   api      : 그 밖                      → 회원(로그인 전은 IP) 당 defaultPerClient
 * 넘으면 서비스로 보내지 않고 429 TOO_MANY_REQUESTS + Retry-After(초).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

  private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;
  // StripPrefix 전에 돌기 때문에 경로에 /api 가 남아 있다
  private static final List<PathPattern> AUTH_PATHS = List.of(
      PARSER.parse("/api/v*/auth/login"),
      PARSER.parse("/api/v*/auth/signup/**"),
      PARSER.parse("/api/v*/auth/password/**"));   // 비밀번호 찾기·재설정 (#162): 메일 낭비·코드 대입 방지
  private static final List<PathPattern> PAYMENT_PATHS = List.of(
      PARSER.parse("/api/v*/orders"),                       // 주문 생성 = 결제 요청 생성 (v10)
      PARSER.parse("/api/v*/orders/*/payment/confirm"));    // 결제 확인 (v10)
  private static final List<PathPattern> AI_IMAGE_PATHS = List.of(
      PARSER.parse("/api/v*/images/background-removal"));

  private final RateLimitProperties properties;
  private final RateLimitKeyResolver keyResolver;
  private final RateLimitCounter counter;
  private final GatewayErrorWriter errorWriter;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.enabled()) {
      return chain.filter(exchange);   // 스위치가 꺼져 있으면 세지 않고 통과
    }

    ServerHttpRequest request = exchange.getRequest();
    PathContainer path = request.getPath().pathWithinApplication();
    boolean post = HttpMethod.POST.equals(request.getMethod());

    Mono<Boolean> allowed;
    if (post && matches(AUTH_PATHS, path)) {
      // 로그인 전이라 회원 ID 가 없음 → IP 로만 센다
      allowed = counter.tryAcquire("auth:" + keyResolver.ip(exchange), properties.authPerIp());
    } else if (post && matches(PAYMENT_PATHS, path)) {
      allowed = countByClient(exchange, "payment", properties.paymentPerMember());
    } else if (post && matches(AI_IMAGE_PATHS, path)) {
      allowed = countByClient(exchange, "ai-image", properties.aiImagePerMember());
    } else {
      allowed = countByClient(exchange, "api", properties.defaultPerClient());
    }
    return allowed.flatMap(ok -> ok ? chain.filter(exchange) : reject(exchange));
  }

  /** 회원이면 회원 ID, 로그인 전이면 IP 로 센다 */
  private Mono<Boolean> countByClient(ServerWebExchange exchange, String group, int limit) {
    return keyResolver.memberOrIp(exchange)
        .flatMap(client -> counter.tryAcquire(group + ":" + client, limit));
  }

  private static boolean matches(List<PathPattern> patterns, PathContainer path) {
    return patterns.stream().anyMatch(pattern -> pattern.matches(path));
  }

  private Mono<Void> reject(ServerWebExchange exchange) {
    // 다음 1분 칸이 시작될 때까지 남은 초 (1~60)
    long now = Instant.now().getEpochSecond();
    long retryAfter = RateLimitCounter.WINDOW_SECONDS - now % RateLimitCounter.WINDOW_SECONDS;
    exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
    log.warn("요청 제한 초과 method={} path={}", exchange.getRequest().getMethod(), exchange.getRequest().getPath());
    return errorWriter.write(exchange, CommonErrorCode.TOO_MANY_REQUESTS);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 3;   // 블랙리스트(+2) 다음, 라우팅 전 (차단된 토큰은 세지 않음)
  }
}