"""T30 · 로그인한 사람의 응답이 공유 캐시에 남아 다른 사람에게 나간다(P2). 수업 11-4.

[회사 설정] A7 Varnish의 시작 VCL은 /api/ GET을 60초 동안 Authorization과 상관없이 캐시한다(성능 팀 설정).
재연: 캐시를 비운 뒤 alice 토큰으로 게이트웨이(8086) GET /api/sso/info → bob 토큰으로 같은 주소 → 토큰 없이 같은 주소.
  레드: bob(또는 토큰 없는 요청)이 alice의 회원 번호를 받고 X-Cache: HIT
  그린: 각자 자기 정보(또는 401), 인증 요청은 X-Cache: PASS
응답 본문의 개인정보는 가짜 시드 값이고, 화면에는 회원 번호와 아이디만 보인다.
"""
from __future__ import annotations

import httpx

from daon import boundary
from replay import NotReady, Result
from replay import _boundary as gb
from replay._http import ACCOUNTS, portal, portal_login

T = "T30"
TITLE = "인증된 응답이 공유 캐시에 남아 다른 사람에게 나간다"
LESSON = "11-4"
SOURCE = "[회사 설정] A7 Varnish VCL: /api/ GET은 60초 강제 캐시(Authorization·오리진 Cache-Control 무시)"
STACKS = ("spring",)
PATH = "/api/sso/info"


def info(token: str | None) -> tuple[int, int | None, str, str]:
    """(HTTP 상태, 응답의 회원 번호, X-Cache, Cache-Control)"""
    h = {"Authorization": token} if token else {}
    r = httpx.get(gb.SHOP + PATH, headers=h, timeout=10)
    mid = None
    try:
        data = r.json().get("data")
        mid = data.get("id") if isinstance(data, dict) else None
    except ValueError:
        pass
    return r.status_code, mid, r.headers.get("x-cache", ""), r.headers.get("cache-control", "")


def scene() -> dict:
    gb.require_gateway()
    with portal() as c:
        alice, bob = portal_login(c, "alice"), portal_login(c, "bob")
    boundary.purge_cache()
    a = info(alice)
    b = info(bob)
    n = info(None)
    return {"alice": a, "bob": b, "anon": n}


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s11-04).")
    s = scene()
    alice_id = ACCOUNTS["alice"]["member_id"]
    ev = [f"{who:5} → HTTP {st}, 회원 번호 {mid}, X-Cache {xc or '-'}, Cache-Control {cc or '-'}"
          for who, (st, mid, xc, cc) in (("alice", s["alice"]), ("bob", s["bob"]), ("토큰 없음", s["anon"]))]
    leaked = [who for who in ("bob", "anon") if s[who][1] == alice_id]
    if leaked:
        names = ", ".join("bob" if w == "bob" else "토큰 없는 요청" for w in leaked)
        return Result(True, f"{names}이(가) alice(회원 {alice_id})의 정보를 캐시에서 받았다", ev)
    return Result(False, "각자 자기 정보만(인증 요청은 캐시를 거치지 않음)", ev)
