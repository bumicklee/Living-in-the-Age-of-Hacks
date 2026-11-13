"""T03 · 회원정보 수정으로 등급·포인트가 바뀜(대량 할당, A01). 수업 2-4.

alice가 회원정보 수정 요청 본문에 포인트(integration)·성장치(growth)·회원 등급(memberLevelId)을 끼워 보낸다.
수정 뒤 /sso/info에서 셋 중 하나라도 보낸 값으로 바뀌었으면 레드. 바뀌었으면 같은 경로로 원래 값으로 되돌린다(랩 데이터 보존).
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._http import portal, portal_login

T = "T03"
TITLE = "회원정보 수정으로 포인트·등급 변경(대량 할당)"
LESSON = "2-4"
SOURCE = "[교육용 추가] 회원정보 수정 API가 회원 엔티티를 그대로 받음"
STACKS = ("spring",)

USER = "alice"
FAKE_POINTS = 999999
FAKE_GROWTH = 99999
FAKE_LEVEL = 1          # 원본 시드의 골드 회원 등급(최고 등급 3은 다이아몬드)


def info(c, token) -> dict:
    return c.get("/sso/info", headers={"Authorization": token}).json().get("data") or {}


def update(c, token, body: dict) -> dict:
    return c.post("/sso/updateInfo", json=body, headers={"Authorization": token}).json()


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s2-04/express.diff로 확인).")
    with portal() as c:
        token = portal_login(c, USER)
        before = info(c, token)
        body = {"nickname": before.get("nickname") or "앨리스",
                "integration": FAKE_POINTS, "growth": FAKE_GROWTH, "memberLevelId": FAKE_LEVEL}
        resp = update(c, token, body)
        after = info(c, token)
        keys = ("integration", "growth", "memberLevelId")
        changed = [k for k in keys if after.get(k) != before.get(k)]
        ev = [f"요청: {USER} 토큰으로 POST /sso/updateInfo  본문에 integration={FAKE_POINTS}, growth={FAKE_GROWTH}, "
              f"memberLevelId={FAKE_LEVEL}를 끼워 보냄",
              f"응답: code={resp.get('code')} message={resp.get('message')!r}",
              "포인트 {} → {}, 성장치 {} → {}, 등급 {} → {}".format(*(v for k in keys for v in (before.get(k), after.get(k))))]
        if changed:
            update(c, token, {k: before.get(k) for k in keys})
            ev.append("랩 데이터를 원래 값으로 되돌렸습니다")
            return Result(True, f"{USER}가 회원정보 수정으로 {', '.join(changed)}을(를) 바꿨다", ev)
    return Result(False, f"{USER}의 포인트·성장치·등급은 회원정보 수정으로 바뀌지 않는다", ev)
