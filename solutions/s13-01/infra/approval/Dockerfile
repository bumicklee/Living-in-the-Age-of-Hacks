# syntax=docker/dockerfile:1
# 다온마켓 A10 승인 서비스(13장). 빌드 컨텍스트는 infra/approval/ 이다.
#   docker build -t daon/approval:dev infra/approval
# mall 이미지와 같은 공식 이미지(고정 버전, amd64·arm64)를 쓴다. 정책 파일(policy.yml)은 이미지에 굽는다:
# 정책을 바꾸면 이미지가 바뀌고 컨테이너가 다시 만들어진다(정책 변경이 「배포」라는 기록이 남는다).

FROM maven:3.9.12-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests && cp target/approval.jar /approval.jar

FROM eclipse-temurin:17.0.17_10-jre
RUN useradd --system --uid 10001 approval
WORKDIR /app
COPY --from=build /approval.jar app.jar
COPY policy.yml /app/policy.yml
USER approval
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1" \
    TZ=Asia/Seoul
EXPOSE 8097
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
