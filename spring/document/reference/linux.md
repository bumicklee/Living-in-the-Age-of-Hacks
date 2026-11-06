# Linux주요 명령어

## 시스템 서비스 관리

### systemctl

- 서비스 시작:systemctl start httpd.service
- 서비스 종료:systemctl stop httpd.service
- 서비스 재시작(실행 여부와 관계없이):systemctl restart httpd.service
- 설정 다시 불러오기(서비스 중단 없이):systemctl reload httpd.service
- 실행 상태 확인:systemctl status httpd.service
- 부팅 시 자동 시작 설정:systemctl enable httpd.service
- 부팅 시 자동 시작 금지:systemctl disable httpd.service
- 시스템에 설치된 서비스 확인:systemctl list-units --type=service

## 파일 관리

### ls
/home 디렉터리 아래 하위 디렉터리 나열:ls -l /home
현재 폴더 아래 모든 폴더와 파일 크기 나열:ls -lht 

### pwd
현재 작업 디렉터리 표시

### cd
디렉터리 이동: cd /usr/local

### date
지정한 형식으로 날짜 표시;date '+date:%x time:%X'

### passwd
root 비밀번호 변경:passwd root

### su
일반 사용자에서 슈퍼유저로 전환:su -

### clear
화면 정보 지우기

### man
ls 명령어 도움말 확인:man ls

### who
- 현재 런레벨 확인:who -r
- 화면에 표시할 로그인 상세:who -buT

### free
메모리 사용 상태를 MB 단위로 표시:free -m

### ps
시스템의 모든 프로세스 확인:ps -ef
실행 중인 java 프로세스 확인: ps -ef | grep java

### top
시스템에서 현재 활성 프로세스 정보 확인

### mkdir
디렉터리 생성

### more 

페이지별 조회  
10행씩 한 화면으로 보기:more -c -10 

### cat
config 파일 확인:cat -Ab config

### rm
- 파일 삭제:rm a.txt
- 폴더 삭제: rm -rf a/

### touch
파일 생성:touch a.txt

### cp
디렉터리 a의 파일을 다음 디렉터리로 복사:b: cp -r /home/a /home/b

### mv
파일 이동 또는 덮어쓰기:mv a.txt b.txt

## 압축 및 압축 해제

### tar
- 폴더를 별도 파일로 압축:tar -cvf /opt/etc.tar /etc
- 폴더를 압축 파일로 압축(gzip):tar -zcvf /opt/etc.tar.gz /etc
- 폴더를 압축 파일로 압축(bzip2):tar -jcvf /opt/etc.tar.bz2 /etc
- 압축 파일 내용 확인(gzip):tar -ztvf /opt/etc.tar.gz /etc
- 현재 디렉터리에 파일 압축 해제(gzip):tar -zxvf /opt/etc.tar.gz

## 디스크 및 네트워크 관리

### df
디스크 사용량 확인:df -hT

### ifconfig
현재 네트워크 인터페이스 상태 확인

### netstat

- 라우트 정보 확인:netstat -rn
- 유효한 모든 TCP 연결 확인:netstat -an
- 시스템에서 실행 중인 리스닝 서비스 확인:netstat -tulnp
- 연결 상태인 시스템 리소스 정보 확인:netstat -atunp

### wget
네트워크에서 소프트웨어 다운로드

## 소프트웨어 설치 및 관리

### rpm

- 패키지 설치:rpm -ivh nginx-1.12.2-2.el7.x86_64.rpm
- 패키지 모호 검색:rpm -qa | grep nginx
- 패키지 정확히 검색:rpm -qa nginx
- 패키지 설치 경로 조회:rpm -ql nginx-1.12.2-2.el7.x86_64
- 패키지 개요 정보 확인:rpm -qi nginx-1.12.2-2.el7.x86_64
- 패키지 내용과 설치 파일이 일치하는지 검증:rpm -V nginx-1.12.2-2.el7.x86_64
- 패키지 업데이트:rpm -Uvh nginx-1.12.2-2.el7.x86_64
- 패키지 삭제:rpm -e nginx-1.12.2-2.el7.x86_64

### yum

- 패키지 설치: yum install nginx
- 업데이트 가능한 패키지 확인:yum check-update
- 지정한 패키지 업데이트:yum update nginx
- 저장소에서 패키지 정보 검색:yum info nginx*
- 설치된 모든 패키지 나열:yum info installed
- 패키지 이름 나열:yum list redis*
- 패키지 모호 검색:yum search redis

## 네트워크 보안

### iptables

- 방화벽 켜기:systemctl start iptables.service
- 방화벽 종료:systemctl stop iptables.service
- 방화벽 상태 확인:systemctl status iptables.service
- 부팅 시 자동 시작 설정:systemctl enable iptables.service
- 부팅 시 자동 시작 비활성화:systemctl disable iptables.service
- filter 테이블의 체인 정보 확인:iptables -L -n
- NAT 테이블의 체인 정보 확인:iptables -t nat -L -n
- 방화벽 규칙 모두 삭제:iptables -F;iptables -X;iptables -Z;
- 필터 규칙 추가(80 포트 개방):iptables -I INPUT -p tcp --dport 80 -j ACCEPT
- 규칙이 있는 행 번호 찾기:iptables -L INPUT --line-numbers -n
- 행 번호로 필터 규칙 삭제:iptables -D INPUT 1



