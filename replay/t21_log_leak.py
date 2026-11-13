"""T21 · 로그가 개인정보·비밀번호·토큰의 사본이 된다(A09·A04, 점검표 F35·F39). 수업 8-5.

가짜 회원 alice로 로그인·주문 목록, 관리자 admin으로 로그인·회원 목록·주문 상세를 한 번씩 한 뒤, 그동안 앱이 남긴 로그를 센다.
  레드: 앱 로그(또는 Loki, 8-4 뒤)에 평문 비밀번호 값·JWT 모양 문자열·휴대전화 모양 문자열이 1건 이상
  그린: 같은 흐름 뒤 앱 로그와 Loki 모두 0건
값은 화면에 찍지 않는다(종류별 건수만). 계정·값은 모두 랩 전용 가짜다.
"""
from __future__ import annotations

import time

import httpx

from daon import stack as st
from replay import NotReady, Result, _logs, _obs
from replay._http import admin, admin_login, new_trace_id, portal, portal_login

T = "T21"
TITLE = "로그에 평문 비밀번호·토큰·전화번호가 남음"
LESSON = "8-5"
SOURCE = "[원본 기본값] WebLogAspect가 요청 인자·응답 전체를 INFO로(F35), 요청마다 사용자 이름(F39)"
STACKS = ("spring",)
SERVICES = ("mall-admin", "mall-portal")


def flows() -> str:
    """로그인·조회 흐름 한 벌. 돌려준 값: 이 흐름의 추적 번호 앞부분(로그 찾기용)"""
    from replay.t09_login_guard import clear_counters
    tag = new_trace_id("t21")
    try:
        clear_counters()
        with portal() as c:
            tok = portal_login(c, "alice")
            h = {"Authorization": tok, "X-Trace-Id": tag + "-p"}
            c.get("/sso/info", headers=h)
            c.get("/order/list", params={"status": -1, "pageNum": 1, "pageSize": 5}, headers=h)
        with admin() as c:
            tok = admin_login(c, "admin")
            h = {"Authorization": tok, "X-Trace-Id": tag + "-a"}
            c.get("/daon/customer/list", headers=h)
            c.get("/order/9001", headers=h)
    except httpx.TransportError as e:
        raise NotReady(f"앱에 닿지 않습니다: {e.__class__.__name__}")
    return tag


def count(lines: list[str]) -> dict[str, int]:
    return {k: sum(1 for ln in lines if rx.search(ln)) for k, rx in _obs.KINDS.items()}


def app_lines(since: str, stack: str = "spring") -> list[str]:
    out = []
    for svc in SERVICES:
        r = st.compose(stack, "logs", "--no-log-prefix", "--since", since, svc, check=False, capture=True)
        out += (r.stdout or "").splitlines()
    return out


def loki_lines(since_s: int) -> list[str]:
    q = '{app=~"mall-admin|mall-portal", stream!="audit"}'
    return [ln for _, ln in _obs.loki_query(q, since_s=since_s, limit=5000)]


def scan(stack: str = "spring", loki_wait: float = 15.0) -> tuple[dict, dict | None, str]:
    """흐름 한 벌을 돌리고 (앱 로그 건수, Loki 건수 또는 None, 추적 번호)"""
    since = _logs.now()
    t0 = time.time()
    tag = flows()
    time.sleep(1.5)
    app = count(app_lines(since, stack))
    lk = None
    if _obs.a05_on() and _obs.loki_ready():
        _obs.wait_loki(f'{{app="mall-admin"}} |= "{tag}-a"', timeout=loki_wait, since_s=600)   # 수집 지연을 기다린다
        lk = count(loki_lines(int(time.time() - t0) + 10))
    return app, lk, tag


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s8-05).")
    app, lk, tag = scan(stack)
    fmt = lambda d: f"비밀번호 값 {d['password']}줄 · JWT 모양 {d['jwt']}줄 · 휴대전화 모양 {d['phone']}줄"
    ev = ["흐름: alice 로그인·회원 정보·주문 목록, admin 로그인·회원 목록·주문 9001 상세(값은 화면에 찍지 않음)",
          f"앱 로그(mall-admin·mall-portal): {fmt(app)}",
          f"Loki(8-4 A5): {fmt(lk)}" if lk is not None else "Loki: 아직 없음(8-4 전) 또는 닿지 않음",
          f"추적 번호 앞부분: {tag}"]
    leaked = sum(app.values()) + (sum(lk.values()) if lk else 0)
    if leaked:
        return Result(True, f"로그가 민감 값의 사본이 됐다(합계 {leaked}줄)", ev)
    return Result(False, "같은 흐름 뒤 로그에 민감 값 0건", ev)
