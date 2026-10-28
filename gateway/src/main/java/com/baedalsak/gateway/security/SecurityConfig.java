package com.baedalsak.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * gateway 보안 설정 (#23).
 * 1단계: 의존성만 넣으면 기본값이 모든 요청을 막으므로, 우선 전부 통과시킨다.
 * 다음 단계에서 JWT 검증·공개 경로·역할 검사를 차례로 추가한다.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // 쿠키 기반이지만 CSRF 는 X-Requested-With 검사로 직접 한다 (8단계)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            // 로그인 화면·Basic 인증·/logout 은 member 가 담당 → Security 기본 기능 끔
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            // JWT 경비원: 쿠키 __Host-access 에서 토큰을 꺼내(3단계) Cognito 공개키로 서명·만료·iss 검사(2단계)
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenConverter(new CookieBearerTokenConverter())
                .jwt(Customizer.withDefaults()))
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll())   // 1단계: 전부 통과 (2단계부터 조임)
            .build();
    }
}