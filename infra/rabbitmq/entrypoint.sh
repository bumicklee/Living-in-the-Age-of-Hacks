#!/bin/bash
# 다온마켓 랩 · RabbitMQ 준비(아키텍처 A1, 수업 6-4). 서버가 뜬 직후 rabbitmqctl로 계정을 멱등하게 맞춘다.
# 기존 볼륨에는 이미지의 RABBITMQ_DEFAULT_USER가 먹지 않는다(첫 기동에만 쓰인다). 그래서 매번 같은 순서로 맞춘다:
#   ① 앱 계정(관리자 태그 없음, /mall 안에서만 권한)과 관리자 계정을 먼저 만든다
#   ② 그다음 기본 계정(guest·옛 데모 계정 mall)을 지운다   ← 순서가 바뀌면 앱이 큐에 못 붙는다
# 비밀번호는 환경 변수(랩 전용 가짜 값). 준비가 끝나면 /tmp/daon-rabbit-ready를 만든다(healthcheck가 본다).
set -u
rm -f /tmp/daon-rabbit-ready
: "${DAON_RABBIT_APP_PASSWORD:?}" "${DAON_RABBIT_OPS_PASSWORD:?}"
VHOST=/mall

docker-entrypoint.sh rabbitmq-server &
PID=$!
trap 'kill -TERM "$PID" 2>/dev/null; wait "$PID"; exit $?' TERM INT

until rabbitmqctl -q await_startup >/dev/null 2>&1; do
  kill -0 "$PID" 2>/dev/null || exit 1
  sleep 2
done

ctl() { rabbitmqctl -q "$@"; }
has_user() { ctl list_users --silent 2>/dev/null | cut -f1 | grep -qx "$1"; }
ensure_user() {      # 이름 비밀번호 태그
  if has_user "$1"; then ctl change_password "$1" "$2" >/dev/null; else ctl add_user "$1" "$2" >/dev/null; fi
  ctl set_user_tags "$1" $3 >/dev/null
}

ctl list_vhosts --silent 2>/dev/null | grep -qx "$VHOST" || ctl add_vhost "$VHOST" >/dev/null
ensure_user daon-portal "$DAON_RABBIT_APP_PASSWORD" ""
ctl set_permissions -p "$VHOST" daon-portal '.*' '.*' '.*' >/dev/null
ensure_user daon-ops "$DAON_RABBIT_OPS_PASSWORD" "administrator"
ctl set_permissions -p "$VHOST" daon-ops '.*' '.*' '.*' >/dev/null
for u in guest mall; do
  has_user "$u" && ctl delete_user "$u" >/dev/null
done
echo "[6-4] RabbitMQ 준비: 앱 계정 daon-portal(태그 없음, $VHOST만)·관리자 daon-ops, 기본 계정(guest·mall) 삭제"
touch /tmp/daon-rabbit-ready
wait "$PID"
