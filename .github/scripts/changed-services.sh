#!/usr/bin/env bash
# =============================================================================
# 변경된 백엔드 서비스를 골라 GitHub Actions matrix(JSON)로 출력
#
#   사용: changed-services.sh <비교 기준 커밋 SHA | 빈 값>
#
#   규칙
#     - <모듈 폴더>/ 아래 파일이 바뀜          → 그 서비스만
#     - 무시 목록(프론트·문서·로컬 compose 등)  → 대상 없음
#     - 그 밖의 파일 (공통 모듈, build.gradle, settings.gradle, gradle/,
#       Dockerfile, .dockerignore, 백엔드 CI 파일 등)  → 6개 전체
#     - 기준 커밋이 없음 (수동 실행, 새 브랜치 등)  → 6개 전체
#     - 단, 아직 만들어지지 않은 서비스는 항상 제외
#       (루트에 gradlew 가 없거나, 모듈 폴더에 build.gradle(.kts) 가 없으면 "준비 안 됨")
#       → 백엔드 팀이 모듈을 추가하면 그 서비스부터 자동으로 CI 대상에 들어감
#
#   출력 (GITHUB_OUTPUT 이 있으면 거기에, 없으면 stdout)
#     matrix={"include":[{"name":"store","module":"store"}, ...]}
#     any=true|false
# =============================================================================
set -euo pipefail

BASE="${1:-}"
SERVICES_FILE="${SERVICES_FILE:-.github/backend-services.json}"
OUT="${GITHUB_OUTPUT:-/dev/stdout}"

# 실제로 빌드할 수 있는 서비스만 골라냄 (gradlew + 모듈 빌드 파일이 있는 것)
ready_services() {
  if [ ! -f gradlew ]; then
    echo "루트에 gradlew 없음 → 빌드 가능한 서비스 없음" >&2
    echo "[]"
    return
  fi
  jq -c '.[]' "$SERVICES_FILE" | while read -r row; do
    module=$(jq -r '.module' <<<"$row")
    if [ -f "$module/build.gradle" ] || [ -f "$module/build.gradle.kts" ]; then
      echo "$row"
    else
      echo "준비 안 된 서비스 제외: $(jq -r '.name' <<<"$row") ($module/build.gradle 없음)" >&2
    fi
  done | jq -sc '.'
}
READY=$(ready_services)

# 백엔드 이미지와 무관한 경로 (바뀌어도 백엔드 CI 대상 아님)
is_ignored() {
  case "$1" in
    frontend/*|docs/*|*.md) return 0 ;;
    compose.yaml|compose.*.yaml|docker-compose*.yml|.env.example|docker/local/*) return 0 ;;
    .github/workflows/frontend*|.gitignore|.editorconfig|.vscode/*|.idea/*) return 0 ;;
    *) return 1 ;;
  esac
}

emit() {   # $1 = 서비스 이름 목록 (줄바꿈 구분), "ALL" 이면 전체
  local names="$1" matrix
  if [ "$names" = "ALL" ]; then
    matrix=$(jq -c '{include: .}' <<<"$READY")
  else
    matrix=$(printf '%s\n' "$names" | jq -R . | jq -sc --argjson s "$READY" \
      '[.[] | select(length > 0)] as $n | {include: [$s[] | select(.name as $x | $n | index($x))]}')
  fi
  local any; any=$(jq -r 'if (.include | length) > 0 then "true" else "false" end' <<<"$matrix")
  {
    echo "matrix=$matrix"
    echo "any=$any"
  } >> "$OUT"
  echo "대상 서비스: $(jq -r '[.include[].name] | if length == 0 then "(없음)" else join(", ") end' <<<"$matrix")" >&2
}

# 기준 커밋이 없거나 찾을 수 없으면 전체
if [ -z "$BASE" ] || [ "$BASE" = "0000000000000000000000000000000000000000" ] \
   || ! git cat-file -e "${BASE}^{commit}" 2>/dev/null; then
  echo "기준 커밋 없음 → 전체 서비스" >&2
  emit "ALL"
  exit 0
fi

mapfile -t FILES < <(git diff --name-only "$BASE" HEAD)
mapfile -t MODULES < <(jq -r '.[] | "\(.name)\t\(.module)"' "$SERVICES_FILE")

selected=""
for f in "${FILES[@]}"; do
  [ -z "$f" ] && continue
  is_ignored "$f" && continue

  hit=""
  for row in "${MODULES[@]}"; do
    name="${row%%$'\t'*}"; module="${row#*$'\t'}"
    case "$f" in
      "$module"/*) hit="$name"; break ;;
    esac
  done

  if [ -z "$hit" ]; then
    echo "공통 파일 변경: $f → 전체 서비스" >&2
    emit "ALL"
    exit 0
  fi
  selected+="$hit"$'\n'
done

emit "$(printf '%s' "$selected" | sort -u)"
