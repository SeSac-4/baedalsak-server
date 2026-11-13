// 로그인 · 인증 헤더 공통 코드 (#191). 다른 서비스 시나리오도 이 파일을 가져다 쓴다.
//
//   import { loginAll, authGet, authPost } from './lib/auth.js';
//   export function setup() { return { sessions: loginAll(accounts()) }; }
//   export default function (data) {
//     const s = data.sessions[(__VU - 1) % data.sessions.length];
//     authGet(s, '/stores');
//   }
//
// 토큰은 쿠키로만 오간다: __Host-access(AT 30분, Path=/) · __Secure-sid(세션, Path=/api/v1/auth).
// k6 쿠키 저장소에 맡기지 않고 직접 Cookie 헤더를 만든다 — 로컬 http 에서는 Secure 쿠키가 자동으로 안 붙고,
// setup() 에서 받은 쿠키를 가상 사용자(VU)들이 나눠 써야 해서.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { API } from './config.js';

// 상태 변경 요청(POST·PUT·PATCH·DELETE)은 gateway CSRF 검사에 이 헤더가 필요 (없으면 403)
const REQUESTED_WITH = { 'X-Requested-With': 'XMLHttpRequest' };
const JSON_HEADERS = { 'Content-Type': 'application/json', ...REQUESTED_WITH };

function cookieValue(res, name) {
  const cookies = res.cookies[name];
  return cookies && cookies.length > 0 ? cookies[0].value : null;
}

/** 로그인 → { email, access, sid }. 실패하면 null (tags 로 시나리오별 집계) */
export function login(account, tags = { name: 'POST /auth/login' }) {
  const res = http.post(`${API}/auth/login`,
    JSON.stringify({ email: account.email, password: account.password }),
    { headers: JSON_HEADERS, tags });
  const ok = check(res, { '로그인 200': (r) => r.status === 200 });
  if (!ok) {
    return null;
  }
  return { email: account.email, access: cookieValue(res, '__Host-access'), sid: cookieValue(res, '__Secure-sid') };
}

/**
 * setup() 에서 계정 전부 로그인. Cognito 호출 한도가 있어 천천히(기본 초당 5번) 한다.
 * AT 는 30분 유효 → 테스트를 30분 안에 끝내거나, 긴 테스트는 refresh() 로 갱신
 */
export function loginAll(accountList, perSecond = 5) {
  const sessions = [];
  for (const account of accountList) {
    const session = login(account, { name: 'setup login' });
    if (session) {
      sessions.push(session);
    }
    sleep(1 / perSecond);
  }
  if (sessions.length === 0) {
    throw new Error('로그인한 계정이 없습니다. accounts.js 로 계정을 먼저 만들었는지, LT_PASSWORD 가 맞는지 확인');
  }
  console.log(`로그인 ${sessions.length}/${accountList.length}`);
  return sessions;
}

/** 토큰 갱신 → 새 AT 를 session 에 넣는다. 세션 쿠키는 /api/v1/auth 경로에만 보냄 */
export function refresh(session, tags = { name: 'POST /auth/refresh' }) {
  const res = http.post(`${API}/auth/refresh`, null, {
    headers: { ...REQUESTED_WITH, Cookie: `__Secure-sid=${session.sid}` },
    tags,
  });
  const ok = check(res, { '갱신 200': (r) => r.status === 200 });
  if (ok) {
    session.access = cookieValue(res, '__Host-access') || session.access;
  }
  return res;
}

function authParams(session, tags, extraHeaders = {}) {
  return { headers: { Cookie: `__Host-access=${session.access}`, ...extraHeaders }, tags };
}

/** 로그인 사용자로 GET. path 는 /api/v1 뒤 (예: '/members/me'). name 태그로 경로별 집계 */
export function authGet(session, path, name = `GET ${path}`) {
  return http.get(`${API}${path}`, authParams(session, { name }));
}

export function authPost(session, path, body, name = `POST ${path}`) {
  return http.post(`${API}${path}`, body === undefined ? null : JSON.stringify(body),
    authParams(session, { name }, JSON_HEADERS));
}

export function authDelete(session, path, name = `DELETE ${path}`) {
  return http.del(`${API}${path}`, null, authParams(session, { name }, REQUESTED_WITH));
}
