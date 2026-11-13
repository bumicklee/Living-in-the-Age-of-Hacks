#!/bin/sh
# 다온마켓 랩 · A10 승인 서비스 준비(13-3). docker compose가 up 때마다 한 번 돌린다(openbao-init 다음).
#
#   ① 승인 서명 키 transit/keys/daon-approval(ECDSA P-256). 랩을 처음 띄울 때 금고 안에서 만든다. 키는 금고 밖으로 나오지 않는다
#      (exportable 아님). 저장소에도, 이 스크립트에도 키 값은 없다.
#   ② 정책 둘:  daon-approval         = 서명(transit/sign/daon-approval) + 관리자 로그인 토큰 공개키 읽기 + 전용 DB 계정
#              daon-approval-verify  = 확인(transit/verify/daon-approval)만. 관리자 앱(AppRole daon-admin)에 붙인다
#   ③ 전용 DB 역할 approval: 주문·반품·관리자 표는 읽기만, daon_approval_* 두 표만 쓰기(6-3의 앱별 최소 권한)
#      15-2: 주문 작업 기록(oms_order_operate_history) 읽기를 더한다 — 「받는 사람을 바꾼 뒤 72시간 안 환불은 두 사람」 규칙
#   ④ AppRole daon-approval 접속 정보를 승인 서비스 전용 볼륨(/approval-creds)에만 쓴다. 관리자 앱에는 이 볼륨이 붙지 않는다
#
# 루트 토큰은 openbao-init이 랩 볼륨(bao-lab)에 둔 것을 읽기만 한다(랩 전용. 회사에서는 사람이 나눠 가진 키로 한 번만 한다).
set -eu
export BAO_ADDR=http://openbao:8200
OUT=/approval-creds
mkdir -p "$OUT"
ROOT=$(tr -d '\n ' < /bao-lab/init.json | sed -n 's/.*"root_token":"\([^"]*\)".*/\1/p')
[ -n "$ROOT" ] || { echo "[A10] 금고 루트 토큰을 읽지 못했습니다(openbao-init 먼저)"; exit 1; }
export BAO_TOKEN="$ROOT"

# ① 서명 키
bao read transit/keys/daon-approval >/dev/null 2>&1 || bao write -f transit/keys/daon-approval type=ecdsa-p256 >/dev/null

# ③ DB 역할. 연결 설정의 allowed_roles에 approval을 더한다(openbao-init은 설정이 없을 때만 만들므로 여기서 다시 쓴다)
bao write database/config/mysql plugin_name=mysql-database-plugin \
  connection_url="{{username}}:{{password}}@tcp(db:3306)/" allowed_roles="portal,admin,approval" \
  username=openbao password="$DAON_BAO_DB_PASSWORD" username_template='v-{{.RoleName}}-{{random 8}}' >/dev/null
bao write database/roles/approval db_name=mysql default_ttl=1h max_ttl=24h \
  creation_statements="CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; GRANT SELECT ON mall.oms_order TO '{{name}}'@'%'; GRANT SELECT ON mall.oms_order_return_apply TO '{{name}}'@'%'; GRANT SELECT ON mall.oms_order_operate_history TO '{{name}}'@'%'; GRANT SELECT ON mall.ums_admin TO '{{name}}'@'%'; GRANT SELECT ON mall.ums_admin_role_relation TO '{{name}}'@'%'; GRANT SELECT ON mall.ums_role TO '{{name}}'@'%'; GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON mall.daon_approval_request TO '{{name}}'@'%'; GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON mall.daon_approval_breaker TO '{{name}}'@'%';" \
  revocation_statements="DROP USER IF EXISTS '{{name}}'@'%';" >/dev/null

# ② 정책
cat > /tmp/daon-approval.hcl <<'P'
path "transit/sign/daon-approval"   { capabilities = ["update"] }
path "transit/keys/daon-approval"   { capabilities = ["read"] }
path "transit/keys/daon-jwt"        { capabilities = ["read"] }
path "database/creds/approval"      { capabilities = ["read"] }
path "sys/leases/renew"             { capabilities = ["update"] }
P
bao policy write daon-approval /tmp/daon-approval.hcl >/dev/null
cat > /tmp/daon-approval-verify.hcl <<'P'
path "transit/verify/daon-approval" { capabilities = ["update"] }
P
bao policy write daon-approval-verify /tmp/daon-approval-verify.hcl >/dev/null
# 관리자 앱 AppRole에 「확인만」 정책을 더한다(openbao-init이 매번 역할을 다시 쓰므로 그 다음에 다시 더한다)
bao write auth/approle/role/daon-admin token_policies=daon-app,daon-secrets-admin,daon-approval-verify \
  token_ttl=1h token_max_ttl=24h secret_id_ttl=0 >/dev/null

# ④ 승인 서비스 AppRole
bao write auth/approle/role/daon-approval token_policies=daon-approval token_ttl=1h token_max_ttl=24h secret_id_ttl=0 >/dev/null
bao read -field=role_id auth/approle/role/daon-approval/role-id > "$OUT/approval-role-id"
[ -s "$OUT/approval-secret-id" ] || bao write -f -field=secret_id auth/approle/role/daon-approval/secret-id > "$OUT/approval-secret-id"
chown 10001:10001 "$OUT"/approval-role-id "$OUT"/approval-secret-id
chmod 600 "$OUT"/approval-role-id "$OUT"/approval-secret-id
echo "[A10] 준비 끝: transit daon-approval(서명은 승인 서비스만), 관리자 앱은 확인만, DB 역할 approval, AppRole daon-approval"
