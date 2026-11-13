#!/bin/sh
# 다온마켓 랩 · 파트너 입구 mTLS 인증서(수업 12-3, A9). docker compose가 up 때마다 한 번 돌린다(있으면 그대로 둔다).
#
# - 랩 내부 인증 기관(CA)을 OpenBao PKI 엔진(pki-partner/)으로 처음 켠다. 5-5는 그림 설명 수업이라(작성자 결정 #14) CA를 켜지 않았다.
# - 만드는 것(모두 이 PC 안에서만 쓰는 랩 값. 저장소에 넣지 않는다. 볼륨과 .daon/partner-pki/에만 있다)
#     /pki-gw/       ca.crt, gateway.crt, gateway.key                      게이트웨이만 붙인다(서버 인증서·클라이언트 확인용 CA)
#     /pki-clients/  ca.crt, <파트너>.crt, <파트너>.key (courier-01·analytics-01·legacy-01)   make partner-certs가 PC로 가져간다
# - 인증서는 30일(토큰 15분보다 훨씬 길게: 인증서가 먼저 끝나면 토큰 갱신이 꼬인다). 7일 남으면 다음 up 때 새로 만든다.
# - 개인키는 이 스크립트가 만든 순간 볼륨에만 쓴다(권한 600). 로그에는 이름·만료만 찍는다.
set -eu
export BAO_ADDR=http://openbao:8200
ROOT=$(tr -d '\n ' < /bao-lab/init.json | sed -n 's/.*"root_token":"\([^"]*\)".*/\1/p')
[ -n "$ROOT" ] || { echo "[12-3] OpenBao 루트 토큰을 찾지 못했습니다(openbao-init 먼저)"; exit 1; }
export BAO_TOKEN="$ROOT"
GW=/pki-gw; CL=/pki-clients
mkdir -p "$GW" "$CL"
TTL=720h                       # 30일
RENEW_BEFORE=$((7 * 86400))    # 7일 남으면 새로

bao secrets list | grep -q '^pki-partner/' || {
  bao secrets enable -path=pki-partner pki >/dev/null
  bao secrets tune -max-lease-ttl=87600h pki-partner >/dev/null
  echo "[12-3] PKI 엔진 pki-partner/ 켬"
}
if ! bao list pki-partner/issuers >/dev/null 2>&1; then
  bao write -field=certificate pki-partner/root/generate/internal \
    common_name="Daon Lab Partner CA (fake)" key_type=ec key_bits=256 ttl=87600h >/dev/null
  echo "[12-3] 랩 내부 CA를 만들었습니다(10년, 랩 전용)"
fi
# 역할: 파트너 클라이언트 인증서(클라이언트 용도만), 게이트웨이 서버 인증서(127.0.0.1·localhost)
bao write pki-partner/roles/partner-client allow_any_name=true enforce_hostnames=false \
  client_flag=true server_flag=false key_type=ec key_bits=256 ttl=$TTL max_ttl=$TTL >/dev/null
bao write pki-partner/roles/partner-gateway allowed_domains=localhost allow_bare_domains=true allow_ip_sans=true \
  client_flag=false server_flag=true key_type=ec key_bits=256 ttl=$TTL max_ttl=$TTL >/dev/null

bao read -field=certificate pki-partner/cert/ca > "$GW/ca.crt"
cp "$GW/ca.crt" "$CL/ca.crt"
chmod 644 "$GW/ca.crt" "$CL/ca.crt"

# JSON 한 줄 값 꺼내기(jq 없음): "certificate": "-----BEGIN…\n…" → 줄바꿈을 되살린다
field() {
  printf '%s\n' "$1" | sed -n "s/^ *\"$2\": *\"\(.*\)\",\{0,1\}\$/\1/p" | head -1
}

# issue <역할> <CN> <폴더> <파일 이름> [bao 인자…]
issue() {
  role=$1; cn=$2; dir=$3; name=$4; shift 4
  until=$(cat "$dir/$name.until" 2>/dev/null || echo 0)
  if [ -s "$dir/$name.crt" ] && [ -s "$dir/$name.key" ] && [ "$until" -gt $(( $(date +%s) + RENEW_BEFORE )) ]; then
    return 0
  fi
  out=$(bao write -format=json "pki-partner/issue/$role" common_name="$cn" ttl=$TTL "$@")
  crt=$(field "$out" certificate); key=$(field "$out" private_key)
  [ -n "$crt" ] && [ -n "$key" ] || { echo "[12-3] $name 인증서를 받지 못했습니다"; exit 1; }
  printf '%b\n' "$crt" > "$dir/$name.crt"
  ( umask 077; printf '%b\n' "$key" > "$dir/$name.key" )
  chmod 644 "$dir/$name.crt"; chmod 600 "$dir/$name.key"
  echo $(( $(date +%s) + 720 * 3600 )) > "$dir/$name.until"
  echo "[12-3] 인증서 발급: $name (CN=$cn, 30일)"
}

issue partner-gateway localhost "$GW" gateway ip_sans=127.0.0.1 alt_names=localhost
for p in courier-01 analytics-01 legacy-01; do
  issue partner-client "$p" "$CL" "$p"
done
echo "[12-3] 파트너 입구 인증서 준비 끝(게이트웨이 1, 파트너 3)"
