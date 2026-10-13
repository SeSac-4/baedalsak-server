# syntax=docker/dockerfile:1.7
# =============================================================================
# Spring Boot 공통 Dockerfile 템플릿 (Gradle · Java 21 · Spring Boot 3.3+)
# 폴더 구조와 무관하게 동작: 빌드할 모듈 경로만 MODULE 인자로 넘김
#
#   위치: 저장소 루트 (gradlew 옆), 빌드도 항상 루트에서 실행
#
#   multi-module:
#     docker build --build-arg MODULE=store          -t baedal/store:local .
#     docker build --build-arg MODULE=services/store -t baedal/store:local .
#   single-module (MODULE 생략):
#     docker build -t baedal/store:local .
#
# 전제 두 가지
#   1) gradlew 와 settings.gradle 이 저장소 루트에 있음
#   2) Gradle 프로젝트 경로가 폴더 경로와 같음 (services/store → :services:store)
#      settings.gradle 기본 include 방식이면 자동으로 맞음
#
# 구조
#   build   : JDK로 해당 모듈 bootJar 생성 (테스트는 CI에서 따로 실행)
#   extract : jar를 레이어(의존성 / 로더 / 스냅샷 / 애플리케이션)로 분리
#   runtime : JRE + 비root 사용자로 실행
# =============================================================================

ARG JAVA_VERSION=21

# ---------- 1) build ----------
FROM eclipse-temurin:${JAVA_VERSION}-jdk AS build
ARG MODULE=""
WORKDIR /workspace

# multi-module은 공통 모듈을 함께 컴파일해야 하므로 루트 전체를 복사
# (불필요한 파일은 .dockerignore로 제외, 의존성은 Gradle 캐시 마운트로 재사용)
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    set -eu; \
    chmod +x gradlew; \
    if [ -n "$MODULE" ]; then \
      [ -d "$MODULE" ] || { echo "모듈 폴더가 없습니다: $MODULE"; exit 1; }; \
      task=":$(echo "$MODULE" | tr '/' ':'):bootJar"; \
      libs="$MODULE/build/libs"; \
    else \
      task="bootJar"; \
      libs="build/libs"; \
    fi; \
    ./gradlew "$task" -x test --no-daemon; \
    jars=$(find "$libs" -maxdepth 1 -name '*.jar' ! -name '*-plain.jar'); \
    [ "$(echo "$jars" | grep -c .)" -eq 1 ] || { echo "실행용 jar가 1개가 아닙니다: $jars"; exit 1; }; \
    cp "$jars" /workspace/app.jar

# ---------- 2) extract ----------
FROM eclipse-temurin:${JAVA_VERSION}-jre AS extract
WORKDIR /extract
COPY --from=build /workspace/app.jar app.jar
# Spring Boot 3.3+ 방식. 3.2 이하라면: java -Djarmode=layertools -jar app.jar extract
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---------- 3) runtime ----------
FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime

# 비root 사용자 (K8s securityContext의 runAsNonRoot와 맞춤)
RUN groupadd --system --gid 1001 spring \
 && useradd --system --uid 1001 --gid spring --no-create-home spring

WORKDIR /app

# 자주 바뀌지 않는 레이어부터 복사 → 코드만 바뀌면 마지막 레이어만 다시 push
COPY --from=extract --chown=spring:spring /extract/extracted/dependencies/ ./
COPY --from=extract --chown=spring:spring /extract/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=spring:spring /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=spring:spring /extract/extracted/application/ ./

USER spring:spring

# 컨테이너 메모리 한도 기준으로 힙 크기 결정, OOM 시 즉시 종료 → K8s가 재시작
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    SERVER_PORT=8080

EXPOSE 8080

# exec 형식: Java가 PID 1이 되어 SIGTERM을 직접 받음 → graceful shutdown 가능
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]