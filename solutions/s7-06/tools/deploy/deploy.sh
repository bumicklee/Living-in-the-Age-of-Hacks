#!/bin/sh
# 다온마켓 배포(스테이징 칸): make deploy [서비스]      교육용 랩 전용, 127.0.0.1만 부른다.
#   레지스트리의 daon/<서비스>:lesson 이미지를 띄우기 전에 확인한다(7-6).
#     ① 태그가 지금 가리키는 다이제스트(sha256:…)를 정한다   → 못 정하면 거부(확인 불가)
#     ② 그 다이제스트의 서명을 공개 키로 확인한다             → 없거나 틀리면 거부(서명 없음)
#     ③ 출처 증명의 커밋이 CI 저장소 main에 있는지 확인한다   → 아니면 거부(출처 증명의 커밋이 main에 없음)
#   통과한 이미지는 태그가 아니라 다이제스트(@sha256:…)로 띄운다. 확인 자체를 못 해도 띄우지 않는다(실패하면 막는다).
#   투명성 로그(인터넷)는 쓰지 않는다: 랩은 PC 안에서만 돈다. 회사에서는 켠다.
set -eu
ROOT=${DAON_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
cd "$ROOT"
SERVICE=${1:-mall-portal}
set -a; . infra/.env; set +a
PY=${DAON_PY:-python3}
REG=127.0.0.1:${DAON_CI_REGISTRY_PORT:-5050}
NET=daon_ci-run
TOOLS=daon/ci-tools:lab
WORK=.daon/ci/deploy; mkdir -p "$WORK"
DH=$(docker context inspect --format '{{.Endpoints.docker.Host}}')   # --config로 설정 폴더를 바꾸면 도커가 쓰는 소켓 정보를 잃으므로 먼저 알아 둔다
CFG=$(mktemp -d); trap 'rm -rf "$CFG"' EXIT          # 내 도커 로그인 정보를 건드리지 않으려고 임시 설정 폴더를 쓴다
D=""
deny() {
  SHORT=$(printf '%s' "${D:-sha256:unknown}" | cut -c1-19)            # sha256: + 앞 12자
  "$PY" tools/secevent.py emit deploy.denied.unsigned_image 7-6 "deploy:lab" "image:daon/$SERVICE@$SHORT" "$1" >/dev/null
  echo "배포를 거부합니다: $1 (image:daon/$SERVICE@$SHORT)"
  exit 1
}
# 확인 도구는 레지스트리와 같은 망 안의 컨테이너에서 돈다. 공개 키만 읽기 전용으로 준다
tools() { docker run --rm --network "$NET" "$@"; }

D=$(tools --entrypoint skopeo "$TOOLS" inspect --tls-verify=false --creds "lab-admin:$DAON_CI_ADMIN_PASSWORD" \
      "docker://registry:5000/daon/$SERVICE:lesson" --format '{{.Digest}}' 2>/dev/null) || D=""
[ -n "$D" ] || deny "확인 불가"
printf '{"auths":{"registry.local:5000":{"auth":"%s"}}}' "$(printf '%s:%s' lab-admin "$DAON_CI_ADMIN_PASSWORD" | base64 | tr -d '\n')" > "$CFG/config.json"
REF=registry.local:5000/daon/$SERVICE@$D
cosign() { tools -e DOCKER_CONFIG=/dc -v "$CFG:/dc:ro" -v daon_ci-keys:/keys:ro --entrypoint cosign "$TOOLS" "$@"; }

cosign verify --key /keys/cosign.pub --insecure-ignore-tlog=true "$REF" >/dev/null 2>&1 || deny "서명 없음"
PROV=$(cosign verify-attestation --type custom --key /keys/cosign.pub --insecure-ignore-tlog=true "$REF" 2>/dev/null) || deny "서명 없음"
COMMIT=$(printf '%s' "$PROV" | "$PY" -c '
import sys, json, base64
for line in sys.stdin:
    try:
        st = json.loads(base64.b64decode(json.loads(line)["payload"]))
    except Exception:
        continue
    pred = st.get("predicate", {})
    if isinstance(pred.get("Data"), str):
        pred = json.loads(pred["Data"])
    print(pred.get("commit", ""))
    break
') || COMMIT=""
[ -n "$COMMIT" ] || deny "확인 불가"
ON_MAIN=$(curl -fsS -u "lab-admin:$DAON_CI_ADMIN_PASSWORD" "http://127.0.0.1:${DAON_CI_PORT:-3300}/api/v1/repos/daon/market/commits?sha=main&limit=100" 2>/dev/null | "$PY" -c '
import sys, json
want = sys.argv[1]
print("yes" if any(c["sha"] == want for c in json.load(sys.stdin)) else "no")' "$COMMIT") || ON_MAIN=""
[ -n "$ON_MAIN" ] || deny "확인 불가"
[ "$ON_MAIN" = yes ] || deny "출처 증명의 커밋이 main에 없음"

printf '%s' "$DAON_CI_ADMIN_PASSWORD" | docker -H "$DH" --config "$CFG" login "$REG" -u lab-admin --password-stdin >/dev/null 2>&1 || deny "확인 불가"
docker -H "$DH" --config "$CFG" pull -q "$REG/daon/$SERVICE@$D" >/dev/null || deny "확인 불가"
cat > "$WORK/compose.staging.yml" <<YML
name: daon-staging
services:
  $SERVICE:
    image: $REG/daon/$SERVICE@$D
    container_name: daon-staging-$SERVICE
    entrypoint: ["sleep", "3600"]
    network_mode: none
YML
docker compose -f "$WORK/compose.staging.yml" up -d --force-recreate >/dev/null 2>&1
echo "배포했습니다(서명·출처 확인 통과, 커밋 $(printf '%s' "$COMMIT" | cut -c1-12)): daon/$SERVICE@$(printf '%s' "$D" | cut -c1-19)… → 컨테이너 daon-staging-$SERVICE"
docker inspect daon-staging-$SERVICE --format '이미지: {{.Config.Image}}  라벨 daon.build={{index .Config.Labels "daon.build"}}'
