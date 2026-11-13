#!/bin/bash
# 다온마켓 14-2 정답 · DB 백업 한 번(backup-job이 5분마다 부른다. 지금 한 번: make backup-now)
#
#   MySQL 덤프(백업 전용 읽기 계정) → 압축 → sha256 → 잠금 버킷 daon-backup에 올림(backup-writer, 올리기만)
#   해시와 덤프 시작 시각(복구 시점, UTC)을 객체 메타데이터로 남긴다: X-Amz-Meta-Sha256 · X-Amz-Meta-Snapshot-At
#   한 단계라도 실패하면 0이 아닌 값으로 끝나고 아무것도 올리지 않는다. 실패 이벤트는 따로 남기지 않는다:
#   새 백업이 기준 시간 안에 안 생기면 backup-audit가 backup.missed를 남긴다(조용히 멈춘 백업도 잡는다).
# 비밀번호·비밀 키는 프로세스 인자로 넘기지 않는다(MYSQL_PWD·MC_HOST_ 환경 변수, 이 스크립트 안에서만).
set -euo pipefail
umask 077
TS=$(date -u +%Y-%m-%dT%H%M%SZ)
SNAPSHOT_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
WORK=$(mktemp -d)
trap 'rm -rf -- "$WORK"' EXIT
NAME="mall-$TS.sql.gz"
OUT="$WORK/$NAME"

MYSQL_PWD="$(cat "$DB_CREDS_DIR/password")" mysqldump --host db --user "$(cat "$DB_CREDS_DIR/user")" \
  --single-transaction --no-tablespaces --set-gtid-purged=OFF --triggers mall | gzip > "$OUT"
[ -s "$OUT" ] || { echo "[14-2] 덤프가 비었습니다" >&2; exit 1; }
SUM=$(sha256sum "$OUT" | cut -d' ' -f1)

export MC_HOST_vault="http://$(cat "$WRITER_DIR/access_key"):$(cat "$WRITER_DIR/secret_key")@${VAULT_URL}"
mc cp --quiet --attr "sha256=${SUM};snapshot-at=${SNAPSHOT_AT}" "$OUT" "${TARGET}${NAME}" >/dev/null
echo "[14-2] 백업 올림: ${TARGET}${NAME} · sha256 ${SUM:0:12}… · 복구 시점 ${SNAPSHOT_AT} (잠금 버킷, backup-writer)"
