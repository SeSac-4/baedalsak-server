#!/bin/sh
# 로컬 PostgreSQL 초기화 — 컨테이너를 처음 만들 때(볼륨이 비어 있을 때) 한 번만 실행됨.
# 운영 RDS와 같은 구조: 인스턴스 1개 + 서비스별 DB 5개 (DB마다 같은 이름의 schema·전용 계정).
# 'order'는 PostgreSQL 예약어라 DB·schema 이름을 orders로 사용.
#
#   격리: 각 계정은 자기 DB에만 접속 가능(CONNECT 권한), 자기 schema에만 테이블 생성 가능
#   다시 실행하려면: docker compose down -v  (로컬 DB 데이터가 모두 삭제됨)
set -e   # -u는 쓰지 않음: 실행 권한이 없으면 entrypoint가 source로 읽기 때문

for pair in \
  member:member_svc \
  store:store_svc \
  orders:order_svc \
  payment:payment_svc \
  delivery:delivery_svc
do
  db=${pair%%:*}
  role=${pair##*:}

  # 1) 계정 + DB (관리자 기본 DB에서 실행)
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<EOSQL
CREATE ROLE $role LOGIN PASSWORD '$SERVICE_DB_PASSWORD';
CREATE DATABASE $db OWNER $role;
-- 다른 서비스 계정은 이 DB에 접속 불가
REVOKE ALL ON DATABASE $db FROM PUBLIC;
EOSQL

  # 2) 서비스 schema (해당 DB 안에서 실행)
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$db" <<EOSQL
CREATE SCHEMA $db AUTHORIZATION $role;
-- public schema에는 아무도 테이블을 만들지 못하게 막음 (서비스 schema 강제)
ALTER SCHEMA public OWNER TO "$POSTGRES_USER";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER ROLE $role IN DATABASE $db SET search_path = $db;
EOSQL
done
