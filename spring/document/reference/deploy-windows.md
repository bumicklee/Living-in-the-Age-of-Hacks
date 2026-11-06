# windows환경 구축

## IDEA

- IDEA 설치와 사용은 다음을 참고하세요[https://github.com/judasn/IntelliJ-IDEA-Tutorial](https://github.com/judasn/IntelliJ-IDEA-Tutorial)
- 플러그인 저장소를 검색해 플러그인 설치lombok

## Eclipse

- 프로젝트 가져오기, maven 프로젝트로 import  
    ![eclipse_import_1.png](https://github.com/macrozheng/mall/blob/master/document/resource/eclipse_import_1.png)  
    ![eclipse_import_2.png](https://github.com/macrozheng/mall/blob/master/document/resource/eclipse_import_2.png)
- lombok 플러그인 설치, 다운로드 주소:https://projectlombok.org/downloads/lombok.jar  
- 다운로드가 끝나면 더블클릭하여 java 프로그램으로 열기
- 안내에 따라 eclipse.exe 설치 경로를 선택해 플러그인을 설치하고, 완료 후 재시작Eclipse
- 프로젝트 시작: com.macro.mall.MallAdminApplication의 main 메서드를 우클릭하고 다음을 선택run as Java Application

## mysql

- 다운로드 주소:https://dev.mysql.com/downloads/mysql/5.7.html#downloads
- 다운로드 후 안내에 따라 설치
- document/sql 아래의 mall.sql 파일 가져오기

## redis

- 다운로드 주소:https://github.com/MicrosoftArchive/redis/releases
- 다운로드 후 안내에 따라 설치
- 시작redis:redis-server.exe redis.windows.conf

## elasticsearch

- 다운로드 주소:https://www.elastic.co/downloads/past-releases/elasticsearch-6-2-2
- .zip 파일을 내려받아 지정한 디렉터리에 압축 해제
- kibana 설치: 브라우저에서 es에 접근하기 위한 것으로 6.2.2 버전을 다운로드, 자세한 내용은 다음 참고:https://www.elastic.co/downloads/kibana
- .zip 파일을 내려받아 압축을 풀면 됩니다. bin\kibana.bat을 실행한 뒤 http://localhost:5601 에 접속해 설치 여부를 확인하세요
- 중국어 형태소 분석 플러그인 주소:https://github.com/medcl/elasticsearch-analysis-ik
- 중국어 형태소 분석 플러그인 설치, elasticsearch-6.2.2\bin 디렉터리에서 다음 명령 실행:
elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v6.2.2/elasticsearch-analysis-ik-6.2.2.zip
- elasticsearch 시작: 실행elasticsearch-6.2.2\bin\elasticsearch.bat
- kibana를 사용하지 않는다면 head 플러그인을 설치할 수 있습니다. 자세한 내용은 다음을 참고하세요:https://github.com/mobz/elasticsearch-head
- 주의: mall-search에서 es의 cluster-name: mall-es를 수정했다면 elasticsearch-6.2.2\config\elasticsearch.yml 파일도 수정해야 함cluster.name: mall-es

## mongodb

- 다운로드 주소:https://fastdl.mongodb.org/win32/mongodb-win32-x86_64-2008plus-ssl-3.2.21-signed.msi
- 경로(c:\mongodb\)를 선택하여 설치하고, 설치 경로 아래에 data\db와 data\log 두 폴더를 생성
- 서버 실행 프로그램:mongodb\bin\mongod.exe
- 클라이언트 실행 프로그램:mongodb\bin\mongo.exe
- 설정 파일 생성:mongodb\mongod.cfg
    ``` lua
    systemLog:
        destination: file
        path: c:\mongodb\data\log\mongod.log
    storage:
        dbPath: c:\mongodb\data\db
    ```
- 서비스로 설치(명령 실행에 관리자 권한 필요):C:\mongodb\bin\mongod.exe --config "C:\mongodb\mongod.cfg" --install
- 서비스 시작:net start MongoDB
- 서비스 종료:net stop MongoDB
- 서비스 제거:C:\mongodb\bin\mongod.exe --remove
- 클라이언트 프로그램 다운로드:https://download.robomongo.org/1.2.1/windows/robo3t-1.2.1-windows-x86_64-3e50a65.zip
- 지정한 디렉터리에 압축을 풀고 robo3t.exe를 실행해 다음에 연결localhost:27017

## rabbitmq

- Erlang 설치:http://erlang.org/download/otp_win64_21.3.exe
- rabbitmq 다운로드:https://dl.bintray.com/rabbitmq/all/rabbitmq-server/3.7.14/rabbitmq-server-3.7.14.exe
- 안내에 따라 설치하고, 설치가 끝나면 rabbitmq 설치 디렉터리로 이동:D:\RabbitMQ Server\rabbitmq_server-3.7.14\sbin
- 주소 표시줄에 cmd를 입력하고 Enter를 눌러 명령줄을 연 뒤 다음 명령 입력:rabbitmq-plugins enable rabbitmq_management
- 접속 주소로 설치 성공 여부 확인:http://127.0.0.1:15672/
- 계정과 비밀번호를 입력해 로그인:guest guest
- 사용자를 생성하고 역할을 관리자로 설정:mall mall
    ![rabbitmq_install_2.png](https://github.com/macrozheng/mall/blob/master/document/resource/rabbitmq_install_2.png)
- 생성virtual host:/mall
    ![rabbitmq_install_3.png](https://github.com/macrozheng/mall/blob/master/document/resource/rabbitmq_install_3.png)
- mall 사용자에게 해당 virtual host의 권한 범위 설정
    ![rabbitmq_install_4.png](https://github.com/macrozheng/mall/blob/master/document/resource/rabbitmq_install_4.png)
- rabbitmq지연 메시지 플러그인 설치(생략 가능):
    - 지연 메시지 플러그인(rabbitmq_delayed_message_exchange) 다운로드:https://www.rabbitmq.com/community-plugins.html
    - 플러그인을 플러그인 디렉터리로 복사:D:\RabbitMQ Server\rabbitmq_server-3.7.14\plugins
    - sbin 디렉터리에서 다음 명령을 실행해 플러그인을 활성화:rabbitmq-plugins enable rabbitmq_delayed_message_exchange
    
## OSS

- 이 프로젝트의 파일 업로드는 OSS를 사용하므로 OSS 계정을 직접 가입하고 설정해야 합니다
- 먼저 mall-admin\src\main\resources\application.properties 파일에서 aliyun.oss.로 시작하는 설정을 본인의 설정으로 변경하세요
- OSS파일 업로드를 사용하려면 교차 출처 리소스 공유(CORS) 규칙을 설정해야 합니다. 참고 문서:https://help.aliyun.com/document_detail/31928.html
- 업로드 방식은 서버에서 서명한 후 바로 업로드하는 형태입니다. 참고 문서:https://help.aliyun.com/document_detail/31926.html

## mall-admin

- 프로젝트 시작: com.macro.mall.MallAdminApplication의 main 메서드를 바로 실행하면 됩니다
- API 문서 주소:http://localhost:8080/swagger-ui.html

## mall-search

- 프로젝트 시작: com.macro.mall.search.MallSearchApplication의 main 메서드를 바로 실행하면 됩니다
- API 문서 주소:http://localhost:8081/swagger-ui.html
- 사용하기 전에 먼저 API를 호출해 데이터를 가져와야 합니다;http://localhost:8081/esProduct/importAll
- 시작되지 않는 문제가 발생하면 elasticsearch 안의 데이터를 먼저 삭제한 뒤 시작

## mall-portal

- mall-portal 프로젝트 시작: com.macro.mall.portal.MallPortalApplication의 main 메서드를 바로 실행하면 됩니다
- API 문서 주소:http://localhost:8085/swagger-ui.html
