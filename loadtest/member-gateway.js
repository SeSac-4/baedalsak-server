// member · gateway 부하 시나리오 (#191). 목표치는 설계서 N1.1 · N1.2.
//   k6 run -e BASE_URL=... -e LT_PASSWORD=... -e LT_PREFIX=lt -e LT_COUNT=100 loadtest/member-gateway.js
//
// 시나리오 3개 (동시에 돈다)
//   browse   로그인한 고객이 내 정보 조회 — gateway 의 인증(JWT) · 블랙리스트(Redis) · 요청 제한(Redis) · 라우팅 + member DB 조회.
//            gateway 를 지나는 모든 API 가 공통으로 내는 비용이라 기준선이 된다. 목표 p95 500ms
//   refresh  토큰 갱신 (Redis 세션 + Cognito). 30분마다 한 번이라 낮은 비율
//   login    로그인 (Cognito + Redis 세션 저장). 실제로는 드물어서 낮은 비율. 목표 p95 1s
//
// 세기 조절 (-e)
//   RATE      browse 초당 요청 수 (기본 50 = 설계서 피크 50 TPS)
//   DURATION  유지 시간 (기본 10m). AT 가 30분이라 30분 넘기지 말 것
//   SMOKE=1   1분 동안 초당 2번 — 스크립트 확인용
//
// 실행 전 끌 것: gateway RATE_LIMIT_ENABLED=false (회원당 분당 100번 · 로그인 IP당 10번에 걸림),
//               member SMS_NOTIFICATION_ENABLED=false (주문 시나리오와 같이 돌릴 때)

import { check, sleep } from 'k6';
import { accounts, requirePassword } from './lib/config.js';
import { authGet, login, loginAll, refresh } from './lib/auth.js';

const SMOKE = __ENV.SMOKE === '1';
const RATE = SMOKE ? 2 : parseInt(__ENV.RATE || '50', 10);
const DURATION = SMOKE ? '1m' : (__ENV.DURATION || '10m');
const SIDE_RATE = SMOKE ? 1 : Math.max(1, Math.round(RATE / 25));   // refresh · login 은 browse 의 약 4%

export const options = {
  setupTimeout: '10m',   // 계정이 많으면 setup 로그인에 시간이 걸림 (초당 5번)
  scenarios: {
    browse: {
      executor: 'constant-arrival-rate',   // 응답이 느려져도 초당 요청 수를 유지 → 실제 피크와 비슷
      rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: Math.max(10, RATE), maxVUs: Math.max(10, RATE * 4),
      exec: 'browse',
    },
    refresh: {
      executor: 'constant-arrival-rate',
      rate: SIDE_RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 5, maxVUs: 20,
      exec: 'refreshToken',
    },
    login: {
      executor: 'constant-arrival-rate',
      rate: SIDE_RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 5, maxVUs: 20,
      exec: 'loginAgain',
    },
  },
  thresholds: {
    // 시나리오별 통과 조건 (설계서 N1.1). 못 지키면 k6 가 실패(종료 코드 99)로 끝남
    'http_req_duration{scenario:browse}': ['p(95)<500'],
    'http_req_duration{scenario:refresh}': ['p(95)<1000'],
    'http_req_duration{scenario:login}': ['p(95)<1000'],
    'http_req_failed{scenario:browse}': ['rate<0.01'],   // 에러율 1% 미만 (알람 기준은 5%)
    checks: ['rate>0.99'],
  },
};

export function setup() {
  requirePassword();
  const list = accounts();
  return { accounts: list, sessions: loginAll(list) };
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export function browse(data) {
  const res = authGet(pick(data.sessions), '/members/me');
  check(res, {
    '내 정보 200': (r) => r.status === 200,
    '429 아님 (요청 제한 꺼야 함)': (r) => r.status !== 429,
  });
}

export function refreshToken(data) {
  refresh(pick(data.sessions));
}

export function loginAgain(data) {
  login(pick(data.accounts));   // 로그인할 때마다 Redis 에 세션이 하나씩 생김 (14일 TTL, 테스트 규모에선 문제 없음)
  sleep(0.1);
}
