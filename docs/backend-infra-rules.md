# baedalsak-server 백엔드 ↔ 인프라 연동 규칙

> 이 문서는 `Dockerfile`, `compose.yaml`, 이후 CI(GitHub Actions)·Kubernetes 배포가 **서비스 코드와 맞물리기 위한 최소 규칙**입니다.
> 서비스 내부 설계(패키지 구조, API 등)는 자유롭게 하되, 아래 항목만 지켜 주세요.
> 규칙을 바꿔야 하면 인프라 담당과 먼저 이야기해 주세요. Dockerfile·compose·배포 설정이 함께 바뀝니다.

- 작성: 인프라(백엔드 A) · 2026-10-13
- 관련 파일: `Dockerfile`, `.dockerignore`, `compose.yaml`, `.env.example`, `docker/local/postgres/init/`

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

| 서비스 | 기본 모듈 경로 | 로컬 포트 (VSCode 실행 시) |
|---|---|---|
| gateway | `gateway` | 8080 |
| store | `store` | 8081 |
| order | `order` | 8082 |
| payment | `payment` | 8083 |
| delivery | `delivery` | 8084 |
| notification | `notification` | 8085 |
| user | `user` | 8086 |

---

## 2. 필수 의존성

모든 서비스 모듈에 추가해 주세요.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
runtimeOnly    'io.micrometer:micrometer-registry-prometheus'   // Prometheus 지표 수집용
```

- `/actuator/health`: 로컬 확인과 Kubernetes liveness·readiness probe에 사용
- `/actuator/prometheus`: Prometheus가 지표를 수집하는 경로

---

## 3. 환경 변수 이름 (변경 금지)

설정값은 모두 아래 이름의 환경 변수로 받습니다. **이 이름은 로컬 `.env` → Kubernetes Secret → AWS Secrets Manager까지 그대로 이어지므로 임의로 바꾸지 마세요.**

| 변수 | 설명 | 로컬 compose 값 |
|---|---|---|
| `SERVER_PORT` | 서버 포트 | 컨테이너 안에서는 `8080` |
| `SPRING_PROFILES_ACTIVE` | 활성 프로필 | `local` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | PostgreSQL 접속 정보 | `postgres` / `5432` / `baedalsak` |
| `DB_USER` / `DB_PASSWORD` | 서비스별 DB 계정 | 아래 4번 표 참고 |
| `DB_SCHEMA` | 서비스별 schema | 아래 4번 표 참고 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ 접속 정보 | `rabbitmq` / `5672` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | RabbitMQ 계정 | `.env` 값 |
| `DYNAMODB_ENDPOINT` | DynamoDB 엔드포인트 (로컬 전용) | `http://dynamodb:8000` |
| `REDIS_SESSION_HOST` / `REDIS_SESSION_PORT` | 세션 관리용 Redis (gateway·user) | `redis-session` / `6379` |
| `REDIS_SESSION_PASSWORD` | 세션 Redis 비밀번호 | `.env` 값 |
| `REDIS_CACHE_HOST` / `REDIS_CACHE_PORT` / `REDIS_CACHE_PASSWORD` | DB 캐시용 Redis — **보류** (이름만 예약) | 도입 시 `redis-cache` / `6379` |
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` |
| `STORE_URL`, `ORDER_URL` … | gateway가 라우팅할 서비스 주소 | `http://store:8080` 등 |

> 운영에서는 `DYNAMODB_ENDPOINT`를 **비워 둡니다.** 값이 없으면 AWS SDK 기본 엔드포인트를 쓰도록 코드를 작성해 주세요.
> 운영의 AWS 권한은 Access Key가 아니라 IRSA(파드별 IAM 역할)로 주입되므로, **코드에 자격 증명을 넣지 마세요.**

### `application.yml` 작성 예시 (store 서비스)

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
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:baedalsak}?currentSchema=${DB_SCHEMA:store}
    username: ${DB_USER:store_svc}
    password: ${DB_PASSWORD}          # 기본값을 코드에 넣지 않음
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true                 # /actuator/health/liveness, /readiness 활성화
```

---

## 4. 데이터베이스

운영은 **RDS PostgreSQL 인스턴스 1개 + 서비스별 schema·계정 분리**, 그리고 **DynamoDB** 2종입니다. 로컬 compose도 같은 구조로 만들어 둡니다.

| 서비스 | schema | DB 계정 |
|---|---|---|
| store | `store` | `store_svc` |
| order | `orders` | `order_svc` |
| payment | `payment` | `payment_svc` |
| delivery | `delivery` | `delivery_svc` |
| notification | `notification` | `notification_svc` |
| user | `member` | `user_svc` |

- `order`, `user`는 PostgreSQL 예약어라 schema 이름을 `orders`, `member`로 씁니다.
- 각 계정은 **자기 schema에만** 테이블을 만들 수 있습니다. 다른 서비스의 schema를 직접 조회하지 말고 API나 이벤트로 받아 주세요.
- `public` schema에는 테이블을 만들 수 없게 막혀 있습니다.
- 어떤 데이터를 DynamoDB로 보낼지는 서비스 단계에서 결정합니다. 테이블이 생기면 인프라 담당에게 테이블 이름과 키 구조를 공유해 주세요. 운영 테이블은 Terraform으로 만듭니다.

---

## 5. Redis

Redis는 **용도별로 2개**를 따로 둡니다. 한 Redis에 섞으면 메모리가 찼을 때 캐시를 지우려다 세션까지 지워질 수 있기 때문입니다.

| 용도 | 상태 | 사용 서비스 | 메모리가 찼을 때 | 로컬 PC 접속 | 컨테이너 안 접속 |
|---|---|---|---|---|---|
| ① 세션 관리 | **사용** | gateway, user | 지우지 않음 (`noeviction`) | `localhost:6379` | `redis-session:6379` |
| ② DB 캐시 | **보류** (compose에 주석 처리) | 데이터를 가진 서비스 | 오래된 키부터 삭제 (`allkeys-lru`) | `localhost:6380` | `redis-cache:6379` |

- 세션 키에는 반드시 만료 시간(TTL)을 둡니다. Spring Session을 쓰면 자동으로 붙습니다.
- 세션 Redis에 세션 외의 데이터를 넣지 않습니다. 캐시가 필요해지면 ② 도입을 인프라 담당과 먼저 합의해 주세요.
- 운영에서는 둘 다 ElastiCache의 **별도 엔드포인트**이고 포트는 둘 다 6379입니다. 주소는 환경 변수로만 받으므로 코드는 바뀌지 않습니다.

`application.yml` 예시 (gateway·user만):

```yaml
spring:
  data:
    redis:
      host: ${REDIS_SESSION_HOST:localhost}
      port: ${REDIS_SESSION_PORT:6379}
      password: ${REDIS_SESSION_PASSWORD}
```

---

## 6. 로그·종료·시크릿

| 규칙 | 이유 |
|---|---|
| 로그는 **표준 출력(stdout)으로만** 남긴다. 파일 appender 금지 | 운영에서 Fluent Bit이 컨테이너 출력을 CloudWatch Logs로 보냅니다. 파일로 쓰면 수집되지 않고 디스크만 찹니다. |
| `server.shutdown: graceful` 설정 | 배포 중 파드가 교체될 때 처리 중이던 요청이 끊기지 않습니다. |
| 비밀번호·키를 `application.yml`이나 코드에 쓰지 않는다 | 모두 환경 변수로 받습니다. |
| `.env`는 커밋하지 않는다 | `.env.example`만 커밋합니다. `.gitignore`에 `.env`가 있는지 확인해 주세요. |

---

## 7. 로컬 개발 방법

### 처음 한 번

```bash
cp .env.example .env
```

### 방법 A — 의존성만 띄우고 VSCode에서 서비스 실행 (평소 개발)

```bash
docker compose up -d          # PostgreSQL, RabbitMQ, DynamoDB Local, 세션 Redis
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

> `.env`에는 `DB_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `REDIS_SESSION_PASSWORD`가 들어 있어 앱이 그대로 읽습니다. `DB_USER`, `DB_SCHEMA`는 `application.yml`의 기본값(서비스별)이 쓰입니다.

### 방법 B — 서비스까지 전부 컨테이너로 실행 (통합 확인)

```bash
docker compose --profile apps up -d --build          # 전체
docker compose --profile apps up -d --build store    # store만 다시 빌드
```

### 확인

- 서비스 상태: `http://localhost:8081/actuator/health` → `{"status":"UP"}`
- RabbitMQ 관리 화면: `http://localhost:15672`
- 로컬 DB 초기화(데이터 삭제): `docker compose down -v`

---

## 8. 체크리스트 (첫 서비스 모듈을 만들 때)

- [ ] `settings.gradle`의 모듈 이름이 폴더 경로와 같다
- [ ] 공통 모듈에는 Spring Boot 플러그인을 적용하지 않았다
- [ ] actuator, micrometer-prometheus 의존성을 추가했다
- [ ] `application.yml`이 3번 표의 환경 변수 이름을 쓴다
- [ ] 비밀번호 기본값이 코드에 없다
- [ ] 로그가 stdout으로만 나간다
- [ ] `docker compose --profile apps up -d --build <서비스>` 후 `/actuator/health`가 UP이다