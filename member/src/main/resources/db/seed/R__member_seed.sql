-- =============================================================================
-- member 개발용 seed 계정 (2026-10-23 인프라 합의, #73)
--   개발 환경에서만 실행: SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
--   운영은 기본값(db/migration 만)이라 실행되지 않는다.
--
--   R__ (반복 마이그레이션): 버전 마이그레이션(V*)이 모두 끝난 뒤 실행되고,
--   이 파일 내용이 바뀌면 다시 실행된다 → 여러 번 실행돼도 안전하게 작성한다.
--
--   id 규칙: 고객 1~ / 사장님 101~ / 라이더 201~ (seed 는 1~999), 가입은 1000부터
--   Cognito 사용자(username = id)는 별도 스크립트로 만든다.
-- =============================================================================

-- 회원 6명
--   OVERRIDING SYSTEM VALUE: id 가 GENERATED ALWAYS 라 직접 넣으려면 필요
--   ON CONFLICT DO NOTHING : 이미 있으면(id·email 중복) 건너뜀 → 다시 실행돼도 오류 없음
INSERT INTO member (id, email, name, phone, role)
OVERRIDING SYSTEM VALUE
VALUES
    (1,   'seed-customer1@baedalsak.click', '시드고객1',   '010-1000-0001', 'CUSTOMER'),
    (2,   'seed-customer2@baedalsak.click', '시드고객2',   '010-1000-0002', 'CUSTOMER'),
    (101, 'seed-owner1@baedalsak.click',    '시드사장님1', '010-1000-0101', 'OWNER'),
    (102, 'seed-owner2@baedalsak.click',    '시드사장님2', '010-1000-0102', 'OWNER'),
    (201, 'seed-rider1@baedalsak.click',    '시드라이더1', '010-1000-0201', 'RIDER'),
    (202, 'seed-rider2@baedalsak.click',    '시드라이더2', '010-1000-0202', 'RIDER')
ON CONFLICT DO NOTHING;

-- 사장님 프로필 (가입 시 입력한 가게 이름 — 가게 등록 화면 기본값)
INSERT INTO owner_profile (member_id, store_name_at_signup)
VALUES
    (101, '시드분식'),
    (102, '시드치킨')
ON CONFLICT DO NOTHING;

-- 라이더 프로필 (운송수단)
INSERT INTO rider_profile (member_id, vehicle_type)
VALUES
    (201, 'MOTORCYCLE'),
    (202, 'BICYCLE')
ON CONFLICT DO NOTHING;

-- id 를 직접 넣어도 시퀀스는 그대로라, 맞춰 두지 않으면 가입 때 1·2… 가 다시 나와 충돌한다
--   GREATEST(999, 현재 최대 id) → 다음 가입 id 는 1000 (이미 1000 이상이 있으면 그 다음)
SELECT setval(pg_get_serial_sequence('member', 'id'), GREATEST(999, (SELECT MAX(id) FROM member)));
