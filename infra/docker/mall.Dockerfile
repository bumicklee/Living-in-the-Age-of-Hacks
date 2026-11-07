# syntax=docker/dockerfile:1
# 다온마켓 Spring 스택(mall 포크) 이미지. 빌드 컨텍스트는 spring/ 이다.
# 한 번의 Maven 빌드로 mall-admin·mall-portal 두 jar를 만들고, 마지막 단계(target)만 다르게 고른다.
#   docker build -f infra/docker/mall.Dockerfile --target mall-portal spring/
# 이미지 둘 다 amd64·arm64가 있는 공식 이미지를 고정 버전으로 쓴다.

FROM maven:3.9.12-eclipse-temurin-17 AS build
WORKDIR /src
# 의존성 먼저(pom만 바뀌지 않으면 캐시를 다시 쓴다)
COPY pom.xml ./
COPY mall-common/pom.xml mall-common/
COPY mall-mbg/pom.xml mall-mbg/
COPY mall-security/pom.xml mall-security/
COPY mall-demo/pom.xml mall-demo/
COPY mall-admin/pom.xml mall-admin/
COPY mall-search/pom.xml mall-search/
COPY mall-portal/pom.xml mall-portal/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl mall-admin,mall-portal -am dependency:go-offline -Ddocker.skip=true || true
COPY . .
# docker.skip: 원본 pom의 docker-maven-plugin이 원작자 서버(192.168.3.101)로 가지 않게
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl mall-admin,mall-portal -am package -DskipTests -Ddocker.skip=true \
 && cp mall-admin/target/mall-admin-*.jar /mall-admin.jar \
 && cp mall-portal/target/mall-portal-*.jar /mall-portal.jar

FROM eclipse-temurin:17.0.17_10-jre AS runtime
RUN useradd --system --uid 10001 mall && mkdir -p /var/logs && chown mall /var/logs
WORKDIR /app
USER mall
# 컨테이너 mem_limit 안에서 힙을 잡는다(compose의 mem_limit 기준 70%)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1" \
    TZ=Asia/Seoul

FROM runtime AS mall-admin
COPY --from=build /mall-admin.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS mall-portal
COPY --from=build /mall-portal.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
