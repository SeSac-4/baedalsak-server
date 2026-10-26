package com.baedalsak.order.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 실제 PostgreSQL(컨테이너)에 Flyway 를 적용하고 앱 전체를 띄우는 통합 테스트 기반.
 */
@SpringBootTest
@AutoConfigureMockMvc
// 발행기는 RabbitMQ 가 필요해서 기본은 끈다. 발행 테스트(OutboxPublisherTest)만 켠다
@TestPropertySource(properties = "outbox.publisher.enabled=false")
public abstract class IntegrationTest {

    // 테스트 클래스 전체가 컨테이너 1개를 공유한다
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // 운영과 같이 orders schema 를 쓴다. 로컬·운영은 schema 를 초기화 스크립트가 만들지만 테스트는 Flyway 가 만든다
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=orders");
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
        jdbcTemplate.execute("TRUNCATE orders.order_item, orders.orders, orders.cart_item, orders.cart, "
                + "orders.outbox, orders.processed_event, orders.mail_log RESTART IDENTITY");
    }
}
