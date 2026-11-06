#!/usr/bin/env bash
# 애플리케이션 그룹명 정의
group_name='mall'
# 애플리케이션 이름 정의
app_name='mall-admin'
# 애플리케이션 버전 정의
app_version='1.0-SNAPSHOT'
# 애플리케이션 환경 정의
profile_active='prod'
echo '----copy jar----'
docker stop ${app_name}
echo '----stop container----'
docker rm ${app_name}
echo '----rm container----'
docker rmi ${group_name}/${app_name}:${app_version}
echo '----rm image----'
# docker 이미지 패키징 및 빌드
docker build -t ${group_name}/${app_name}:${app_version} .
echo '----build image----'
docker run -p 8080:8080 --name ${app_name} \
--link mysql:db \
--link redis:redis \
-e 'spring.profiles.active'=${profile_active} \
-e TZ="Asia/Shanghai" \
-v /etc/localtime:/etc/localtime \
-v /mydata/app/${app_name}/logs:/var/logs \
-d ${group_name}/${app_name}:${app_version}
echo '----start container----'