// 부하 테스트 공통 설정 (#191). 값은 모두 실행할 때 -e 로 넘긴다 (비밀번호는 코드 · 저장소에 두지 않음)
//   BASE_URL     gateway 주소. 로컬 http://localhost:8080 / AWS https://baedalsak.click
//   LT_PASSWORD  테스트 계정 비밀번호 (영문 소문자 + 숫자, 8자 이상)
//   LT_PREFIX    테스트 계정 이메일 앞부분. 로컬 풀은 팀원끼리 겹치지 않게 이름을 붙임 (예: hj.lt)
//   LT_COUNT     테스트 계정 수

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
export const API = `${BASE_URL}/api/v1`;

export const PASSWORD = __ENV.LT_PASSWORD;
export const PREFIX = __ENV.LT_PREFIX || 'lt';
export const COUNT = parseInt(__ENV.LT_COUNT || '20', 10);

export function requirePassword() {
  if (!PASSWORD) {
    throw new Error('LT_PASSWORD 가 없습니다. 예: k6 run -e LT_PASSWORD=... accounts.js');
  }
}

/** n 번째(1부터) 테스트 고객 계정 */
export function account(n) {
  return {
    email: `${PREFIX}.customer${n}@test.baedalsak.click`,
    password: PASSWORD,
    name: `부하고객${n}`,
    // 010-9 로 시작하는 가짜 번호 (문자 인증 안 함 → 알림 문자도 안 감)
    phone: `010-9${String(Math.floor(n / 10000) % 1000).padStart(3, '0')}-${String(n % 10000).padStart(4, '0')}`,
  };
}

export function accounts(count = COUNT) {
  return Array.from({ length: count }, (_, i) => account(i + 1));
}
