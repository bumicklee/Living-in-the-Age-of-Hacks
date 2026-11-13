#!/bin/sh
# 다온마켓 랩 · OpenBao 준비(아키텍처 A4, 수업 5-2·5-3·5-4). docker compose가 up 때마다 한 번 돌린다.
# - 처음이면 초기화하고, 봉인 해제 키를 랩 볼륨(/bao-lab)에 둔다. 랩 전용이다. 회사에서는 자동 봉인 해제(KMS·HSM)나 사람 여럿이 나눠 갖는다.
# - 매번: 봉인 해제 → 엔진·키·정책·AppRole이 없으면 만든다 → 앱별 AppRole 값을 /bao-creds에 쓴다(앱이 읽기 전용으로 붙인다).
set -eu
export BAO_ADDR=http://openbao:8200
LAB=/bao-lab; CREDS=/bao-creds
mkdir -p "$LAB" "$CREDS"

i=0
while :; do                                   # bao status: 0 = 봉인 풀림, 2 = 봉인됨(둘 다 응답함), 1 = 닿지 않음
  rc=0; bao status >/dev/null 2>&1 || rc=$?
  [ "$rc" = 0 ] || [ "$rc" = 2 ] && break
  i=$((i+1)); [ $i -gt 60 ] && { echo "OpenBao가 뜨지 않습니다"; exit 1; }; sleep 1
done

if ! bao status -format=json 2>/dev/null | grep -q '"initialized": true'; then
  echo "[A4] OpenBao 초기화(랩 전용 키 1개)"
  bao operator init -key-shares=1 -key-threshold=1 -format=json > "$LAB/init.json"
  chmod 600 "$LAB/init.json"
fi
UNSEAL=$(sed -n 's/.*"unseal_keys_b64": *\[ *"\([^"]*\)".*/\1/p' "$LAB/init.json" | head -1)
[ -n "$UNSEAL" ] || UNSEAL=$(tr -d '\n ' < "$LAB/init.json" | sed -n 's/.*"unseal_keys_b64":\["\([^"]*\)".*/\1/p')
ROOT=$(tr -d '\n ' < "$LAB/init.json" | sed -n 's/.*"root_token":"\([^"]*\)".*/\1/p')
if bao status -format=json 2>/dev/null | grep -q '"sealed": true'; then
  bao operator unseal "$UNSEAL" >/dev/null
  echo "[A4] 봉인 해제"
fi
export BAO_TOKEN="$ROOT"

# 감사 로그는 config.hcl에서 켠다(OpenBao 2.x는 API로 켤 수 없다)

bao secrets list | grep -q '^transit/' || bao secrets enable transit >/dev/null
bao read transit/keys/daon-jwt >/dev/null 2>&1 || bao write -f transit/keys/daon-jwt type=ecdsa-p256 >/dev/null
bao read transit/keys/daon-pii >/dev/null 2>&1 || bao write -f transit/keys/daon-pii type=aes256-gcm96 >/dev/null
bao secrets list | grep -q '^secret/' || bao secrets enable -path=secret kv-v2 >/dev/null
bao kv get secret/daon/pii-hmac >/dev/null 2>&1 || \
  bao kv put secret/daon/pii-hmac key="$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')" version=1 >/dev/null

# ✏️ 6-4: 캐시 서명 키(secret/daon/cache-hmac)도 저장소에 둔다(값은 환경 변수로만 들어온다)
# 6-3: 앱별 KV와 동적 DB 자격증명. 값은 환경 변수(랩 전용 가짜 값)로만 들어오고 이 파일에는 적지 않는다
bao secrets list | grep -q '^database/' || bao secrets enable database >/dev/null
if ! bao read database/config/mysql >/dev/null 2>&1; then
  bao write database/config/mysql plugin_name=mysql-database-plugin \
    connection_url="{{username}}:{{password}}@tcp(db:3306)/" allowed_roles="portal,admin" \
    username=openbao password="$DAON_BAO_DB_PASSWORD" username_template='v-{{.RoleName}}-{{random 8}}' >/dev/null
  echo "[6-3] database/config/mysql 연결 설정"
fi
# 역할: 앱이 쓰는 권한만(표 삭제·권한 부여 없음). 앱이 시작할 때 하는 마이그레이션(5-3 키 표·5-4 검색 칸)을 위해 해당 표에만 CREATE·ALTER·INDEX를 준다(운영에서는 마이그레이션 계정을 따로 둔다). 계정은 1시간, 늘려도 하루가 끝
for r in portal admin; do
  bao write database/roles/$r db_name=mysql default_ttl=1h max_ttl=24h \
    creation_statements="CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; GRANT SELECT, INSERT, UPDATE, DELETE ON mall.* TO '{{name}}'@'%'; GRANT CREATE ON mall.daon_data_key TO '{{name}}'@'%'; GRANT ALTER, INDEX ON mall.ums_member TO '{{name}}'@'%'; GRANT ALTER, INDEX ON mall.oms_order TO '{{name}}'@'%'; GRANT ALTER, INDEX ON mall.ums_member_receive_address TO '{{name}}'@'%';" \
    revocation_statements="DROP USER IF EXISTS '{{name}}'@'%';" >/dev/null
  bao kv put secret/daon/$r druid_password="$DAON_DRUID_PASSWORD" >/dev/null
  cat > /tmp/daon-secrets-$r.hcl <<P
path "secret/data/daon/$r"       { capabilities = ["read"] }
path "database/creds/$r"         { capabilities = ["read"] }
path "sys/leases/renew"          { capabilities = ["update"] }
P
  bao policy write daon-secrets-$r /tmp/daon-secrets-$r.hcl >/dev/null
done
# 정책: 앱은 필요한 길만. 서명 키는 밖으로 꺼낼 수 없다(export 경로 없음)
cat > /tmp/daon-app.hcl <<'P'
path "transit/sign/daon-jwt"        { capabilities = ["update"] }
path "transit/sign/daon-jwt/*"      { capabilities = ["update"] }
path "transit/keys/daon-jwt"        { capabilities = ["read"] }
path "transit/datakey/plaintext/daon-pii" { capabilities = ["update"] }
path "transit/decrypt/daon-pii"     { capabilities = ["update"] }
path "secret/data/daon/pii-hmac"    { capabilities = ["read"] }
P
bao policy write daon-app /tmp/daon-app.hcl >/dev/null

bao auth list | grep -q '^approle/' || bao auth enable approle >/dev/null
for app in portal admin; do
  bao write auth/approle/role/daon-$app token_policies=daon-app,daon-secrets-$app token_ttl=1h token_max_ttl=24h secret_id_ttl=0 >/dev/null
  bao read -field=role_id auth/approle/role/daon-$app/role-id > "$CREDS/$app-role-id"
  [ -s "$CREDS/$app-secret-id" ] || bao write -f -field=secret_id auth/approle/role/daon-$app/secret-id > "$CREDS/$app-secret-id"
done
chmod 644 "$CREDS"/*
echo "[A4] 준비 끝: transit daon-jwt·daon-pii, kv secret/daon/pii-hmac, AppRole daon-portal·daon-admin, 감사 로그"
