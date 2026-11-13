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

# ✏️ 6-3: 여기에 앱별 KV(secret/daon/portal·admin)와 동적 DB 자격증명(database 엔진)·앱별 정책을 더한다
# ✏️ 6-4: 같은 앱별 KV에 저장소 앱 계정의 비밀(portal: MongoDB·RabbitMQ, admin: MinIO)도 둔다
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
  bao write auth/approle/role/daon-$app token_policies=daon-app token_ttl=1h token_max_ttl=24h secret_id_ttl=0 >/dev/null
  bao read -field=role_id auth/approle/role/daon-$app/role-id > "$CREDS/$app-role-id"
  [ -s "$CREDS/$app-secret-id" ] || bao write -f -field=secret_id auth/approle/role/daon-$app/secret-id > "$CREDS/$app-secret-id"
done
chmod 644 "$CREDS"/*
echo "[A4] 준비 끝: transit daon-jwt·daon-pii, kv secret/daon/pii-hmac, AppRole daon-portal·daon-admin, 감사 로그"
