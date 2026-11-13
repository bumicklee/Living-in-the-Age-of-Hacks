#!/bin/bash
# 다온마켓 14장 시작 모습: [회사 설정] DB 백업 한 번(backup-job 컨테이너가 5분마다 부른다. 지금 한 번: make backup-now)
#
# ✏️ 14-2에서 고칠 것(정답: make solution L=14-2)
#   ① 앱과 같은 MinIO 계정(MINIO_ACCESS_KEY)·같은 버킷(app/mall/backups/)에 쓴다 → 백업 전용 저장소 backup-vault, 올리기만 되는 backup-writer
#   ② DB root 계정으로 덤프한다 → 읽기만 되는 백업 전용 DB 계정
#   ③ set -e만 있고 pipefail이 없다 → mysqldump가 실패해도 gzip이 성공하면 「성공」으로 끝나고 빈 백업이 올라간다
#   ④ 해시가 없다 → 복구할 때 이 파일이 올린 그대로인지 알 수 없다
set -e
TS=$(date -u +%Y-%m-%dT%H%M%SZ)
OUT=/tmp/mall-$TS.sql.gz
MYSQL_PWD=root mysqldump --host db --user root --single-transaction --no-tablespaces --set-gtid-purged=OFF mall | gzip > "$OUT"
export MC_HOST_app="http://${MINIO_ACCESS_KEY}:${MINIO_SECRET_KEY}@minio:9000"
mc cp --quiet "$OUT" "${TARGET}mall-$TS.sql.gz" >/dev/null
rm -f "$OUT"
echo "[14장 시작 모습] 백업 올림: ${TARGET}mall-$TS.sql.gz (앱 계정·앱 버킷)"
