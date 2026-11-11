package com.baedalsak.gateway.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

/**
 * gateway 경로별 역할 규칙 (#112 T47). 외부 API 전체 × (고객 · 사장님 · 라이더 · 비로그인).
 * 허용이면 gateway 를 통과해 꺼진 서비스까지 가서 503, 역할이 다르면 403, 토큰이 없으면 401.
 * 새 API 를 만들면 여기에 한 줄 추가 — 규칙을 빠뜨리면 이 테스트가 실패한다.
 */
@SpringBootTest(properties = {
    "COGNITO_ISSUER_URI=https://issuer.test",   // 진짜 Cognito 대신 아래 가짜 토큰 해석기를 씀
    "REDIS_SESSION_PASSWORD=test",
    "spring.data.redis.port=1",                 // Redis 없음 → 블랙리스트는 fail-open 으로 통과
    "rate-limit.enabled=false",
    // 서비스는 모두 아무도 안 듣는 주소 → 통과한 요청은 항상 503 (로컬에 서비스를 켜 둬도 결과가 같게)
    "STORE_SERVICE_URL=http://127.0.0.1:59999", "ORDER_SERVICE_URL=http://127.0.0.1:59999",
    "PAYMENT_SERVICE_URL=http://127.0.0.1:59999", "DELIVERY_SERVICE_URL=http://127.0.0.1:59999",
    "MEMBER_SERVICE_URL=http://127.0.0.1:59999", "AI_SERVICE_URL=http://127.0.0.1:59999"})
class RoleMatrixTest {

  private static final List<String> ROLES = List.of("CUSTOMER", "OWNER", "RIDER");

  /** 쿠키 값을 그대로 역할(Cognito 그룹)로 쓰는 가짜 토큰 해석기. 예) __Host-access=OWNER → 사장님 */
  @TestConfiguration
  static class FakeJwtConfig {
    @Bean
    @Primary
    ReactiveJwtDecoder fakeJwtDecoder() {
      return token -> Mono.just(Jwt.withTokenValue(token)
          .header("alg", "none")
          .claim("username", "1")
          .claim("token_use", "access")
          .claim("cognito:groups", List.of(token))
          .issuedAt(Instant.now())
          .expiresAt(Instant.now().plusSeconds(60))
          .build());
    }
  }

  @Autowired
  private ApplicationContext context;

  /**
   * allowed: 통과해야 하는 역할 (C=고객, O=사장님, R=라이더), PUBLIC = 로그인 없이도 통과
   */
  @ParameterizedTest(name = "{0} {1} → {2}")
  @CsvSource(delimiter = '|', textBlock = """
      POST   | /api/v1/auth/login                       | PUBLIC
      POST   | /api/v1/auth/signup/customer             | PUBLIC
      POST   | /api/v1/auth/signup/owner                | PUBLIC
      POST   | /api/v1/auth/signup/rider                | PUBLIC
      POST   | /api/v1/auth/refresh                     | PUBLIC
      POST   | /api/v1/auth/logout                      | PUBLIC
      GET    | /api/v1/members/me                       | C O R
      DELETE | /api/v1/members/me                       | C O R
      GET    | /api/v1/categories                       | C O R
      GET    | /api/v1/stores                           | C O R
      GET    | /api/v1/stores/summary                   | C O R
      GET    | /api/v1/stores/3                         | C O R
      GET    | /api/v1/stores/3/menus                   | C O R
      GET    | /api/v1/stores/me                        | O
      POST   | /api/v1/stores                           | O
      PUT    | /api/v1/stores/3                         | O
      PUT    | /api/v1/stores/3/business-hours          | O
      PATCH  | /api/v1/stores/3/status                  | O
      POST   | /api/v1/stores/3/images/upload-url       | O
      POST   | /api/v1/stores/3/menus                   | O
      PUT    | /api/v1/stores/3/menus/7                 | O
      DELETE | /api/v1/stores/3                         | O
      DELETE | /api/v1/stores/3/menus/7                 | O
      PATCH  | /api/v1/stores/3/menus/7/status          | O
      GET    | /api/v1/stores/3/orders                  | O
      POST   | /api/v1/images/background-removal        | O
      GET    | /api/v1/cart                             | C
      DELETE | /api/v1/cart                             | C
      POST   | /api/v1/cart/items                       | C
      PATCH  | /api/v1/cart/items/7                     | C
      DELETE | /api/v1/cart/items/7                     | C
      POST   | /api/v1/orders                           | C
      GET    | /api/v1/orders                           | C
      GET    | /api/v1/orders/5                         | C O
      POST   | /api/v1/orders/5/accept                  | O
      POST   | /api/v1/orders/5/reject                  | O
      POST   | /api/v1/orders/5/cancel                  | C
      POST   | /api/v1/orders/5/payment/confirm         | C
      GET    | /api/v1/payments                         | C
      GET    | /api/v1/payments/9                       | C
      GET    | /api/v1/payments/by-order/5              | C
      GET    | /api/v1/deliveries/waiting               | R
      GET    | /api/v1/deliveries/me/active             | R
      POST   | /api/v1/deliveries/8/accept              | R
      POST   | /api/v1/deliveries/8/pickup              | R
      POST   | /api/v1/deliveries/8/complete            | R
      PUT    | /api/v1/deliveries/8/location            | R
      GET    | /api/v1/deliveries/by-order/5            | C O
      GET    | /api/v1/deliveries/by-order/5/route      | C O R
      GET    | /api/v1/deliveries/by-order/5/location   | C O R
      """)
  void 경로별_역할_규칙(String method, String path, String allowed) {
    WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
    boolean isPublic = allowed.equals("PUBLIC");
    Set<String> allowedRoles = Set.of(allowed.split(" "));

    for (String role : ROLES) {
      boolean ok = isPublic || allowedRoles.contains(role.substring(0, 1));
      HttpStatus expected = ok ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.FORBIDDEN;
      request(client, method, path).cookie("__Host-access", role)
          .exchange().expectStatus().isEqualTo(expected);
    }
    // 토큰 없이: 공개 경로만 통과, 나머지는 401
    HttpStatus guest = isPublic ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED;
    request(client, method, path).exchange().expectStatus().isEqualTo(guest);
  }

  /** 상태 변경 요청은 CSRF 헤더가 필요하므로 모든 요청에 붙인다 (여기서는 역할만 본다) */
  private WebTestClient.RequestHeadersSpec<?> request(WebTestClient client, String method, String path) {
    return client.method(HttpMethod.valueOf(method)).uri(path).header("X-Requested-With", "XMLHttpRequest");
  }
}