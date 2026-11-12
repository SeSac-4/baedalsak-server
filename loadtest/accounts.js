// 테스트 고객 계정 만들기 (#191). 부하 테스트 전에 한 번 실행한다.
//   k6 run -e BASE_URL=... -e LT_PASSWORD=... -e LT_PREFIX=lt -e LT_COUNT=500 loadtest/accounts.js
//
// - 가입 API(gateway → member → Cognito)를 그대로 부른다. 이미 있는 계정(409)은 건너뜀 → 여러 번 실행해도 됨
// - gateway 요청 제한(가입은 IP당 분당 10번)을 꺼야 빨리 끝난다: RATE_LIMIT_ENABLED=false
//   켜져 있으면 429 를 받고 Retry-After 만큼 기다렸다가 다시 시도 (느리지만 끝까지 감)
// - AWS(dev): DB 는 매일 새로 뜨고 Cognito 의 가입 계정(id ≥ 1000)은 매일 끄기 때 지워진다 → 테스트하는 날 켜기 뒤에 다시 실행
// - 휴대폰 인증은 하지 않음 → 알림 문자 대상 아님

import http from 'k6/http';
import { sleep } from 'k6';
import { API, COUNT, accounts, requirePassword } from './lib/config.js';

export const options = {
  scenarios: {
    create: {
      executor: 'shared-iterations',   // Cognito 가입 호출 한도가 있어 한 명이 차례로
      vus: 1, iterations: 1,
      maxDuration: '2h',               // 요청 제한이 켜져 있으면 분당 10개라 오래 걸릴 수 있음
    },
  },
};

const PER_SECOND = parseFloat(__ENV.LT_RATE || '3');   // 초당 가입 수

export default function () {
  requirePassword();
  const result = { created: 0, existed: 0, failed: 0 };

  for (const account of accounts(COUNT)) {
    for (let attempt = 1; attempt <= 5; attempt++) {
      const res = http.post(`${API}/auth/signup/customer`, JSON.stringify(account), {
        headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
        tags: { name: 'POST /auth/signup/customer' },
      });
      if (res.status === 201) {
        result.created++;
        break;
      }
      if (res.status === 409) {
        result.existed++;
        break;
      }
      if (res.status === 429) {
        const wait = parseInt(res.headers['Retry-After'] || '10', 10);
        console.warn(`요청 제한 429 — ${wait}초 기다림 (RATE_LIMIT_ENABLED=false 로 끄면 빠름)`);
        sleep(wait);
        continue;
      }
      console.error(`가입 실패 ${account.email} status=${res.status} body=${res.body}`);
      if (attempt === 5) {
        result.failed++;
      }
      sleep(2);
    }
    sleep(1 / PER_SECOND);
  }

  console.log(`계정 ${COUNT}개: 새로 만듦 ${result.created} · 이미 있음 ${result.existed} · 실패 ${result.failed}`);
}
