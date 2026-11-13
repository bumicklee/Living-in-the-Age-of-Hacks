#!/usr/bin/env bash
# 공개 저장소(Living-in-the-Age-of-Hacks)로 내보내기: 랩 저장소의 공개할 파일만 DEST로 복사하고, 비밀이 섞였는지 검사한다.
#
#   tools/sync_public_repo.sh [DEST]          기본 DEST = ../_public/Living-in-the-Age-of-Hacks (랩 폴더 옆)
#   EXCLUDE_SOLUTIONS=1 tools/sync_public_repo.sh   정답(solutions/)을 빼고 내보내기(기본은 넣는다: 정답도 저장소 안 solutions/, 2026-11-08 결정)
#   EXPRESS_MINI=<경로>                              Express 미니 랩 위치(기본 ../express-mini). 공개 저장소의 express-mini/ 폴더로 함께 내보낸다(2026-11-13)
#
# 하는 일
#   ① 목록: git이 아는 파일(커밋된 것 + 아직 커밋하지 않았지만 .gitignore에 걸리지 않은 것)만. 상태 폴더·빌드 결과·키 파일은 다시 한 번 뺀다
#   ② 복사: DEST의 예전 파일은 지우고(.git은 남김) 새로 채운다
#   ③ 검사: 개인 홈 경로·개인키·토큰 모양·실제처럼 보이는 전화번호(tools/public_scan.py). 하나라도 있으면 종료 코드 1, DEST는 그대로 두고 멈춘다
# 하지 않는 일: git commit·push. 검사가 통과해도 사람이 DEST를 열어 보고 직접 올린다.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${1:-$ROOT/../_public/Living-in-the-Age-of-Hacks}"
PY="${PYTHON:-python3}"

case "$DEST" in
  "$ROOT"|"$ROOT"/*) echo "DEST가 랩 저장소 안입니다: $DEST" >&2; exit 2 ;;
esac
mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"

LIST="$(mktemp)"
trap 'rm -f "$LIST"' EXIT

# ① 공개할 파일 목록
git -C "$ROOT" ls-files -z --cached --others --exclude-standard \
  | tr '\0' '\n' \
  | grep -Ev '^(\.daon/|\.venv/|reports/|build/|_upstream/|infra/\.env$|scoreboard/.*\.json$)' \
  | grep -Ev '(^|/)(node_modules|target|__pycache__|partner-pki|approval-creds)/' \
  | grep -Ev '\.(key|pem|p12|jks|keystore)$' \
  | grep -Ev '(^|/)\.env$' \
  > "$LIST" || true
if [ "${EXCLUDE_SOLUTIONS:-0}" = "1" ]; then
  grep -Ev '^solutions/' "$LIST" > "$LIST.tmp" || true
  mv "$LIST.tmp" "$LIST"
fi
COUNT="$(wc -l < "$LIST" | tr -d ' ')"
[ "$COUNT" -gt 0 ] || { echo "내보낼 파일이 없습니다" >&2; exit 2; }

# ② 복사(.git은 남긴다: DEST가 이미 공개 저장소의 작업 폴더일 수 있다)
find "$DEST" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
( cd "$ROOT" && while IFS= read -r f; do
    [ -f "$f" ] || continue
    mkdir -p "$DEST/$(dirname "$f")"
    cp -p "$f" "$DEST/$f"
  done < "$LIST" )
echo "복사: ${COUNT}개 → $DEST $( [ "${EXCLUDE_SOLUTIONS:-0}" = "1" ] && echo '(정답 제외)' )"

# ②-2 Express 미니 랩(섹션 16~18): 별도 git 저장소의 추적 파일만 DEST/express-mini/로 복사한다
MINI="${EXPRESS_MINI:-$ROOT/../express-mini}"
if [ -d "$MINI/.git" ]; then
  MLIST="$(mktemp)"
  git -C "$MINI" ls-files -z | tr '\0' '\n' | grep -Ev '(^|/)(node_modules|\.lab)/' | grep -Ev '(^|/)\.env$' > "$MLIST" || true
  ( cd "$MINI" && while IFS= read -r f; do
      [ -f "$f" ] || continue
      mkdir -p "$DEST/express-mini/$(dirname "$f")"
      cp -p "$f" "$DEST/express-mini/$f"
    done < "$MLIST" )
  echo "복사: Express 미니 $(wc -l < "$MLIST" | tr -d ' ')개 → $DEST/express-mini ($(git -C "$MINI" describe --tags --always 2>/dev/null))"
  rm -f "$MLIST"
else
  echo "Express 미니 랩이 없어 건너뜀: $MINI" >&2
fi

# ③ 검사
if ! "$PY" -I "$ROOT/tools/public_scan.py" "$DEST" "$ROOT"; then
  echo "멈춤: 공개하면 안 되는 모양이 있습니다. 랩 저장소에서 고친 뒤 다시 내보내세요(허용은 tools/public-allow.txt에 이유와 함께)." >&2
  exit 1
fi
if command -v gitleaks >/dev/null 2>&1; then
  # 6-2 수업 규칙 + 확인된 실습 가짜 값 허용(tools/public-gitleaks.toml). extend 경로 때문에 ROOT에서 실행한다
  ( cd "$ROOT" && gitleaks dir "$DEST" --no-banner --redact --exit-code 1 -c tools/public-gitleaks.toml >/dev/null 2>&1 ) \
    || { echo "멈춤: gitleaks가 허용 목록 밖의 시크릿을 찾았습니다: (cd \"$ROOT\" && gitleaks dir \"$DEST\" -c tools/public-gitleaks.toml --redact)" >&2; exit 1; }
  echo "gitleaks(6-2 규칙 + 실습 가짜 값 허용): 찾은 것 없음"
else
  echo "gitleaks가 없어 6-2 규칙 검사는 건너뜀(brew install gitleaks)"
fi
echo "검사 통과. push는 하지 않았습니다. DEST를 열어 확인한 뒤 직접 올리세요: git -C \"$DEST\" status"
