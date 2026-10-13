#!/bin/sh
# 로컬 PostgreSQL 초기화 — 컨테이너를 처음 만들 때(볼륨이 비어 있을 때) 한 번만 실행됨.
# 운영 RDS와 같은 구조: 인스턴스 1개 + 서비스별 schema·계정 분리.
# 'order', 'user'는 PostgreSQL 예약어라 schema 이름을 orders, member로 사용.
#
# 다시 실행하려면: docker compose down -v  (로컬 DB 데이터가 모두 삭제됨)
set -e   # -u는 쓰지 않음: 실행 권한이 없으면 entrypoint가 source로 읽기 때문

SQL=""
for pair in \
  store:store_svc \
  orders:order_svc \
  payment:payment_svc \
  delivery:delivery_svc \
  notification:notification_svc \
  member:user_svc
do
  schema=${pair%%:*}
  role=${pair##*:}
  SQL="$SQL
CREATE ROLE $role LOGIN PASSWORD '$SERVICE_DB_PASSWORD';
CREATE SCHEMA $schema AUTHORIZATION $role;
REVOKE ALL ON SCHEMA $schema FROM PUBLIC;
ALTER ROLE $role SET search_path = $schema;"
done

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<EOSQL
$SQL
-- public schema에는 아무도 테이블을 만들지 못하게 막음 (서비스별 schema 강제)
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
EOSQL
