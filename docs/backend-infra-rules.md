# baedalsak-server 백엔드 ↔ 인프라 연동 규칙

> 이 문서는 `Dockerfile`, `compose.yaml`, 이후 CI(GitHub Actions)·Kubernetes 배포가 **서비스 코드와 맞물리기 위한 최소 규칙**입니다.
> 서비스 내부 설계(패키지 구조, API 등)는 자유롭게 하되, 아래 항목만 지켜 주세요.
> 규칙을 바꿔야 하면 인프라 담당과 먼저 이야기해 주세요. Dockerfile·compose·배포 설정이 함께 바뀝니다.

- 작성: 인프라(백엔드 A) · 2026-10-13
- v3 수정 2026-10-16: Spring Boot 4, 서비스 6개(user → **member**, notification 제거), RDS 인스턴스 1개 + **서비스별 DB 5개**, RabbitMQ 4.2 + vhost·TLS·exchange·발행 확인, Flyway(Boot 4 starter), JPA, Outbox, 분산 추적(Zipkin), JSON 로그, KST, 비밀값 이름, `*_SERVICE_URL`, `/api/v*` 라우팅, DynamoDB 주석 처리(9-1)
- 2026-10-16 추가: 관측성(Zipkin·Prometheus·Grafana·Fluent Bit)은 **후순위 — 주석 처리** (9-2). traceId 로그 추적은 유지
- 2026-10-22 수정: 메일 발송을 `@Async` 직접 호출 → **자기 메일 큐 + 동기 리스너**로 확정 (10번), 메일 큐 이름 규칙 추가 (3-1)
- 2026-10-22 수정: Outbox 발행기·이벤트 소비자는 공통 모듈이 아니라 **각 서비스에서 구현**으로 확정, 서비스 간 통일 규칙 추가 (3-3)
- 2026-10-23 수정: 설계서 v4 반영 — 큐·DLQ quorum 선언(3-1), 메일 수신자 `customerEmail`·`ownerEmail` 스냅샷·메일 큐 `deliveryLimit`(10-4), AWS 권한 IRSA → Pod Identity 표기, 공통 모듈은 추후 생성(3-3)
- 관련 파일: `Dockerfile`, `.dockerignore`, `compose.yaml`, `.env.example`, `docker/local/postgres/init/`
- 동작 예시: `smoke-test/` (이 규칙대로 만든 검증용 6개 모듈, 저장소 미포함)

---

## 1. 저장소 구조

| 규칙 | 이유 |
|---|---|
| `gradlew`, `settings.gradle`은 **저장소 루트**에 둔다 | Dockerfile은 루트에서 빌드합니다. 공통 모듈을 함께 컴파일해야 하기 때문입니다. |
| **모듈 이름 = 폴더 경로**로 둔다 (`include 'store'` 또는 `include 'services:store'`) | Dockerfile이 폴더 경로(`services/store`)를 Gradle 경로(`:services:store`)로 바꿔 빌드합니다. `projectDir`로 이름을 따로 바꾸면 빌드가 실패합니다. |
| Spring Boot 플러그인(`org.springframework.boot`)은 **실행할 서비스 모듈에만** 적용한다 | 공통 모듈(`common` 등)에 적용하면 main 클래스가 없어 `bootJar`가 실패합니다. 공통 모듈은 `java-library`로 둡니다. |
| 서비스 모듈 하나는 **실행용 jar 하나**만 만든다 | Dockerfile이 `build/libs`에서 실행용 jar(`*-plain.jar` 제외)가 정확히 1개인지 확인합니다. |
| `Dockerfile`, `.dockerignore`는 루트에 하나만 두고 **모듈별로 복사하지 않는다** | 모듈은 빌드 인자 `MODULE`로 구분합니다. |

모듈 폴더 이름의 기본값은 아래와 같습니다. 다르게 만들 경우 `.env`의 `*_MODULE` 값만 바꾸면 됩니다.

| 서비스 | 도메인 | 기본 모듈 경로 | 로컬 포트 (VSCode 실행 시) |
|---|---|---|---|
| gateway | Ingress (API 진입점) | `gateway` | 8080 |
| store | 메뉴·가게 | `store` | 8081 |
| order | 주문·장바구니 | `order` | 8082 |
| payment | 결제 | `payment` | 8083 |
| delivery | 배달 | `delivery` | 8084 |
| member | 인증·인가 | `member` | 8085 |

> 알림(notification) 서비스는 없습니다. 메일은 이벤트를 발행한 서비스가 자기 이벤트를 **자기 메일 큐**로 다시 받아 AWS SES 로 보냅니다 (10번).

---

## 2. 필수 의존성 (Spring Boot 4.0.x)

- 버전: **Spring Boot 4.0.8**, (gateway) **Spring Cloud 2025.1.3**. Boot 4.1 은 맞는 Spring Cloud(2026.0)가 아직 정식 출시 전이라 4.0.x 사용.
- Boot 4 는 기능별로 auto-configuration 이 나뉘어서 **라이브러리만 넣으면 동작하지 않고 `spring-boot-starter-*` 가 있어야** 자동 설정됩니다 (예: Flyway).
- 버전은 Spring Boot 가 관리하므로 적지 않습니다.

모든 서비스 모듈:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
// 분산 추적: traceId 를 HTTP·RabbitMQ 헤더로 전파하고 로그에 출력 (6-2)
implementation 'org.springframework.boot:spring-boot-micrometer-tracing-brave'
implementation 'io.micrometer:micrometer-tracing-bridge-brave'
// 후순위 (관측성) — 쓸 때 주석 해제 (9-2)
// implementation 'org.springframework.boot:spring-boot-starter-zipkin'   // Zipkin 전송 (위 2줄 대신)
// runtimeOnly    'io.micrometer:micrometer-registry-prometheus'          // Prometheus 지표
```

DB·메시지 큐를 쓰는 서비스 (gateway 제외 5개):

```groovy
implementation 'org.springframework.boot:spring-boot-starter-webmvc'      // Boot 4 이름 (구 starter-web)
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-flyway'      // Boot 4: Flyway 자동 실행에 필요
implementation 'org.flywaydb:flyway-database-postgresql'                  // Flyway 10+ 는 DB별 모듈 필요
implementation 'org.springframework.boot:spring-boot-starter-amqp'
runtimeOnly    'org.postgresql:postgresql'
```

세션·토큰 Redis 를 쓰는 서비스 (gateway, member):

```groovy
implementation 'org.springframework.boot:spring-boot-starter-session-data-redis'
// gateway(WebFlux)는 추가로 spring-boot-starter-data-redis-reactive
```

- `/actuator/health`: 로컬 확인과 Kubernetes liveness·readiness probe에 사용 (DB·RabbitMQ 연결 포함, N5.4)
- `/actuator/prometheus`: Prometheus 지표 경로 — **후순위** (현재 노출 안 함, 9-2)

---

## 3. 환경 변수 이름 (변경 금지)

설정값은 모두 아래 이름의 환경 변수로 받습니다. **이 이름은 로컬 `.env` → Kubernetes Secret → AWS Secrets Manager까지 그대로 이어지므로 임의로 바꾸지 마세요.**

| 변수 | 설명 | 로컬 compose 값 |
|---|---|---|
| `SERVER_PORT` | 서버 포트 | 컨테이너 안에서는 `8080` |
| `SPRING_PROFILES_ACTIVE` | 활성 프로필 | `local` |
| `TZ` | 시간대 (Dockerfile 기본값) | `Asia/Seoul` |
| `DB_HOST` / `DB_PORT` | PostgreSQL 인스턴스 주소 | `postgres` / `5432` |
| `DB_NAME` | **서비스별 DB 이름** (4번 표) | `member`, `store`, `orders`, `payment`, `delivery` |
| `DB_USER` / `DB_PASSWORD` | 서비스별 DB 계정 | 4번 표 / `.env` 값 |
| `DB_SCHEMA` | 서비스별 schema (= DB 이름) | 4번 표 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ 접속 정보 (운영: Amazon MQ for RabbitMQ 4.2) | `rabbitmq` / `5672` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | RabbitMQ 계정 | `.env` 값 |
| `RABBITMQ_VHOST` | 접속할 vhost (3-1) | `/` (운영 `/prod`·`/dev`) |
| `RABBITMQ_SSL_ENABLED` | TLS 접속 여부 (3-1) | `false` (운영 `true`, 포트 5671) |
| `REDIS_SESSION_HOST` / `REDIS_SESSION_PORT` | 세션·토큰 Redis (gateway·member) | `redis-session` / `6379` |
| `REDIS_SESSION_PASSWORD` | 세션 Redis 비밀번호 | `.env` 값 |
| `REDIS_CACHE_HOST` / `REDIS_CACHE_PORT` / `REDIS_CACHE_PASSWORD` | DB 캐시용 Redis — **보류** (이름만 예약) | 도입 시 `redis-cache` / `6379` |
| ~~`ZIPKIN_ENDPOINT`~~ | 추적 데이터 전송 주소 — **후순위, 주석 처리** (9-2) | — |
| `AWS_REGION` | AWS 리전 (SES 등) | `ap-northeast-2` |
| `STORE_SERVICE_URL`, `ORDER_SERVICE_URL`, `PAYMENT_SERVICE_URL`, `DELIVERY_SERVICE_URL`, `MEMBER_SERVICE_URL` | 서비스 주소 (설계서 이름). gateway 라우팅 + [내부] 동기 호출 | `http://store:8080` 등 |
| `JWT_SECRET` 🔒 | JWT 서명 키 (gateway 검증·member 발급, 32바이트 이상) | `.env` 값 |
| `TOSS_SECRET_KEY` 🔒 | 토스페이먼츠 시크릿 키 (payment, 테스트 키만) | `.env` 값 |
| ~~`DYNAMODB_ENDPOINT`~~ | DynamoDB — **주석 처리** (9번) | — |

- 🔒 = 비밀값. `.env` 에만 두고 코드·`application.yml` 에 기본값을 쓰지 않습니다. 로컬 K8s 는 `make secret` 이 `.env` 에서 6개 키를 Secret 으로 만들고, 각 서비스는 필요한 키만 받습니다 (차트 `secretKeys`). 운영은 Secrets Manager → K8s Secret (N4.6). 새 비밀값이 생기면 인프라 담당과 키 이름을 맞춰 주세요.
- 서비스 주소 주입 대상 (compose): gateway 는 5개 전부, order 는 `STORE_SERVICE_URL`·`MEMBER_SERVICE_URL`, delivery 는 `MEMBER_SERVICE_URL` (설계서 '동기 호출' 탭). 주소를 코드에 하드코딩하지 않습니다.
- 운영의 AWS 권한은 Access Key가 아니라 EKS Pod Identity(파드별 IAM 역할, 설계서 '인프라 구성 상세' 탭)로 주입되므로, **코드에 자격 증명을 넣지 마세요.**
- `.env` 에 `DB_NAME` 을 쓰지 마세요. VSCode(`envFile`)로 실행한 서비스가 전부 그 DB 로 붙습니다. 서비스 DB 이름은 `application.yml` 기본값을 씁니다.

### `application.yml` 작성 예시 (store 서비스, 전체)

```yaml
server:
  port: ${SERVER_PORT:8081}          # 로컬 VSCode 실행 시 서비스별 포트 (1번 표 참고)
  shutdown: graceful                  # 배포 중 처리 중인 요청을 끝내고 종료

spring:
  application:
    name: store
  lifecycle:
    timeout-per-shutdown-phase: 20s
  datasource:
    # RDS 인스턴스 1개 안에 서비스별 DB (DB 이름 = schema 이름)
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:store}?currentSchema=${DB_SCHEMA:store}
    username: ${DB_USER:store_svc}
    password: ${DB_PASSWORD}          # 기본값을 코드에 넣지 않음
    # ## DB 커넥션 풀 (부하 테스트 때 조정) — 지금은 HikariCP 기본값(10) 사용
    #   필요한 수 ≈ 피크 TPS × 쿼리 1건 평균 시간(초) + 여유  (N1.2 50 TPS × 0.05s ≈ 3 → 10 이면 충분)
    #   한도: 모든 서비스의 (파드 수 × maximum-pool-size) 합 < DB max_connections (로컬 postgres 기본 100)
    # hikari:
    #   maximum-pool-size: 10
  flyway:
    schemas: ${DB_SCHEMA:store}       # 마이그레이션 대상 = 자기 schema (flyway_schema_history 도 여기)
    default-schema: ${DB_SCHEMA:store}
    create-schemas: false             # DB·schema·계정은 초기화 스크립트(로컬) / Terraform(운영)이 만듦
  jpa:
    open-in-view: false               # 트랜잭션이 끝나면 바로 커넥션 반납 (4-2)
    hibernate:
      ddl-auto: validate              # 테이블은 Flyway 만 만듦. 엔티티와 안 맞으면 시작 실패 (4-2)
    properties:
      hibernate:
        jdbc:
          time_zone: Asia/Seoul       # KST
  jackson:
    time-zone: Asia/Seoul             # API 응답 시각 KST (+09:00)
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST:/}       # 로컬 / , 운영 Amazon MQ /prod·/dev
    ssl:
      enabled: ${RABBITMQ_SSL_ENABLED:false} # 로컬 false(5672) / 운영 Amazon MQ true(5671)
    publisher-confirm-type: correlated      # 발행 확인 (3-2)
    publisher-returns: true
    template:
      mandatory: true                       # 받을 큐가 없는 메시지는 버리지 않고 return
      observation-enabled: true             # 발행 시 traceId 를 메시지 헤더에 실음
    listener:
      simple:
        # ## 미리 받아 두는 메시지 수 (부하 테스트 때 조정)
        #   1건 처리가 느릴수록(DB 트랜잭션) 작게, 빠를수록 크게. 파드가 죽으면 이만큼 재전달됨
        prefetch: 10
        observation-enabled: true           # 소비 시 헤더의 traceId 를 이어받음
        default-requeue-rejected: false     # 실패 메시지를 큐에 되돌리지 않음 → DLQ (무한 재처리 방지)
        retry:                              # 앱 안에서 재시도 후 포기 → DLQ
          enabled: true
          max-attempts: 3                   # 최초 1회 + 재시도 2회
          initial-interval: 1s
          multiplier: 2
          max-interval: 10s

management:
  endpoints:
    web:
      exposure:
        include: health,info          # 후순위: ,prometheus
  endpoint:
    health:
      probes:
        enabled: true                 # /actuator/health/liveness, /readiness 활성화
  tracing:
    sampling:
      probability: 1.0                # 요청 100% 추적 (트래픽이 많아지면 낮춤)
    # 후순위 (관측성): Zipkin 전송 — starter-zipkin 과 함께 주석 해제
    # export:
    #   zipkin:
    #     endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}

logging:
  structured:
    format:
      console: logstash               # JSON 로그 (6-1)
```

### 3-1. 메시지 큐 — exchange·큐·DLQ·헤더 (설계서 '이벤트 목록' 탭)

큐·exchange·binding 은 **받는 서비스가 앱 코드(`@Bean`)로 선언**합니다 (브로커 정책 X). 로컬 RabbitMQ와 운영 Amazon MQ 어디서든 기동 시 자동 생성됩니다.

| 대상 | 이름 규칙 | 예 (delivery 가 주문 수락 이벤트를 받음) |
|---|---|---|
| 이벤트 exchange | **`baedalsak.events`** (topic, 전 서비스 공용 1개) | — |
| 라우팅 키 | `<발행 도메인>.<사건>` | `order.accepted` |
| 큐 | `<받는 서비스>.<이벤트>` (받는 쪽이 선언·소유) | `delivery.order-accepted` |
| 메일 큐 | `<발행 서비스>.mail.<이벤트>` (발행한 서비스가 자기 이벤트를 받음, 10번) | `order.mail.order-accepted` |
| DLX (dead letter exchange) | `<받는 서비스>.dlx` (서비스당 1개, direct) | `delivery.dlx` |
| DLQ | `<큐>.dlq`, 라우팅 키 = 원래 큐 이름 | `delivery.order-accepted.dlq` |

```java
@Bean
Declarables orderAcceptedQueues() {
    String queue = "delivery.order-accepted", dlx = "delivery.dlx";
    TopicExchange events = new TopicExchange("baedalsak.events");
    DirectExchange deadLetterExchange = new DirectExchange(dlx);
    Queue main = QueueBuilder.durable(queue)
            .quorum()                       // 큐는 quorum (설계서 '이벤트 목록' 탭, 인프라 구성 상세)
            .deadLetterExchange(dlx)
            .deadLetterRoutingKey(queue)
            .build();
    Queue dlq = QueueBuilder.durable(queue + ".dlq").quorum().build();
    return new Declarables(events, deadLetterExchange, main, dlq,
            BindingBuilder.bind(main).to(events).with("order.accepted"),
            BindingBuilder.bind(dlq).to(deadLetterExchange).with(queue));
}

// 메시지 본문 JSON (Jackson 3). 모르는 필드는 무시 → 발행 쪽이 필드를 추가해도 소비 쪽이 깨지지 않음
@Bean
MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
}
```

**메시지 헤더 (모든 이벤트 공통)**

| 헤더 | 넣는 방법 | 용도 |
|---|---|---|
| `messageId` = **eventId (UUID)** | `MessageProperties.setMessageId(eventId)` | 받는 쪽이 `processed_event` 에 INSERT → 중복 처리 방지 (N2.4) |
| `eventType` | `setHeader("eventType", "OrderAccepted")` | 이벤트 구분 |
| `eventVersion` | `setHeader("eventVersion", 1)` | 페이로드 구조가 바뀔 때 구분 |
| `occurredAt` | `setHeader("occurredAt", OffsetDateTime.now().toString())` | 발생 시각 (KST) |
| `traceId` | 자동 (`template.observation-enabled`) + 명시 헤더 | 주문 → 배달 흐름을 로그·Zipkin 에서 추적 (N5.2) |

| 규칙 | 이유 |
|---|---|
| vhost 는 `RABBITMQ_VHOST` 로만 받는다 | 브로커 1대 안에서 `/prod`·`/dev` 를 완전히 분리 (exchange·큐·권한이 vhost 별). dev 서비스가 prod 메시지를 가져가는 사고 방지 |
| TLS 는 `RABBITMQ_SSL_ENABLED` 로만 켠다 | Amazon MQ 는 AMQPS 5671 만 열림 → TLS 없으면 연결 실패. 같은 코드로 로컬(false)·운영(true) |
| `listener.simple.retry` 로 **앱 안에서 3회** (1s → 2s) 후 포기 | 일시적 장애는 재시도로 해결 (설계서: 3회 지수 백오프) |
| `default-requeue-rejected: false` | 포기한 메시지를 DLX → DLQ 로. `true` 면 같은 메시지가 무한 반복 |
| `prefetch: 10` | 1건마다 DB 트랜잭션이라 작게. 파드 2개에 고르게 분배, 파드가 죽어도 재전달 최대 10건 |
| 처리 로직은 **같은 메시지가 두 번 와도 안전하게** (멱등) | RabbitMQ 는 at-least-once. `processed_event`(PK=eventId) 또는 UNIQUE 제약으로 보장 — 설정이 아니라 코드 몫 |
| DLQ 에는 리스너를 붙이지 않는다 | 원인 확인 후 사람이 재처리 (MVP 수동 확인). 관리 화면 `http://localhost:15672` → Queues |
| 큐·DLQ 는 **quorum** 으로 선언한다 (`.quorum()`) | 설계서 '이벤트 목록'·'인프라 구성 상세' 탭. 메일 큐의 `deliveryLimit` 도 quorum 전용 (10-4) |
| 이미 만들어진 큐의 인자(DLX·큐 종류 등)를 바꾸지 않는다 | 인자가 다르면 `PRECONDITION_FAILED` 로 앱이 시작되지 않음. 로컬은 관리 화면에서 큐 삭제 후 재시작(또는 `docker compose down -v`), 운영은 인프라 담당과 협의 |

### 3-2. 이벤트 발행 — Outbox + 발행 확인 (N2.3)

상태 변경과 `outbox` INSERT 를 **한 트랜잭션**에 넣고, 발행기가 1초마다 미발행 행을 RabbitMQ 로 보냅니다.

**① 중복 발행 방지 — `FOR UPDATE SKIP LOCKED`**
order·delivery 는 파드가 2개(N2.1)라 발행기 2개가 같은 행을 동시에 읽을 수 있습니다. 행 잠금으로 한 파드만 가져가게 합니다.

```sql
-- 발행기 1회 (한 트랜잭션 안에서)
SELECT id, event_id, event_type, routing_key, payload, trace_id
  FROM outbox
 WHERE published_at IS NULL
 ORDER BY id
 LIMIT 100
   FOR UPDATE SKIP LOCKED;          -- 다른 파드가 잡은 행은 건너뜀 → 같은 행을 두 파드가 보내지 않음
-- 발행 확인(ack) 받은 행만:
UPDATE outbox SET published_at = now() WHERE id = ANY(:ackedIds);
```

**② 발행 확인 — `publisher-confirm-type: correlated` + `mandatory`**

| 결과 | 의미 | 발행기 처리 |
|---|---|---|
| confirm **ack** + return 없음 | 브로커가 받아 큐까지 넣음 | `published_at` 기록 |
| confirm **nack** / 시간 초과 | 브로커가 못 받음 | 기록하지 않음 → 다음 폴링에 다시 발행 |
| **return** 발생 | 받은 큐가 없음 (라우팅 키 오타·바인딩 누락) | 기록하지 않고 에러 로그 |

```java
CorrelationData cd = new CorrelationData(eventId);
rabbitTemplate.convertAndSend("baedalsak.events", routingKey, payload, m -> {
    m.getMessageProperties().setMessageId(eventId);   // + eventType, eventVersion, occurredAt, traceId
    return m;
}, cd);
boolean ok = cd.getFuture().get(5, TimeUnit.SECONDS).ack() && cd.getReturned() == null;
```

- 발행기가 같은 행을 두 번 보낼 수는 있습니다 (ack 직후 파드가 죽는 경우). 그래서 받는 쪽 멱등(3-1)은 여전히 필수입니다.
- 테이블 구조는 설계서 '테이블 정의서' 탭 (`outbox`, `processed_event`, `mail_log`).

### 3-3. 발행기·소비자는 각 서비스에서 구현 (2026-10-22 결정)

Outbox 발행기·이벤트 소비자·메일 리스너는 **공통 모듈 없이 각 서비스 안에** 만듭니다 (설계서 '테이블 정의서' 탭 공통 규칙, N6.2 — 공통 모듈에는 예외 포맷·로깅 설정·이벤트 DTO·traceId 유틸만).

> 설계서 v4 N6.2: "각 서비스에서 구현 → 추후 공통 모듈 생성". 지금은 각 서비스에 만들고, 공통 모듈은 나중에 따로 만듭니다. 그때 옮기기 쉽도록 아래 통일 규칙을 지켜 주세요.

| 서비스 | 발행기 (outbox) | 이벤트 소비자 (processed_event) | 메일 리스너 (mail_log, 10번) |
|---|:-:|:-:|:-:|
| order | ✓ OrderAccepted | ✓ PaymentApproved · Delivery 3종 | ✓ |
| payment | ✓ PaymentApproved | — | ✓ |
| delivery | ✓ Delivery 3종 | ✓ OrderAccepted | ✓ |

코드는 서비스마다 따로 있지만 **아래는 세 서비스가 똑같이** 맞춥니다. 다르면 이벤트가 전달되지 않거나 중복 처리됩니다.

| 통일할 것 | 기준 |
|---|---|
| 테이블 이름·구조 | `outbox` / `processed_event` / `mail_log` — 설계서 '테이블 정의서' 탭 그대로. 이름에 서비스 접두사를 붙이지 않음 (~~`payment_outbox`~~) |
| 발행기 동작 | 3-2 그대로: 1초 폴링, `FOR UPDATE SKIP LOCKED`, confirm ack 이고 return 이 없을 때만 `published_at` |
| 메시지 헤더 | 3-1 그대로: `messageId` = eventId(UUID), `eventType`, `eventVersion`, `occurredAt`, `traceId` |
| 소비자 멱등 | `processed_event`(PK = messageId) INSERT 에 성공했을 때만 처리. messageId 가 없거나 UUID 가 아니면 거부 → DLQ |
| 이름 | exchange `baedalsak.events` (~~`baedalssak.events`~~), 큐 `<받는 서비스>.<이벤트>`, 메일 큐 `<발행 서비스>.mail.<이벤트>`, DLQ `<큐>.dlq` |
| 이벤트 페이로드 | 설계서 '이벤트 목록' 탭의 필드 이름 그대로. 받는 쪽은 모르는 필드를 무시 (`JacksonJsonMessageConverter`) |

- 발행·소비 로직을 바꿀 때는 다른 두 서비스에도 같은 변경이 필요한지 PR 에 적어 주세요.

---

## 4. 데이터베이스

운영은 **RDS PostgreSQL 인스턴스 1개 + 서비스별 DB 5개** (DB 마다 같은 이름의 schema·전용 계정)입니다. 로컬 compose 도 같은 구조로 만듭니다 (`docker/local/postgres/init/01-service-schemas.sh`).

| 서비스 | DB | schema | DB 계정 |
|---|---|---|---|
| member | `member` | `member` | `member_svc` |
| store | `store` | `store` | `store_svc` |
| order | `orders` | `orders` | `order_svc` |
| payment | `payment` | `payment` | `payment_svc` |
| delivery | `delivery` | `delivery` | `delivery_svc` |

- `order` 는 PostgreSQL 예약어라 DB·schema 이름을 `orders` 로 씁니다.
- 각 계정은 **자기 DB 에만 접속**(다른 DB 는 `CONNECT` 권한 없음)하고, **자기 schema 에만** 테이블을 만들 수 있습니다. 다른 서비스 데이터는 API나 이벤트로 받아 주세요 (FK·조인 없음).
- `public` schema 에는 테이블을 만들 수 없게 막혀 있습니다.
- 관리자 기본 DB(`baedalsak`)는 컨테이너 관리용입니다. 서비스는 쓰지 않습니다.

### 4-1. 테이블 만들기 — Flyway

PostgreSQL 테이블은 **Flyway 마이그레이션 SQL로만** 만들고 바꿉니다. 앱이 시작될 때 Spring Boot가 아직 적용 안 된 파일을 자기 schema에 순서대로 적용합니다.

| 규칙 | 이유 |
|---|---|
| 의존성: `spring-boot-starter-flyway` + `flyway-database-postgresql` | Boot 4 는 starter 가 없으면 Flyway 가 실행되지 않음 |
| 위치: `<서비스>/src/main/resources/db/migration/` | Spring Boot 기본 경로. 설정 없이 읽힘 |
| 첫 파일: **`V1__<서비스>_schema.sql`** (설계서의 `{service}_schema.sql` → Flyway 이름) | 예: `V1__store_schema.sql`, `V1__order_schema.sql` |
| 이후: `V<번호>__<설명>.sql` (예: `V2__add_store_phone.sql`) | 밑줄 **2개**. 번호 순서대로 한 번씩만 적용 |
| **이미 적용(머지)된 파일은 수정·삭제·이름 변경하지 않는다.** 바꿀 게 있으면 새 번호 파일을 추가 | 내용·설명이 바뀌면 checksum 불일치로 앱이 시작되지 않음 |
| SQL에 schema 이름을 쓰지 않는다 (`CREATE TABLE store (...)`, ~~`store.store`~~) | `default-schema`가 서비스 schema로 잡혀 있음 |
| 브랜치끼리 번호가 겹치면 머지 전에 뒤 번호로 바꾼다 | 같은 번호 2개면 시작 실패 |

- 로컬에서 꼬였을 때: `docker compose down -v && docker compose up -d` (로컬 DB 전체 초기화 → 마이그레이션 처음부터 다시 적용)
- 운영(K8s)에서는 파드가 여러 개여도 Flyway가 DB 잠금으로 한 파드만 적용합니다. 오래 걸리는 마이그레이션(대량 데이터 변경)은 인프라 담당과 먼저 이야기해 주세요 (startup probe 시간 제한).

### 4-2. JPA

| 설정 | 이유 |
|---|---|
| `spring.jpa.hibernate.ddl-auto: validate` | 앱 시작 시 엔티티와 Flyway 가 만든 실제 테이블을 비교 → 컬럼 누락·타입 차이를 배포 시점에 잡음. `update`/`create` 는 Hibernate 가 테이블을 몰래 바꿔 Flyway 이력과 어긋나고 운영 데이터가 위험 |
| `spring.jpa.open-in-view: false` | 기본값 `true` 는 HTTP 요청이 끝날 때까지 DB 커넥션을 잡고, 응답을 만드는 중 지연 로딩 쿼리가 몰래 실행됨 → 부하 시 커넥션 풀 고갈. `false` 면 트랜잭션이 끝나면 바로 반납 |
| `hibernate.jdbc.time_zone: Asia/Seoul` | DB 컬럼은 `TIMESTAMPTZ`, 앱 시간대는 KST |
| 상태 컬럼은 `@Enumerated(EnumType.STRING)`, FK·객체 그래프 탐색 금지 | 설계서 테이블 정의서 공통 규칙, N3.2 |

---

## 5. Redis

Redis는 **용도별로 2개**를 따로 둡니다. 한 Redis에 섞으면 메모리가 찼을 때 캐시를 지우려다 세션·토큰까지 지워질 수 있기 때문입니다.

| 용도 | 상태 | 사용 서비스 | 메모리가 찼을 때 | 로컬 PC 접속 | 컨테이너 안 접속 |
|---|---|---|---|---|---|
| ① 세션·토큰 관리 | **사용** (ALB 단계에서 토큰 관리, 운영 ElastiCache) | gateway, member | 지우지 않음 (`noeviction`) | `localhost:6379` | `redis-session:6379` |
| ② DB 캐시 | **보류** (compose에 주석 처리) | 데이터를 가진 서비스 | 오래된 키부터 삭제 (`allkeys-lru`) | `localhost:6380` | `redis-cache:6379` |

- 세션·토큰 키에는 반드시 만료 시간(TTL)을 둡니다. Spring Session을 쓰면 자동으로 붙습니다.
- 세션 Redis에 세션·토큰 외의 데이터를 넣지 않습니다. 캐시가 필요해지면 ② 도입을 인프라 담당과 먼저 합의해 주세요.
- 운영에서는 둘 다 ElastiCache의 **별도 엔드포인트**이고 포트는 둘 다 6379입니다. 주소는 환경 변수로만 받으므로 코드는 바뀌지 않습니다.

`application.yml` 예시 (gateway·member만):

```yaml
spring:
  data:
    redis:
      host: ${REDIS_SESSION_HOST:localhost}
      port: ${REDIS_SESSION_PORT:6379}
      password: ${REDIS_SESSION_PASSWORD}
```

---

## 6. 로그·추적·시간대·종료·시크릿

### 6-1. 로그 (N5.1)

| 규칙 | 이유 |
|---|---|
| 로그는 **표준 출력(stdout)으로만**, **JSON** (`logging.structured.format.console: logstash`) | 운영에서 Fluent Bit(후순위)이 CloudWatch Logs로 보낼 예정. JSON 이라 traceId·서비스명으로 검색 가능. 파일 appender 금지 |
| 필수 필드: 시각·서비스명·레벨·`traceId`·사용자 ID·메시지 | 시각·서비스명·레벨·traceId·spanId 는 자동. 사용자 ID 는 MDC(`memberId`)에 넣으면 같이 찍힘 |
| 휴대폰 번호는 로그에 **마스킹** (`010-****-1234`) | N4.4 |

### 6-2. 분산 추적 (N5.2)

- `micrometer-tracing-brave` + `management.tracing.sampling.probability: 1.0` 이면 gateway 가 traceId 를 만들고 HTTP 헤더(`traceparent`·`b3`)로 하위 서비스에 전파합니다.
- RabbitMQ 는 `spring.rabbitmq.template.observation-enabled` / `listener.simple.observation-enabled: true` 로 메시지 헤더에 traceId 를 싣고 이어받습니다.
- gateway(WebFlux)는 `spring.reactor.context-propagation: auto` 가 있어야 로그에 traceId 가 찍힙니다.
- 결과: 요청 1건이 **gateway → 서비스 → RabbitMQ 발행 → 소비** 까지 같은 traceId. **로그(JSON)에서 traceId 로 검색**해 추적합니다 (설계서 N5.2 "UI 없이 로그 검색").
- Zipkin 화면은 후순위입니다 (9-2). 쓸 때는 starter 를 `spring-boot-starter-zipkin` 으로 바꾸고 compose 의 zipkin 을 주석 해제합니다.

### 6-3. 시간대 — KST

| 위치 | 설정 |
|---|---|
| 컨테이너·JVM | `TZ=Asia/Seoul` (Dockerfile 기본값, compose 에도 명시) |
| API 응답 (Jackson) | `spring.jackson.time-zone: Asia/Seoul` → `2026-10-16T11:03:13+09:00` |
| DB | 컬럼 `TIMESTAMPTZ` (절대 시각 저장), Hibernate `jdbc.time_zone: Asia/Seoul` |

> 설계서 API 규칙(UTC `...Z`)과 다릅니다. `+09:00` 은 이미 9시간을 더한 한국 시각이며 같은 순간을 나타냅니다. 프론트는 `new Date(...)` 로 그대로 읽으면 됩니다.

### 6-4. 종료·시크릿

| 규칙 | 이유 |
|---|---|
| `server.shutdown: graceful` 설정 | 배포 중 파드가 교체될 때 처리 중이던 요청이 끊기지 않습니다. |
| 비밀번호·키(`DB_PASSWORD`, `JWT_SECRET`, `TOSS_SECRET_KEY` …)를 `application.yml`이나 코드에 쓰지 않는다 | 모두 환경 변수로 받습니다 (3번 표 🔒). |
| `.env`는 커밋하지 않는다 | `.env.example`만 커밋합니다. `.gitignore`에 `.env`가 있는지 확인해 주세요. |

---

## 7. 로컬 개발 방법

### 처음 한 번

```bash
cp .env.example .env
```

> v2 이전 `.env` 를 쓰고 있다면: `JWT_SECRET`, `TOSS_SECRET_KEY` 를 추가하고 `DB_NAME` 줄은 지웁니다(주석). DB 구조가 바뀌었으므로 `docker compose down -v` 후 다시 `up` 합니다 (로컬 DB 초기화).

### 방법 A — 의존성만 띄우고 VSCode에서 서비스 실행 (평소 개발)

```bash
docker compose up -d          # PostgreSQL, RabbitMQ, 세션 Redis
```

VSCode에서 실행할 때 `.env`를 읽도록 `.vscode/launch.json`에 `envFile`을 지정합니다.

```json
{
  "configurations": [
    {
      "type": "java",
      "name": "store (local)",
      "request": "launch",
      "mainClass": "<store의 main 클래스>",
      "projectName": "store",
      "envFile": "${workspaceFolder}/.env",
      "env": { "SPRING_PROFILES_ACTIVE": "local" }
    }
  ]
}
```

> `.env`에는 `DB_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `REDIS_SESSION_PASSWORD`, `JWT_SECRET`, `TOSS_SECRET_KEY`가 들어 있어 앱이 그대로 읽습니다. `DB_NAME`, `DB_USER`, `DB_SCHEMA`는 `application.yml`의 기본값(서비스별)이 쓰입니다.

### 방법 B — 서비스까지 전부 컨테이너로 실행 (통합 확인)

```bash
docker compose --profile apps up -d --build          # 전체
docker compose --profile apps up -d --build store    # store만 다시 빌드
```

### 확인

- 서비스 상태: `http://localhost:8081/actuator/health` → `{"status":"UP"}`
- gateway 경유: `curl http://localhost:8080/v1/stores` (K8s Ingress 경유는 `/api/v1/stores`)
- DB 목록: `docker compose exec postgres psql -U admin -d baedalsak -c "\l"` → `member`, `store`, `orders`, `payment`, `delivery`
- Flyway 적용 확인: `docker compose exec postgres psql -U admin -d store -c "\dt store.*"` → `flyway_schema_history` + 내 테이블
- RabbitMQ 관리 화면: `http://localhost:15672` (Exchanges 에 `baedalsak.events`, Queues 에서 `<큐>.dlq` 메시지 수)
- 추적: `docker compose logs | grep <traceId>` → gateway·서비스·MQ 소비 로그가 같은 traceId
- 로컬 DB 초기화(데이터 삭제): `docker compose down -v`

### gateway 라우팅 (`/api/v*/...`)

Ingress 가 `/api` 를 떼고 gateway 에 넘깁니다 (`/api/v1/stores` → `/v1/stores`). gateway 는 **버전 숫자와 무관하게** `/v*/...` 로 라우팅합니다. 위에서부터 먼저 맞는 것 하나로 갑니다.

| 순서 | 경로 | 서비스 |
|:-:|---|---|
| 1 | `/v*/stores/*/orders/**` | order (사장님 가게 주문 목록 — store 보다 먼저) |
| 2 | `/v*/stores/**`, `/v*/categories/**` | store |
| 3 | `/v*/orders/**`, `/v*/cart/**` | order |
| 4 | `/v*/payments/**` | payment |
| 5 | `/v*/deliveries/**` | delivery |
| 6 | `/v*/auth/**`, `/v*/members/**` | member |
| — | `/internal/**` | **라우트 없음 → 404** (서비스 간 직접 호출 전용) |

- 서비스 컨트롤러는 `/v1/...` 처럼 버전을 포함한 경로로 만듭니다. v2 가 생기면 서비스에 `/v2/...` 만 추가하면 gateway 는 그대로입니다.

---

## 8. 체크리스트 (첫 서비스 모듈을 만들 때)

- [ ] `settings.gradle`의 모듈 이름이 폴더 경로와 같다
- [ ] 공통 모듈에는 Spring Boot 플러그인을 적용하지 않았다
- [ ] Spring Boot 4.0.x, 2번의 의존성을 넣었다 (actuator, tracing-brave / webmvc, data-jpa, flyway, amqp)
- [ ] `application.yml`이 3번 표의 환경 변수 이름을 쓴다 (예시 그대로 복사 권장)
- [ ] 비밀값(`DB_PASSWORD`, `JWT_SECRET`, `TOSS_SECRET_KEY`) 기본값이 코드에 없다
- [ ] (DB 사용 서비스) 첫 테이블은 `db/migration/V1__<서비스>_schema.sql`, JPA `ddl-auto: validate`, `open-in-view: false`
- [ ] (메시지를 받는 서비스) 큐를 `baedalsak.events` 바인딩 + DLX·DLQ 와 함께 선언, `processed_event` 로 멱등 처리
- [ ] (이벤트를 보내는 서비스) Outbox + `FOR UPDATE SKIP LOCKED` + 발행 확인(ack·return) 후 `published_at`, `messageId = eventId`
- [ ] (이벤트를 주고받는 서비스) 테이블 이름 `outbox`·`processed_event`·`mail_log`, exchange `baedalsak.events` (3-3)
- [ ] 컨트롤러 경로가 `/v1/...` 이고 내부 API 는 `/internal/...` 이다
- [ ] 로그가 stdout JSON 으로만 나가고 휴대폰 번호는 마스킹한다
- [ ] `docker compose --profile apps up -d --build <서비스>` 후 `/actuator/health`가 UP이다

---

## 9. 주석 처리 목록 (2026-10-16)

### 9-1. DynamoDB

설계서에 DynamoDB 사용처가 없어 **코드는 지우지 않고 주석 처리**했습니다. 다시 쓰려면 아래를 주석 해제합니다 (경로는 `baedalsak/` 기준).

| 파일 | 주석 처리한 부분 |
|---|---|
| `baedalsak-server/compose.yaml` | `x-app.depends_on.dynamodb` · `x-app-env` 의 `DYNAMODB_ENDPOINT`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` · `services.dynamodb` 블록 전체 · `volumes.ddbdata` |
| `baedalsak-server/smoke-test/compose.yaml` | 위와 같음 |
| `baedalsak-infra/Makefile` | `VALUES_LOCAL` 의 `--set global.dynamodb.endpoint=...` (주석 줄로 남김) · `infra-check`·`dep-host` 의 포트 8000 → zipkin 9411 로 교체 |
| `baedalsak-infra/helm/values/local.yaml` | `global.dynamodb.endpoint`, `global.awsLocalCredentials` |
| `baedalsak-server/docs/backend-infra-rules.md` | 3번 표 `DYNAMODB_ENDPOINT` (취소선) |

그대로 둔 것 (켜져 있지 않아 동작하지 않음):
- `baedalsak-infra/helm/charts/baedalsak-service/templates/configmap.yaml` 의 DynamoDB 블록 — `dynamodb.enabled: true` 일 때만 주입
- `baedalsak-infra/helm/charts/baedalsak-service/values.yaml` 의 `global.dynamodb`, `dynamodb.enabled: false`
- `baedalsak-infra/helm/values/services/*.yaml` 의 `dynamodb.enabled: false`, `baedalsak-infra/helm/values/aws.yaml` 의 `global.dynamodb.endpoint: ""`
- `AWS_REGION` 은 SES 에서 쓰므로 주석 처리하지 않음

### 9-2. 관측성 — Zipkin · Prometheus · Grafana · Fluent Bit (후순위)

후순위 개발 예정이라 **설정을 주석 처리**했습니다. traceId 를 HTTP·RabbitMQ 헤더로 전파하고 로그에 찍는 기능(6-2)은 설계서 N5.2 필수 항목이라 유지합니다.

| 도구 | 파일 | 주석 처리한 부분 |
|---|---|---|
| Zipkin | `baedalsak-server/compose.yaml`, `baedalsak-server/smoke-test/compose.yaml` | `x-app-env.ZIPKIN_ENDPOINT` · `services.zipkin` 블록 |
| Zipkin | `baedalsak-server/smoke-test/*/build.gradle` (6개) | `spring-boot-starter-zipkin` (대신 `spring-boot-micrometer-tracing-brave` + `micrometer-tracing-bridge-brave`) |
| Zipkin | `baedalsak-server/smoke-test/*/src/main/resources/application.yml` (6개) | `management.tracing.export.zipkin.endpoint` |
| Zipkin | `baedalsak-infra/Makefile` | `VALUES_LOCAL` 의 `--set global.zipkin.endpoint` (주석 줄) · `infra-check`·`dep-host` 의 9411 확인 제거 (3개 확인) |
| Zipkin | `baedalsak-infra/helm/charts/baedalsak-service/templates/configmap.yaml` | `ZIPKIN_ENDPOINT` 주입 (`{{/* */}}` 주석) |
| Zipkin | `baedalsak-infra/helm/charts/baedalsak-service/values.yaml` | `global.zipkin` |
| Zipkin | `baedalsak-infra/helm/values/local.yaml` | `global.zipkin.endpoint` |
| Prometheus | `baedalsak-server/smoke-test/*/build.gradle` (6개) | `micrometer-registry-prometheus` |
| Prometheus | `baedalsak-server/smoke-test/*/src/main/resources/application.yml` (6개) | `management.endpoints.web.exposure.include` 의 `prometheus` |
| Prometheus | `baedalsak-server/docs/backend-infra-rules.md` | 2번 의존성 (주석) |
| Grafana | — | 설정 파일 없음 (문서에만 계획으로 표기) |
| Fluent Bit | — | 설정 파일 없음 (문서에만 계획으로 표기, 로그는 stdout JSON 으로 준비됨) |

다시 켤 때: 위 주석 해제 → Zipkin 은 `spring-boot-starter-zipkin` 하나로 tracing-brave 2줄을 대체, Prometheus 는 exposure 에 `prometheus` 추가.

---

## 10. 메일 발송 (알림 서비스 대신)

notification 서비스·DB 는 없습니다. **이벤트를 발행한 서비스가, 자기가 발행한 그 이벤트를 자기 메일 큐로 다시 받아** AWS SES 로 메일을 보냅니다 (설계서 '이벤트 목록' 탭).

- 2026-10-22 수정: 이전 안(요청 처리 중 `@Async` 로 SES 직접 호출)을 폐기하고 메일 큐 방식으로 확정

### 10-1. 흐름 (결제 예)

```
[payment 서비스]
① 결제 승인 API 처리
   payment 테이블 APPROVED + outbox 행 저장 (같은 트랜잭션) → 고객에게 바로 응답
② outbox 발행기 → RabbitMQ baedalsak.events 에 PaymentApproved 발행 (3-2)
③ 이 이벤트가 두 큐로 복사됨
   - order.payment-approved          → order 서비스가 받음 (주문 상태 결제완료)
   - payment.mail.payment-approved   ← 자기 메일 큐: 발행한 payment 가 다시 받음
④ payment 의 메일 리스너가 이 큐에서 꺼내 SES 로 메일 발송 → 끝나면 ack
```

### 10-2. 비동기·동기 구간

**요청 기준으로는 비동기, 메일 리스너 안에서는 동기**입니다.

| 구간 | 방식 | 이유 |
|---|---|---|
| API 요청 → 메일 발송 | **비동기** (큐 경유) | API 는 메일을 기다리지 않고 응답합니다. SES 가 느리거나 장애가 나도 결제·주문은 성공하고(N2.2), 메일은 큐에 남아 재시도되다 끝내 실패하면 DLQ 로 갑니다 |
| 리스너 안 SES 호출 | **동기** (`@Async` 사용 금지) | 리스너가 SES 발송을 끝낸 뒤 ack 합니다. `@Async` 를 쓰면 리스너가 바로 끝나 발송 전에 ack 되어, 발송이 실패해도 메시지가 사라지고 재시도 시 중복 발송이 생깁니다 |

### 10-3. 메일 큐 목록

| 발행 서비스 | 이벤트 (라우팅 키) | 메일 큐 | 받는 사람 | 메일 |
|---|---|---|---|---|
| payment | PaymentApproved (`payment.approved`) | `payment.mail.payment-approved` | 고객·사장님 | 결제완료 |
| order | OrderAccepted (`order.accepted`) | `order.mail.order-accepted` | 고객 | 조리 시작 |
| delivery | DeliveryAssigned (`delivery.assigned`) | `delivery.mail.delivery-assigned` | 고객·사장님 | 배차 완료 |
| delivery | DeliveryPickedUp (`delivery.picked_up`) | `delivery.mail.delivery-picked-up` | 고객 | 배달 시작 |
| delivery | DeliveryCompleted (`delivery.completed`) | `delivery.mail.delivery-completed` | 고객·사장님 | 배달 완료 |

- 메일 큐도 업무 큐처럼 발행 서비스가 앱 코드(`@Bean`)로 `baedalsak.events` 에 바인딩해 선언합니다 (3-1).
- 자기 이벤트를 받는 구조이므로 **Outbox 발행기(3-2)가 먼저 있어야** 메일이 나갑니다.

### 10-4. 메일 리스너 규칙

| 규칙 | 이유 |
|---|---|
| 리스너 메서드 안에서 SES 를 **동기** 호출, `@Async` 사용 금지 | 발송이 끝나야 ack (10-2) |
| 동시성 1 · prefetch 1 | 메일은 순서대로 한 건씩. 파드가 죽어도 재전달은 1건 |
| `mail_log` 에 `(event_id, recipient)` 를 먼저 INSERT(`SENDING`)로 선점하고, 선점에 성공했을 때만 발송 → 성공하면 `SENT` | 재전달·파드 종료로 같은 메일이 두 번 나가지 않게 (N2.4). 고객·사장님 2명이면 2행 |
| 일시 오류(SES 쓰로틀링·네트워크)는 3회 재시도(2초, ×2) 후 DLQ, 영구 오류(잘못된 주소 등)는 바로 DLQ. 메일 큐는 `deliveryLimit 3`(quorum 큐 인자) | 설계서 N1.4·'이벤트 목록' 탭. DLX 는 업무 큐와 같은 `<서비스>.dlx` → `<큐>.dlq`, DLQ 는 수동 확인 (3-1) |
| 수신자 이메일은 이벤트 페이로드의 `customerEmail`·`ownerEmail` 을 쓴다 | 메일 리스너는 member 를 호출하지 않음. 이메일은 order 가 주문 생성 때 member 내부 API 로 받아 `orders` 에 스냅샷하고, payment(`/internal/orders` 응답)·delivery(OrderAccepted 페이로드)도 각자 스냅샷 컬럼에 저장 (설계서 v4 '서비스 경계'·'테이블 정의서' 탭) |
| 로컬(`local` 프로필)은 실제 발송 대신 로그만, 운영은 SES 실발송 (샌드박스는 인증된 수신자만) | N5.6 |
| 운영 SES 권한은 Pod Identity(`ses:SendEmail`), 리전은 `AWS_REGION` | 코드에 자격 증명을 넣지 않음 (3번) |

- `mail_log` 테이블 구조는 설계서 '테이블 정의서' 탭 (order·payment·delivery 세 DB 동일).
