# 다온마켓 14장 백업 작업 이미지: MySQL 클라이언트(mysqldump) + MinIO 클라이언트(mc).
# mc는 정적 빌드라 MinIO 이미지(랩이 이미 쓰는 판)에서 꺼내 그대로 옮긴다. 스크립트(backup.sh 등)는 이미지에 굽지 않고 compose가 붙인다.
FROM pgsty/minio:RELEASE.2026-08-04T00-00-00Z AS mc
RUN cp "$(command -v mc)" /tmp/mc

FROM mysql:8.4.13
COPY --from=mc /tmp/mc /usr/local/bin/mc
ENV MC_CONFIG_DIR=/tmp/.mc
USER mysql
