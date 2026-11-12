// 테스트 계정 정리 (#191) — 로그인 → 회원 탈퇴(DELETE /members/me)로 member · Cognito 에서 지운다.
//   k6 run -e BASE_URL=... -e LT_PASSWORD=... -e LT_PREFIX=lt -e LT_COUNT=500 loadtest/cleanup.js
//
// 주로 로컬 Cognito 풀(baedalsak-local, 팀원 공유)을 깨끗이 할 때 쓴다.
// AWS(dev)는 DB 가 매일 새로 뜨고 Cognito 가입 계정도 매일 지워져서 안 돌려도 된다.
// 탈퇴한 계정은 같은 이메일로 다시 가입할 수 있어 accounts.js 를 다시 돌리면 된다.

import { sleep } from 'k6';
import { COUNT, accounts, requirePassword } from './lib/config.js';
import { authDelete, login } from './lib/auth.js';

export const options = {
  scenarios: {
    cleanup: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '2h' },
  },
};

export default function () {
  requirePassword();
  const result = { deleted: 0, missing: 0, failed: 0 };

  for (const account of accounts(COUNT)) {
    const session = login(account, { name: 'cleanup login' });
    if (!session) {
      result.missing++;   // 없는 계정(이미 정리됨)이면 로그인 401
      continue;
    }
    const res = authDelete(session, '/members/me');
    if (res.status === 204) {
      result.deleted++;
    } else {
      result.failed++;
      console.error(`탈퇴 실패 ${account.email} status=${res.status} body=${res.body}`);
    }
    sleep(0.3);
  }

  console.log(`계정 ${COUNT}개: 지움 ${result.deleted} · 없음 ${result.missing} · 실패 ${result.failed}`);
}
