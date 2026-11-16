package com.baedalsak.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * client_id 검사기 (#204). 우리 앱 클라이언트로 발급된 토큰만 통과해야 한다.
 */
class ClientIdValidatorTest {

  private final OAuth2TokenValidator<Jwt> validator = new SecurityConfig().clientIdValidator("our-client");

  @Test
  void 같은_client_id_면_통과() {
    assertThat(validator.validate(jwt("our-client")).hasErrors()).isFalse();
  }

  @Test
  void 다른_client_id_면_거절() {
    assertThat(validator.validate(jwt("other-client")).hasErrors()).isTrue();
  }

  @Test
  void client_id_가_없으면_거절() {
    assertThat(validator.validate(jwt(null)).hasErrors()).isTrue();
  }

  // client_id 가 null 이면 클레임 자체를 넣지 않는다
  private Jwt jwt(String clientId) {
    Jwt.Builder builder = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("token_use", "access")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60));
    if (clientId != null) {
      builder.claim("client_id", clientId);
    }
    return builder.build();
  }
}
