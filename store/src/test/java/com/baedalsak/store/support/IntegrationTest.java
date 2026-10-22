package com.baedalsak.store.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 실제 PostgreSQL(컨테이너)에 Flyway 를 적용하고 MockMvc 로 API 를 호출하는 통합 테스트 기반.
 * 현재 시각은 2026-10-22(목) 12:00 KST 로 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTest.FixedClockConfig.class)
public abstract class IntegrationTest {

    public static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneId.of("Asia/Seoul"));

    // 테스트 클래스 전체가 컨테이너 1개를 공유한다
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // 운영과 같이 store schema 를 쓴다. 로컬·운영은 schema 를 초기화 스크립트가 만들지만 테스트는 Flyway 가 만든다
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=store");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.create-schemas", () -> "true");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE store.menu, store.business_hour, store.store RESTART IDENTITY");
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.toInstant(), NOW.getZone());
        }
    }
}
