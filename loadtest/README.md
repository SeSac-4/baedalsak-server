# 부하 테스트 (k6)

설계서 목표치(비기능 N1.1 ~ N1.3)를 확인하는 스크립트입니다. 지금은 **member · gateway** 파트만 있고,
다른 서비스는 `lib/auth.js`(로그인 · 인증 헤더)를 가져다 시나리오 파일을 추가하면 됩니다. (#191)

| 목표 (설계서) | 값 |
|---|---|
| 응답 시간 p95 (N1.1) | 조회 500ms · 주문 · 결제 생성 1s · 배차 수락 300ms |
| 처리량 (N1.2) | 피크 50 TPS, 동시 주문 100건 |
| 동시 사용자 (N1.3) | 고객 500 · 사장님 50 · 라이더 100 |

## 파일

| 파일 | 하는 일 |
|---|---|
| `lib/config.js` | 주소 · 계정 규칙 (`-e` 로 받음) |
| `lib/auth.js` | 로그인(쿠키 꺼내기) · 토큰 갱신 · 인증 GET/POST/DELETE (쿠키 + `X-Requested-With`) |
| `accounts.js` | 테스트 고객 계정 N개 가입 (이미 있으면 건너뜀) |
| `member-gateway.js` | 내 정보 조회(기준선) · 토큰 갱신 · 로그인 시나리오 + 통과 조건 |
| `cleanup.js` | 테스트 계정 탈퇴로 정리 (로컬 Cognito 풀용) |

## 설치

```bash
brew install k6
```

## 실행 전 확인

| 확인 | 왜 | 어떻게 |
|---|---|---|
| **gateway 요청 제한 끄기** | 부하 도구는 IP 몇 개에서 몰아 보내 금방 429 (로그인 · 가입 IP당 분당 10번, 그 밖 회원당 분당 100번) | gateway `RATE_LIMIT_ENABLED=false`, 끝나면 다시 켜기 |
| **알림 문자 끄기** | 주문 시나리오를 같이 돌리면 문자 비용(월 1달러 한도) | member `SMS_NOTIFICATION_ENABLED=false` |
| 메일 | SES 샌드박스라 테스트 계정 주소로는 실패 → 재시도 → DLQ | 메일 담당과 발송 처리 맞추기 |
| 결제 | 토스 테스트 키인지 | payment 담당 확인 |
| 어디서 쏠지 | 집 인터넷이 병목이 될 수 있음 | AWS 는 같은 리전 EC2(Bastion 등)에서 실행 권장 |
| AWS 계정 | DB 는 매일 새로 뜨고 Cognito 가입 계정(id ≥ 1000)은 매일 끄기 때 지워짐 | 테스트하는 날 켜기 뒤에 `accounts.js` 다시 실행 |

## 순서

비밀번호는 코드에 두지 않고 실행할 때만 넘깁니다 (영문 소문자 + 숫자, 8자 이상).

```bash
export LT_PASSWORD='<테스트 계정 비밀번호>'
```

1. 계정 만들기 (로컬은 팀원과 겹치지 않게 접두어에 이름: `hj.lt`)
```bash
k6 run -e BASE_URL=http://localhost:8080 -e LT_PASSWORD="$LT_PASSWORD" -e LT_PREFIX=hj.lt -e LT_COUNT=20 loadtest/accounts.js
```

2. 스모크 (1분, 초당 2번) — 스크립트 · 서버 설정 확인
```bash
k6 run -e BASE_URL=http://localhost:8080 -e LT_PASSWORD="$LT_PASSWORD" -e LT_PREFIX=hj.lt -e LT_COUNT=20 -e SMOKE=1 loadtest/member-gateway.js
```

3. 목표치 (초당 50번 10분) → 한계 찾기 (RATE 를 100 · 200 … 올려 p95 · 에러율이 무너지는 지점)
```bash
k6 run -e BASE_URL=https://baedalsak.click -e LT_PASSWORD="$LT_PASSWORD" -e LT_PREFIX=lt -e LT_COUNT=100 -e RATE=50 -e DURATION=10m loadtest/member-gateway.js
```

4. (로컬) 정리
```bash
k6 run -e BASE_URL=http://localhost:8080 -e LT_PASSWORD="$LT_PASSWORD" -e LT_PREFIX=hj.lt -e LT_COUNT=20 loadtest/cleanup.js
```

## 결과 읽기

k6 가 끝에 표를 출력하고, 통과 조건(thresholds)을 못 지키면 ✗ 표시와 함께 실패로 끝납니다.

| 항목 | 뜻 | 기준 |
|---|---|---|
| `http_req_duration{scenario:browse}` p(95) | 내 정보 조회 응답 시간 상위 5% 경계 | < 500ms |
| `http_req_duration{scenario:refresh / login}` p(95) | 갱신 · 로그인 (Cognito 포함) | < 1s |
| `http_req_failed{scenario:browse}` | 실패율 (4xx · 5xx) | < 1% |
| `checks` | 200 · 429 아님 등 확인 통과율 | > 99% |
| `dropped_iterations` | 가상 사용자가 모자라 못 보낸 요청 | 0 이 아니면 maxVUs 를 늘리거나 서버가 느린 것 |

## Grafana 에서 같이 볼 것

| 볼 것 | 어디 | 의심 |
|---|---|---|
| gateway · member p95, 5xx | Spring Boot Observability 대시보드 (`http_server_requests_seconds`) | k6 결과와 비교해 네트워크 몫 구분 |
| CPU · 파드 수 | gateway HPA(2~4) | CPU 70% 넘으면 늘어나는지 |
| DB 커넥션 대기 | `hikaricp_connections_pending` | 대기가 생기면 풀(기본 10) 부족 |
| Redis 지연 | gateway 로그 "fail-open" 경고 | 블랙리스트 · 요청 제한이 100ms 넘어 통과 처리됨 |
| 느린 요청 | Loki 에서 traceId → Tempo | 어느 구간(gateway · member · DB · Cognito)이 느린지 |
| Cognito | member 로그 `TooManyRequests` · 503 | 로그인 · 갱신 비율을 낮추기 |

## 다른 서비스 시나리오 추가

`member-gateway.js` 를 복사해 `<서비스>.js` 로 만들고, `browse` 자리에 자기 API 를 넣으면 됩니다.

```js
import { accounts } from './lib/config.js';
import { authGet, authPost, loginAll } from './lib/auth.js';

export function setup() { return { sessions: loginAll(accounts()) }; }

export function browse(data) {
  const s = data.sessions[Math.floor(Math.random() * data.sessions.length)];
  authGet(s, '/stores?size=20');                    // GET
  authPost(s, '/cart/items', { /* API 명세의 요청 본문 */ });   // 상태 변경은 X-Requested-With 자동
}
```

사장님 · 라이더 계정은 seed(101 · 102 · 201 · 202) 또는 가입 API(`/auth/signup/owner` · `/rider`)로 만들어 같은 방식으로 로그인하면 됩니다.
