package com.baedalsak.member.seed;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.auth.CognitoTokens;
import com.baedalsak.member.domain.Role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * seed 계정 6명을 Cognito 풀에 만든다 (DB seed R__member_seed.sql 과 같은 id·이메일).
 * 실행: SPRING_PROFILES_ACTIVE=local,cognito-seed — 끝나면 앱이 종료된다.
 * 이미 로그인되는 계정은 건너뛰므로 여러 번 실행해도 안전하다.
 */
@Slf4j
@Component
@Profile("cognito-seed")
@RequiredArgsConstructor
public class CognitoSeedRunner implements ApplicationRunner {

  private record SeedAccount(long id, String email, Role role) {
  }

  private static final List<SeedAccount> ACCOUNTS = List.of(
      new SeedAccount(1, "seed-customer1@baedalsak.click", Role.CUSTOMER),
      new SeedAccount(2, "seed-customer2@baedalsak.click", Role.CUSTOMER),
      new SeedAccount(101, "seed-owner1@baedalsak.click", Role.OWNER),
      new SeedAccount(102, "seed-owner2@baedalsak.click", Role.OWNER),
      new SeedAccount(201, "seed-rider1@baedalsak.click", Role.RIDER),
      new SeedAccount(202, "seed-rider2@baedalsak.click", Role.RIDER));

  private final CognitoAuthClient cognitoAuthClient;
  private final ConfigurableApplicationContext context;

  @Value("${SEED_PASSWORD}")
  private String seedPassword;

  @Override
  public void run(ApplicationArguments args) {
    for (SeedAccount a : ACCOUNTS) {
      if (canLogin(a)) {
        log.info("[seed] 이미 있음 id={} email={}", a.id(), a.email());
        continue;
      }
      cognitoAuthClient.createUser(a.id(), a.email(), seedPassword, a.role());
      CognitoTokens tokens = cognitoAuthClient.login(a.email(), seedPassword);
      log.info("[seed] 생성 id={} 토큰 클레임={}", a.id(), claims(tokens.accessToken()));
    }
    log.info("[seed] 완료 — 앱을 종료합니다");
    System.exit(SpringApplication.exit(context, () -> 0));
  }

  private boolean canLogin(SeedAccount a) {
    try {
      cognitoAuthClient.login(a.email(), seedPassword);
      return true;
    } catch (BusinessException e) {
      if (e.getErrorCode() == CommonErrorCode.UNAUTHORIZED) {
        return false;   // 사용자가 없음 → 만들면 됨
      }
      throw e;          // 503 등은 그대로 실패
    }
  }

  // access 토큰 가운데 조각(payload)만 디코딩 — username·cognito:groups 확인용 (서명·토큰 원문은 남기지 않음)
  private String claims(String accessToken) {
    String payload = accessToken.split("\\.")[1];
    return new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
  }
}