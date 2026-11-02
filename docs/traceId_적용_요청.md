# traceId 적용 요청

- 확인 기준: `baedalsak-server` `18f998e`, `baedalsak-client` `8d47541` (`origin/develop`, 2026-11-02)
- 확인 방법: 코드 검색. 실제 요청으로 Loki·Tempo 추적은 하지 않음

## 결론

**`traceId`는 gateway → 서비스 구간까지만 이어집니다.** 아래 6가지를 넣어야 에러 화면에서 받은 `traceId` 하나로 로그와 비동기 처리까지 찾을 수 있습니다.

| 순서 | 작업 | 담당 | 관련 이슈 |
|---|---|---|---|
| 1 | 프론트 에러에 `traceId` 표시 | 유창호 | 신규 |
| 2 | 4xx 업무 예외 WARN 로그 (server `common`) | 김형준 | 신규 |
| 3 | delivery 에러 응답에 `traceId` 추가 | 박범훈 | 신규 |
| 4 | gateway 에러 본문에 `traceId` 추가 | 김형준 | #112 |
| 5 | 모든 응답에 `X-Trace-Id` 헤더 추가 | 김형준 | 신규 |
| 6 | MQ 소비 구간 추적 연결 | 유창호(order), 이상민(payment), 박범훈(delivery) | 신규 |

## 1. 어떤 작업이 빠졌는가

| 구간 | 현재 상태 |
|---|---|
| gateway → 서비스 HTTP 전파 | 동작함 |
| `common` 사용 서비스의 에러 본문 | `traceId` 포함 |
| 프론트 | `api-types.ts`에 타입만 있고 표시하지 않음 |
| 4xx 업무 예외 로그 | 없음. `GlobalExceptionHandler`는 처리되지 않은 예외만 `log.error` |
| delivery 에러 본문 | `BusinessException`만 포함. `@Valid` 실패, 409, 500은 없음 |
| gateway 401, 403, 404, 429, 5xx | 공통 에러 형식이 아니고 `traceId` 없음 |
| 응답 헤더 | `X-Trace-Id` 없음 |
| MQ 소비 | outbox가 `traceId` 헤더를 넣지만 소비 쪽에서 읽는 코드를 찾지 못함 |

## 2. 왜 해야 하는가

- **문의 대응:** 사용자가 에러를 알려도 어떤 요청인지 찾을 값이 화면에 없습니다.
- **원인 검색:** 4xx는 로그가 없어 Loki에서 `traceId`로 찾을 수 없습니다.
- **gateway 공백:** 401, 403, 429는 서비스까지 가지 않아 지금 구조로는 응답에서 `traceId`를 얻을 수 없습니다. RateLimiter가 들어오면 429도 같은 문제가 생깁니다.
- **비동기 공백:** outbox 발행 스레드에서 새 trace가 시작됩니다. 주문 → 결제 → 배달 → 메일 흐름이 요청의 `traceId`로 이어지지 않습니다. `docs/backend-infra-rules.md` 6-2의 "소비까지 같은 traceId"와도 다릅니다.

## 3. 어떻게 하면 되는가

### 3-1. 프론트 에러에 `traceId` 표시 (유창호)

- 수정 위치: `baedalsak-client/src/shared/api/get-error-message.ts`
- 5xx 에러 문구 뒤에 `traceId`를 붙입니다. 503을 먼저 반환하는 분기도 포함합니다.
- 모든 에러는 콘솔에 전체 `traceId`를 남깁니다.
- `traceId`가 없는 응답은 기존 문구를 그대로 보여 줍니다.

### 3-2. 4xx 업무 예외 WARN 로그 (김형준)

- 수정 위치: `baedalsak-server/common/src/main/java/com/baedalsak/common/error/GlobalExceptionHandler.java`
- 적용 범위: `common`을 쓰는 store, order, payment, member (delivery는 3-3에서 처리)
- `BusinessException` 처리부에 에러 코드를 포함한 `log.warn` 1줄을 추가합니다.
- `traceId`는 MDC로 로그에 자동으로 찍힙니다.
- 비밀번호, 토큰, 연락처 같은 개인정보는 넣지 않습니다.

### 3-3. delivery 에러 응답 (박범훈)

- 수정 위치: `delivery/.../exception/GlobalExceptionHandler.java`
- `@Valid` 실패, 409, 처리되지 않은 예외(500)도 `{code, message, traceId}` 형식으로 응답합니다.
- `common` 모듈로 전환하면 인증 코드 중복과 `memberId` 로그 누락도 함께 해결됩니다. 가능하면 전환을 권장합니다.

### 3-4. gateway 에러 본문 (김형준, #112)

- gateway는 WebFlux라 `common`의 MVC 핸들러를 쓸 수 없어 별도로 구현합니다.
- 401: `ServerAuthenticationEntryPoint`
- 403: `ServerAccessDeniedHandler`, `RequestedWithHeaderFilter`
- 404, 5xx: `ErrorWebExceptionHandler`
- 429: RateLimiter(#24) 작업 시 같은 형식을 사용합니다.

### 3-5. `X-Trace-Id` 헤더 (김형준)

- GlobalFilter가 아니라 **WebFilter**로 구현합니다.
- 필터 순서는 Spring Security(-100)보다 앞에 둡니다. GlobalFilter로 만들면 Security가 끊는 401, 403에 헤더가 붙지 않습니다.
- `response.beforeCommit()`에서 현재 span의 `traceId`를 헤더에 넣습니다.
- 3-4와 같은 영역이므로 함께 작업합니다.

### 3-6. MQ 소비 구간 (order, payment, delivery 담당)

- 소비 시 메시지 헤더 `traceId`를 읽어 MDC `originTraceId`로 넣고, 처리 후 제거합니다.
- Loki에서 `originTraceId`로 원래 요청의 후속 처리를 찾을 수 있습니다.
- 메일 리스너도 포함합니다.
- `docs/backend-infra-rules.md` 6-2 문구를 실제 동작에 맞게 수정합니다.
- Tempo 화면까지 한 줄로 이으려면 outbox에 `traceparent`를 저장해야 하고 DB 컬럼 추가가 필요합니다. 이번 범위에서는 제외합니다.

## 4. 완료 확인

- [ ] 500 에러 화면과 콘솔에서 `traceId` 확인
- [ ] 409 같은 업무 에러의 `traceId`로 Loki 로그 검색
- [ ] delivery `@Valid` 실패, 409, 500 응답에 `traceId` 포함
- [ ] 토큰 없이 호출한 401 응답의 본문과 `X-Trace-Id` 값이 일치
- [ ] `X-Requested-With` 없이 호출한 403 응답에도 `traceId` 포함
- [ ] 주문 수락 요청의 `traceId`로 배달 생성 소비 로그 검색 (`originTraceId`)
