# 백엔드 CI (GitHub Actions)

baedalsak-server 저장소의 백엔드 CI 설명입니다. 전체 인프라 결정 사항은 `docs/backend-infra-rules.md`를 참고하세요.

```
.github/
├── backend-services.json          # 서비스 ↔ 모듈 폴더 목록 (6개)
├── scripts/changed-services.sh    # 변경된 서비스 감지
└── workflows/
    ├── backend-ci.yml             # 진입점 (PR / main push / 수동 실행)
    └── _backend-service.yml       # 서비스 1개 처리 (재사용 워크플로)
```

## 동작

| 상황 | 대상 | 하는 일 |
|---|---|---|
| PR → `main` | 변경된 서비스 | 테스트 → 이미지 빌드(amd64) → Trivy 검사 |
| `main` 머지 | 변경된 서비스 | 위와 같음 |
| `develop` 머지 | 변경된 서비스 | 위와 같음 → **성공하면** `notify-infra` 가 AWS 배포 요청 (실패한 커밋은 배포 안 됨). 문서만 바뀐 push 는 CI·배포 둘 다 안 돎 |
| 수동 실행 (Actions 화면) | 6개 전체 | 위와 같음 |

- **이미지 push 없음** (GHCR 사용 안 함, 2026-10-23). 배포 이미지는 develop 머지 시 `notify-infra` → baedalsak-infra 가 빌드해 **ECR** 에 올립니다.
- Trivy: **CRITICAL이 있으면 실패**합니다. 전체 결과(HIGH 이하 포함)는 저장소 **Security → Code scanning** 탭에 올라갑니다.

### 변경 서비스 판단 규칙
| 바뀐 파일 | 대상 |
|---|---|
| `<모듈 폴더>/` 아래 | 그 서비스만 |
| `frontend/`, `docs/`, `*.md`, `compose.yaml`, `.env.example`, `docker/local/` | 없음 (CI 안 돎) |
| 그 밖의 파일 (공통 모듈, `build.gradle`, `settings.gradle`, `gradle/`, `Dockerfile`, `.dockerignore`, 백엔드 CI 파일 등) | **6개 전체** |

**아직 만들어지지 않은 서비스는 항상 제외합니다.** 루트에 `gradlew`가 없거나 모듈 폴더에 `build.gradle(.kts)`가 없으면 "준비 안 됨"으로 보고 대상에서 뺍니다. 백엔드 팀이 모듈을 추가하면 그 서비스부터 자동으로 CI 대상에 들어갑니다. 대상이 하나도 없으면 서비스 job은 건너뛰고 `backend-ci 결과`는 통과합니다.

**트리거에 `paths-ignore`를 두지 않습니다.** 두면 문서만 바꾼 PR에서 워크플로가 아예 실행되지 않아 필수 체크가 영원히 대기 상태가 되고, 머지가 막힙니다. 무시할 경로는 위 표처럼 `changed-services.sh` 한 곳에서만 판단합니다.

## 처음 한 번 설정 (GitHub 저장소)

1. **브랜치 보호 규칙** (`main`, Settings → Rules):
   - 필수 체크: **`backend-ci 결과`** 하나만 지정합니다. 서비스별 job은 변경이 없으면 건너뛰어지므로 개별 job을 필수로 지정하면 PR이 막힙니다.
   - 필수 승인: **1명** (CONTRIBUTING 규칙)
   - 체크 이름은 워크플로가 한 번 실행된 뒤에 선택 목록에 나타납니다.
2. 별도 Secret 등록은 필요 없습니다.

## 모듈 경로가 기본값과 다를 때
`.env`의 `*_MODULE`을 바꿨다면 `.github/backend-services.json`의 `module`도 같은 값으로 바꿔야 합니다.
예: `{ "name": "store", "module": "services/store" }`

## 구현 메모
- **amd64만 빌드**: 검사 목적이고 EKS 노드가 amd64 입니다. Dockerfile 은 멀티 아키텍처를 지원해 Mac(arm64)에서 로컬 빌드도 됩니다.
- **Action 고정 방식**: 모든 Action을 태그가 아닌 커밋 SHA로 고정했습니다. 2026년 3월 Trivy 관련 Action 공급망 사고(CVE-2026-33634) 때 태그가 악성 커밋으로 바뀐 사례가 있었습니다. 업데이트는 Dependabot(github-actions)로 받는 걸 권장합니다.
- **Gradle 캐시**: `actions/setup-java`의 `cache: gradle`을 씁니다. `gradle/actions/setup-gradle` v6의 기본 캐시는 상용 서비스라서 쓰지 않았습니다.
- **테스트 위치**: 테스트는 CI의 `test` job에서만 실행합니다. Dockerfile 빌드는 문서 규칙대로 `-x test`입니다.

## 확인이 필요한 것 (A와)
- [ ] `ignore-unfixed`: 지금은 **수정 버전이 없는 CRITICAL도 실패**로 처리합니다. 베이스 이미지(`eclipse-temurin`)에 패치 없는 CRITICAL이 생기면 머지가 막힙니다. "수정 가능한 것만 실패"로 바꿀지 정해 주세요.
- [ ] 예외 처리: 오탐이나 수용한 취약점은 루트에 `.trivyignore`를 두고 CVE ID와 사유를 적는 방식을 제안합니다.
