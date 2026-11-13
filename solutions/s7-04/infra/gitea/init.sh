#!/bin/sh
# 다온마켓 A8 · Gitea 준비(멱등). 교육용 랩 전용, 값은 모두 가짜.
# 하는 일: 관리자 계정 → 조직(daon·daon-lab) → 저장소(daon/market, daon-lab/scan-step) → 외부 단계 씨앗(고정 시각 커밋, 태그 v1)
#         → 저장소 비밀값을 secrets.list 대로 맞춤 → 러너 등록 토큰을 러너가 읽는 볼륨에 적음
# 비밀번호·토큰 값은 어떤 출력에도 넣지 않는다.
set -eu
GITEA=http://gitea:3000
API=$GITEA/api/v1
ADMIN=lab-admin
: "${DAON_CI_ADMIN_PASSWORD:?}" "${DAON_CI_PUSH_PASSWORD:?}"
curl_admin() { curl -sS -u "$ADMIN:$DAON_CI_ADMIN_PASSWORD" -H 'Content-Type: application/json' "$@"; }

echo "[A8] Gitea를 기다립니다"
i=0; until curl -fsS "$GITEA/api/healthz" >/dev/null 2>&1; do i=$((i+1)); [ $i -gt 90 ] && { echo "Gitea가 뜨지 않습니다"; exit 1; }; sleep 2; done

mk_user() { # 이름 비밀번호 [admin]
  if ! curl_admin -f -o /dev/null "$API/users/$1" 2>/dev/null; then
    su-exec git gitea admin user create --username "$1" --password "$2" --email "$1@example.invalid" --must-change-password=false ${3:+--admin} >/dev/null
    echo "[A8] 계정 $1 을(를) 만들었습니다"
  fi
}
mk_user "$ADMIN" "$DAON_CI_ADMIN_PASSWORD" admin

mk_org() { curl_admin -o /dev/null -w '' -X POST "$API/orgs" -d "{\"username\":\"$1\",\"visibility\":\"public\"}" || true; }
mk_org daon; mk_org daon-lab
mk_repo() { # 조직 이름
  curl_admin -o /dev/null -X POST "$API/orgs/$1/repos" -d "{\"name\":\"$2\",\"private\":false,\"default_branch\":\"main\"}" || true
}
mk_repo daon market; mk_repo daon-lab scan-step

# 외부 단계 씨앗: 고정 시각·고정 작성자라 커밋 해시가 모든 PC에서 같다(pins.lock에 적는 값)
if [ "$(curl_admin "$API/repos/daon-lab/scan-step/tags" | grep -c '"name"')" = 0 ]; then
  W=$(mktemp -d); cd "$W"; git init -q -b main
  cp /seed/scan-step/action.yml . ; git add action.yml
  GIT_AUTHOR_NAME=daon-lab GIT_AUTHOR_EMAIL=lab@example.invalid GIT_COMMITTER_NAME=daon-lab GIT_COMMITTER_EMAIL=lab@example.invalid \
  GIT_AUTHOR_DATE='2026-01-05T09:00:00+0900' GIT_COMMITTER_DATE='2026-01-05T09:00:00+0900' git commit -q -m "scan-step v1 (교육용 씨앗)"
  git tag v1
  AUTH=$(printf '%s:%s' "$ADMIN" "$DAON_CI_ADMIN_PASSWORD" | base64 | tr -d '\n')
  git -c http.extraHeader="Authorization: Basic $AUTH" push -q "$GITEA/daon-lab/scan-step.git" main v1
  echo "[A8] 외부 단계 daon-lab/scan-step@v1 을(를) 만들었습니다(커밋 $(git rev-parse HEAD | cut -c1-12))"
  cd /; rm -rf "$W"
fi

# 저장소 비밀값: secrets.list 대로. REGISTRY_PASSWORD = 레지스트리 장기 계정(ci-push)의 비밀번호(만료 없음)
if grep -v '^#' /seed/secrets.list | grep -q '^REGISTRY_PASSWORD$'; then
  curl_admin -o /dev/null -X PUT "$API/repos/daon/market/actions/secrets/REGISTRY_PASSWORD" -d "{\"data\":\"$DAON_CI_PUSH_PASSWORD\"}"
  echo "[A8] 저장소 비밀값 REGISTRY_PASSWORD(장기 키)를 넣었습니다(값은 출력하지 않습니다)"
else
  curl_admin -o /dev/null -X DELETE "$API/repos/daon/market/actions/secrets/REGISTRY_PASSWORD" || true
  echo "[A8] 저장소 비밀값에 장기 키가 없습니다"
fi

# 러너 등록 토큰(러너 컨테이너가 읽는다)
curl_admin "$API/admin/runners/registration-token" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' > /reg/token
chmod 644 /reg/token
echo "[A8] 준비 끝"
