# Docker입문 매뉴얼

## Docker 이미지 자주 쓰는 명령
### 이미지 검색
docker search java
### 이미지 다운로드
- docker pull java:8
- docker pull macro/eureka-server:0.0.1
### 이미지 나열
docker images
### 이미지 삭제
- docker rmi java
- docker rmi -f java 
- docker rmi -f $(docker images)

## Docker 컨테이너 주요 명령어
### 새 컨테이너 생성 및 시작
docker run -d -p 91:80 nginx
### 컨테이너 나열
docker ps
### 컨테이너 중지
docker stop $ContainerId
### 컨테이너 강제 중지
docker kill $ContainerId
### 중지된 컨테이너 시작
docker start $ContainerId
### 컨테이너 진입
- docker inspect --format "{{.State.Pid}}" $ContainerId
- nsenter --target "$pid" --mount --uts --ipc --net --pid
### 컨테이너 삭제
- docker rm $ContainerId
- docker rm -f $(docker ps -a -q)
### 시작 오류 로그 확인
docker logs $ContainerIdName(또는$ContainerId)
### 컨테이너 IP 주소 확인(172.17.0.*)
docker inspect --format '{{ .NetworkSettings.IPAddress }}' $ContainerId
### 호스트 시간을 컨테이너와 동기화
docker cp /etc/localtime $ContainerName:/etc/
### 호스트에서 docker의 CPU, 메모리, 네트워크, IO 사용 현황 확인
- 지정한 컨테이너 상태 확인:docker stats $ContainerName
- 모든 컨테이너 상태 확인:docker stats -a
### docker 내부로 진입bash
docker exec -it $ContainerName /bin/bash

## Docker Registry
### Docker Registry 2.0구축
docker run -d -p 5000:5000 --restart=always --name registry2 registry:2
### 프라이빗 저장소에 푸시
docker push localhost:5000/macro/eureka-server:0.0.1
### 이미지 태그 수정
docker tag macro/eureka-server:0.0.1 localhost:5000/macro/eureka-server:0.0.1

## maven으로 Docker 이미지 빌드
### 이미지 빌드
- command:mvn clean package docker:build
- tip:
    Linux서버에서 원격 접속을 허용해야 함api:vi /usr/lib/systemd/system/docker.service
    다음으로 변경:ExecStart=/usr/bin/dockerd -H tcp://0.0.0.0:2375 -H unix://var/run/docker.sock   
### 이미지를 프라이빗 저장소에 푸시
- command:mvn clean package docker:build -DpushImage
- tip:
    pom.xml수정<imageName>192.168.1.71:5000/macro/${project.artifactId}:${project.version}</imageName>
- tip:
    docker지원하려면http:echo '{ "insecure-registries":["39.98.190.128:5000"] }' > /etc/docker/daemon.json 
### Docker 이미지 저장 위치 변경
1. Docker 저장 위치 확인:docker info | grep "Docker Root Dir"(기본값은 /var/lib/docker)
2. Docker 서비스 종료:systemctl stop docker
3. 디렉터리를 대상 경로로 이동:mv /var/lib/docker /root/data/docker
4. 심볼릭 링크 생성:ln -s /root/data/docker /var/lib/docker

## Docker compose
### 설치
1. 다운로드 주소:https://github.com/docker/compose/releases
2. 설치 주소:/usr/local/bin/docker-compose
3. 실행 가능하도록 설정:sudo chmod +x /usr/local/bin/docker-compose
4. 설치 성공 여부 테스트:docker-compose --version

### 명령어 자동완성 도구 설치
sudo curl -L https://raw.githubusercontent.com/docker/compose/1.22.0/contrib/completion/bash/docker-compose -o /etc/bash_completion.d/docker-compose

### 주요 명령어
- 관련 컨테이너 빌드, 생성, 시작:docker-compose up
- 모든 컨테이너 나열:docker-compose ps
- 지정한 서비스의 컨테이너 삭제:docker-compose rm eureka
- 컨테이너 동적 확장:docker-compose scale eureka=3
- 관련 컨테이너 중지:docker-compose stop eureka
- 관련 컨테이너 시작:docker-compose start eureka

### SpringCloud 마이크로서비스 오케스트레이션
#### 사용된 프로젝트
- eureka-server
- hello-service
- feign-consumer
- api-gateway
#### 오케스트레이션 모드
1. SpringCloud 마이크로서비스 오케스트레이션: 참고eureka-server/docker-res/docker-compose.yml
2. SpringCloud 마이크로서비스 오케스트레이션 간소화: 참고eureka-server/docker-res/docker-compose-simple.yml
3. 고가용성 레지스트리 구성: 참고eureka-server/docker-res/docker-compose-eureka.yml