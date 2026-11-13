#!/bin/sh
# 다온마켓 랩 · MongoDB 준비(아키텍처 A1, 수업 6-4). 컨테이너가 뜰 때마다 한 번 돌고 인증을 켠 서버로 바뀐다.
# 기존 볼륨(인증 없이 만들어진 데이터)에서도 같게 동작해야 하므로 이미지의 MONGO_INITDB_* 방식(빈 볼륨에서만 동작)을 쓰지 않는다.
#   1) 인증 없이, 이 컨테이너 안(127.0.0.1:27018)에서만 잠깐 서버를 띄운다(밖에서는 닿지 않는다)
#   2) 계정을 멱등하게 맞춘다: 앱 계정(mall-port DB 읽기·쓰기만), 랩 도구 계정(관리용)
#   3) 잠깐 띄운 서버를 내리고, 인증(--auth)을 켠 진짜 서버를 띄운다
# 비밀번호는 환경 변수로만 들어온다(랩 전용 가짜 값). 이 파일에는 적지 않는다.
set -eu
: "${DAON_MONGO_APP_PASSWORD:?}" "${DAON_MONGO_OPS_PASSWORD:?}"
CACHE="${DAON_MONGO_CACHE_GB:-0.25}"

gosu mongodb mongod --fork --bind_ip 127.0.0.1 --port 27018 --dbpath /data/db --logpath /tmp/mongod-prep.log \
  --wiredTigerCacheSizeGB "$CACHE" >/dev/null
mongosh --quiet --port 27018 admin --eval '
  const a = db.getSiblingDB("admin");
  function ensure(user, pwd, roles) {
    if (a.getUser(user)) { a.updateUser(user, { pwd: pwd, roles: roles }); } else { a.createUser({ user: user, pwd: pwd, roles: roles }); }
  }
  ensure("daon-portal", process.env.DAON_MONGO_APP_PASSWORD, [{ role: "readWrite", db: "mall-port" }]);
  ensure("lab-ops", process.env.DAON_MONGO_OPS_PASSWORD, [{ role: "root", db: "admin" }]);
' >/dev/null
mongosh --quiet --port 27018 admin --eval 'db.shutdownServer()' >/dev/null 2>&1 || true
while pgrep -x mongod >/dev/null 2>&1; do sleep 1; done
echo "[6-4] MongoDB 준비: 인증 켬, 계정 daon-portal(mall-port 읽기·쓰기)·lab-ops(랩 도구)"
exec docker-entrypoint.sh mongod --auth --bind_ip_all --wiredTigerCacheSizeGB "$CACHE"
