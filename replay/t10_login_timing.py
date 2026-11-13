"""T10 · 응답 시간과 문구로 회원 여부가 샌다(A07). 수업 4-4.

127.0.0.1의 다온마켓 고객 로그인에 「없는 아이디」 20번과 「alice + 틀린 비밀번호」 20번을 보내 응답 시간 중앙값을 비교한다.
중앙값 차가 기준(30ms) 이상이거나, 두 경우의 오류 문구가 다르면 레드. 관리자 로그인 문구도 같은 기준으로 본다.
4-3 속도 제한에 걸리지 않게 몇 번마다 rl:* 키를 지운다(랩 전용). 끝나면 다시 지운다.
"""
from __future__ import annotations

import statistics
import time

from replay import NotReady, Result
from replay._http import admin, portal
from replay.t09_login_guard import clear_counters

T = "T10"
TITLE = "응답 시간·문구로 회원 여부가 샘"
LESSON = "4-4"
SOURCE = "[원본 확인됨] 없는 회원은 bcrypt 비교를 건너뛰고, 관리자 로그인은 문구가 둘"
STACKS = ("spring",)

ROUNDS = 20
LIMIT_MS = 30.0


def _timed_login(c, username: str, password: str) -> tuple[float, str]:
    t0 = time.perf_counter()
    r = c.post("/sso/login", data={"username": username, "password": password})
    ms = (time.perf_counter() - t0) * 1000
    try:
        msg = r.json().get("message") or ""
    except ValueError:
        msg = f"HTTP {r.status_code}"
    return ms, msg


def bar(ms: float, scale: float = 4.0) -> str:
    return "█" * max(1, int(ms / scale))


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다.")
    unknown, known, msgs_unknown, msgs_known = [], [], set(), set()
    try:
        with portal() as c:
            _timed_login(c, "lab-warmup", "x")                      # 첫 요청의 연결 비용을 빼려고 한 번 버린다
            for i in range(ROUNDS):
                if i % 4 == 0:
                    clear_counters()
                ms, m = _timed_login(c, f"lab-nouser-{i}", "lab-wrong")
                unknown.append(ms); msgs_unknown.add(m)
                ms, m = _timed_login(c, "alice", f"lab-wrong-{i}")
                known.append(ms); msgs_known.add(m)
        clear_counters()
        with admin() as c:
            a1 = c.post("/admin/login", json={"username": "lab-nouser", "password": "lab-wrong"}).json().get("message")
            a2 = c.post("/admin/login", json={"username": "staff_goods", "password": "lab-wrong"}).json().get("message")
    finally:
        clear_counters()
    mu, mk = statistics.median(unknown), statistics.median(known)
    diff = mk - mu
    same_member = msgs_unknown == msgs_known and len(msgs_unknown) == 1
    same_admin = a1 == a2
    ev = [f"고객 로그인 {ROUNDS}번씩 (127.0.0.1:8085/sso/login)",
          f"없는 아이디      중앙값 {mu:6.1f}ms {bar(mu)}",
          f"alice 틀린 비번  중앙값 {mk:6.1f}ms {bar(mk)}",
          f"차이 {diff:+.1f}ms (기준 {LIMIT_MS:.0f}ms 미만)",
          f"고객 문구: 없는 아이디 {sorted(msgs_unknown)} / 틀린 비밀번호 {sorted(msgs_known)}",
          f"관리자 문구: 없는 계정 {a1!r} / 틀린 비밀번호 {a2!r}",
          "속도 제한 키를 지웠습니다"]
    problems = []
    if abs(diff) >= LIMIT_MS:
        problems.append(f"응답 시간 중앙값 차 {diff:+.0f}ms")
    if not same_member:
        problems.append("고객 로그인 문구가 다름")
    if not same_admin:
        problems.append("관리자 로그인 문구가 다름")
    if problems:
        return Result(True, "회원 여부를 알 수 있다: " + ", ".join(problems), ev)
    return Result(False, f"없는 아이디와 있는 아이디가 시간({diff:+.0f}ms)·문구로 구별되지 않는다", ev)
