#!/bin/bash
# 다온마켓 14-2 · A11 준비(한 번 돌고 끝남, 몇 번을 돌려도 같은 결과)
#
#   ① 잠금 버킷 daon-backup: 만들 때 객체 잠금을 켠다(나중에는 켤 수 없다) → 기본 보존(모드·기간) → 수명 주기 규칙(보존이 끝난 뒤 정리)
#   ② 정책 둘·계정 둘: backup-writer(올리기만) · backup-restorer(읽기만). 비밀 키는 처음 한 번 만들어 전용 볼륨에만 둔다(값은 찍지 않는다)
#   ③ 백업 전용 DB 계정 daon_backup(mall 읽기만). DB가 없는 스택(Express)에서는 건너뛴다
#   ④ 옛 [회사 설정] 백업(앱 버킷 mall/backups/)을 잠금 버킷 legacy/로 옮기고 앱 버킷에서 지운다
# 루트 계정(backup-vault·앱 MinIO·MySQL)은 이 스크립트만 쓴다. 백업 작업·앱에는 주지 않는다.
set -euo pipefail
umask 077
log() { echo "[A11] $*"; }
secret() { head -c 18 /dev/urandom | od -An -tx1 | tr -d ' \n'; }

# ── ① 잠금 버킷 ──
export MC_HOST_vault="http://${VAULT_ROOT_USER}:${VAULT_ROOT_PASSWORD}@backup-vault:9000"
for i in $(seq 1 60); do mc ls vault >/dev/null 2>&1 && break; sleep 1; done
mc mb --ignore-existing --with-lock vault/daon-backup >/dev/null
if ! mc retention info --default vault/daon-backup 2>/dev/null | grep -qi "${DAON_BACKUP_LOCK_MODE}"; then
  mc retention set --default "${DAON_BACKUP_LOCK_MODE}" "${DAON_BACKUP_RETENTION}" vault/daon-backup >/dev/null
fi
if ! mc ilm rule ls vault/daon-backup 2>/dev/null | grep -q "Expiration\|expir"; then
  mc ilm rule add --expire-days "${DAON_BACKUP_EXPIRE_DAYS}" --noncurrent-expire-days "${DAON_BACKUP_EXPIRE_DAYS}" vault/daon-backup >/dev/null \
    || log "수명 주기 규칙을 더하지 못했습니다(보존이 끝난 백업은 남습니다. make wipe HARD=1로 볼륨째 지웁니다)"
fi
log "잠금 버킷 daon-backup: 객체 잠금 켬 · 기본 보존 ${DAON_BACKUP_LOCK_MODE} ${DAON_BACKUP_RETENTION}"

# ── ② 정책·계정 ──
mc admin policy create vault backup-writer /backup/policy-writer.json >/dev/null
mc admin policy create vault backup-restorer /backup/policy-restorer.json >/dev/null
for who in writer restorer; do
  dir=/creds/$who
  if [ ! -s "$dir/secret_key" ]; then
    echo "backup-$who" > "$dir/access_key"
    secret > "$dir/secret_key"
  fi
  mc admin user add vault "backup-$who" "$(cat "$dir/secret_key")" >/dev/null
  mc admin policy attach vault "backup-$who" --user "backup-$who" >/dev/null 2>&1 || true   # 이미 붙어 있으면 오류라서 넘어간다
done
chown -R 999:999 /creds/writer && chmod 0500 /creds/writer && chmod 0400 /creds/writer/*       # 백업 작업(mysql 사용자)만 읽는다
chmod 0500 /creds/restorer && chmod 0400 /creds/restorer/*                                         # 복구 훈련 때만 root로 붙인다
log "계정 backup-writer(올리기만)·backup-restorer(읽기만). 지우기 권한을 가진 사용자는 없음"

# ── ③ 백업 전용 DB 계정 ──
if getent hosts db >/dev/null 2>&1; then
  if [ ! -s /creds/db/password ]; then
    echo daon_backup > /creds/db/user
    secret > /creds/db/password
  fi
  for i in $(seq 1 60); do MYSQL_PWD="$MYSQL_ROOT_PASSWORD_FOR_INIT" mysql -h db -uroot -e 'SELECT 1' >/dev/null 2>&1 && break; sleep 2; done
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD_FOR_INIT" mysql -h db -uroot <<SQL
CREATE USER IF NOT EXISTS 'daon_backup'@'%' IDENTIFIED BY '$(cat /creds/db/password)';
ALTER USER 'daon_backup'@'%' IDENTIFIED BY '$(cat /creds/db/password)';
GRANT SELECT, SHOW VIEW, TRIGGER, LOCK TABLES ON mall.* TO 'daon_backup'@'%';
FLUSH PRIVILEGES;
SQL
  chown -R 999:999 /creds/db && chmod 0500 /creds/db && chmod 0400 /creds/db/*
  log "백업 전용 DB 계정 daon_backup(mall 읽기만)"
fi

# ── ④ 옛 [회사 설정] 백업 옮기기 ──
if getent hosts minio >/dev/null 2>&1; then
  export MC_HOST_app="http://${APP_MINIO_ROOT_USER}:${APP_MINIO_ROOT_PASSWORD}@minio:9000"
  n=$(mc ls --recursive app/mall/backups/ 2>/dev/null | wc -l | tr -d ' ')
  if [ "${n:-0}" -gt 0 ]; then
    mc mirror --quiet app/mall/backups/ vault/daon-backup/legacy/ >/dev/null
    mc rm --recursive --force app/mall/backups/ >/dev/null
    log "옛 백업 ${n}개를 잠금 버킷 legacy/로 옮기고 앱 버킷 backups/에서 지웠습니다"
  fi
fi
log "준비 끝"
