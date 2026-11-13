#!/bin/sh
# 다온마켓 배포(스테이징 칸): make deploy [서비스]      교육용 랩 전용, 127.0.0.1만 부른다.
#   레지스트리의 daon/<서비스>:lesson 이미지를 받아 스테이징 컨테이너(daon-staging-<서비스>)로 띄운다.
#   7-6 시작 지점: 태그만 보고 그대로 띄운다. 무엇을 띄우는지 확인하지 않는다(T19).
set -eu
ROOT=${DAON_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
cd "$ROOT"
SERVICE=${1:-mall-portal}
set -a; . infra/.env; set +a
REG=127.0.0.1:${DAON_CI_REGISTRY_PORT:-5050}
IMAGE=$REG/daon/$SERVICE:lesson
WORK=.daon/ci/deploy; mkdir -p "$WORK"
DH=$(docker context inspect --format '{{.Endpoints.docker.Host}}')   # --config로 설정 폴더를 바꾸면 도커가 쓰는 소켓 정보를 잃으므로 먼저 알아 둔다
CFG=$(mktemp -d); trap 'rm -rf "$CFG"' EXIT          # 내 도커 로그인 정보를 건드리지 않으려고 임시 설정 폴더를 쓴다
printf '%s' "$DAON_CI_ADMIN_PASSWORD" | docker -H "$DH" --config "$CFG" login "$REG" -u lab-admin --password-stdin >/dev/null 2>&1 || { echo "레지스트리에 로그인하지 못했습니다(A8이 켜져 있나요? make up)"; exit 1; }
# ✏️ 7-6: 띄우기 전에 ① 다이제스트를 정하고 ② 서명을 확인하고 ③ 출처 증명의 커밋이 main에 있는지 확인한다. 하나라도 못 하면 거부(종료 코드 1, deploy.denied.unsigned_image)
# ✏️ 7-6: 통과한 이미지는 태그가 아니라 @sha256 다이제스트로 띄운다
docker -H "$DH" --config "$CFG" pull -q "$IMAGE" >/dev/null
cat > "$WORK/compose.staging.yml" <<YML
name: daon-staging
services:
  $SERVICE:
    image: $IMAGE
    container_name: daon-staging-$SERVICE
    entrypoint: ["sleep", "3600"]
    network_mode: none
YML
docker compose -f "$WORK/compose.staging.yml" up -d --force-recreate >/dev/null 2>&1
echo "배포했습니다: $IMAGE → 컨테이너 daon-staging-$SERVICE"
docker inspect daon-staging-$SERVICE --format '이미지: {{.Config.Image}}  라벨 daon.build={{index .Config.Labels "daon.build"}}'
