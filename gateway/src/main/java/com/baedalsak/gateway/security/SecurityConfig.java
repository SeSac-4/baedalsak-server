package com.baedalsak.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;

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
            //커스텀 헤더 존재 검사
            .addFilterAfter(new RequestedWithHeaderFilter(), SecurityWebFiltersOrder.CSRF)
            // 로그인 화면·Basic 인증·/logout 은 member 가 담당 → Security 기본 기능 끔
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            // JWT 경비원: 쿠키 __Host-access 에서 토큰을 꺼내(3단계) Cognito 공개키로 서명·만료·iss 검사(2단계)
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenConverter(new CookieBearerTokenConverter())
                .jwt(Customizer.withDefaults()))
            .authorizeExchange(exchanges -> exchanges
                // 운영 상태 확인: AWS 는 8081 별도 포트지만 같은 보안 필터를 지난다 → 막으면 probe 실패로 파드 재시작
                .pathMatchers("/actuator/**").permitAll()
                // 인증 창구: 가입 3종·로그인·갱신·로그아웃 (설계서 API 명세 '인증 불필요' / 로그아웃 '세션 쿠키')
                .pathMatchers(HttpMethod.POST, "/api/v*/auth/**").permitAll()

                // ── 사장님 (위에서부터 처음 맞는 규칙 적용 → 공개 규칙 /stores/* 보다 먼저) ──
                .pathMatchers(HttpMethod.GET, "/api/v*/stores/me", "/api/v*/stores/*/orders").hasRole("OWNER")
                .pathMatchers(HttpMethod.POST, "/api/v*/stores", "/api/v*/stores/*/menus", "/api/v*/orders/*/accept").hasRole("OWNER")
                .pathMatchers(HttpMethod.PUT, "/api/v*/stores/**").hasRole("OWNER")     // 가게 수정·영업시간·메뉴 수정
                .pathMatchers(HttpMethod.PATCH, "/api/v*/stores/**").hasRole("OWNER")   // 운영 상태·품절
                .pathMatchers(HttpMethod.DELETE, "/api/v*/stores/**").hasRole("OWNER")  // 메뉴 삭제

                // 가게·메뉴 둘러보기 (설계서 API 명세 '인증 불필요')
                .pathMatchers(HttpMethod.GET,
                    "/api/v*/categories",
                    "/api/v*/stores",
                    "/api/v*/stores/*",
                    "/api/v*/stores/*/menus").permitAll()

                // ── 고객·사장님 공용: 주문 상세, 주문의 배달 정보 (소유권은 각 서비스가 확인) ──
                .pathMatchers(HttpMethod.GET, "/api/v*/orders/*", "/api/v*/deliveries/by-order/*").hasAnyRole("CUSTOMER", "OWNER")

                // ── 고객: 장바구니, 주문 생성·내 주문 목록, 결제 ──
                .pathMatchers("/api/v*/cart/**", "/api/v*/payments/**").hasRole("CUSTOMER")
                .pathMatchers(HttpMethod.POST, "/api/v*/orders").hasRole("CUSTOMER")
                .pathMatchers(HttpMethod.GET, "/api/v*/orders").hasRole("CUSTOMER")

                // ── 라이더: 배차 대기·수락·픽업·완료·진행 중 (by-order 는 위에서 먼저 처리됨) ──
                .pathMatchers("/api/v*/deliveries/**").hasRole("RIDER")

                // 나머지(/members/me 등)는 로그인만 하면 됨 — 토큰 없으면 401
                .anyExchange().authenticated())
            .build();
    }
        /**
     * Cognito 는 access·id 토큰을 같은 키로 서명하므로 서명 검사만으로는 구분이 안 된다.
     * token_use 가 "access" 인 토큰만 받는다 (id 토큰으로 API 호출 차단).
     * Boot 가 이 타입의 Bean 을 JwtDecoder 검사 목록에 자동으로 추가한다.
     */
    @Bean
    OAuth2TokenValidator<Jwt> accessTokenOnlyValidator() {
        return new JwtClaimValidator<String>("token_use", "access"::equals);
    }
}