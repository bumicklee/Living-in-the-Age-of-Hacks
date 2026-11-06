# 이 이미지가 의존하는 베이스 이미지
FROM openjdk:17
# 현재 디렉터리의 jar 파일을 docker 컨테이너의 / 디렉터리로 복사
ADD mall-admin-1.0-SNAPSHOT.jar /mall-admin-1.0-SNAPSHOT.jar
# 서비스가 8080 포트에서 실행됨을 선언
EXPOSE 8080
# docker 컨테이너 시작 시 실행할 jar 파일 지정
ENTRYPOINT ["java", "-jar","/mall-admin-1.0-SNAPSHOT.jar"]
# 유지 관리자 이름 지정
MAINTAINER macro