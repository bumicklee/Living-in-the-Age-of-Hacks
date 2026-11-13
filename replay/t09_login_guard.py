"""T09 · 로그인 실패를 세지 않는다(실패 제한 없음, A07). 수업 4-3(4-2와 같은 재연 번호).

127.0.0.1의 다온마켓 고객 로그인에 가짜 계정 bob으로 틀린 비밀번호를 8번 보낸다.
한 번도 막히지 않으면(HTTP 429가 없으면) 레드. 같은 시각 alice 정상 로그인이 되는지도 본다(오탐 확인).
끝나면 이 재연이 남긴 속도 제한 키(rl:*)를 지운다(다음 시험이 한도에 걸리지 않게).
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._http import portal, portal_login

T = "T09"
TITLE = "로그인 실패 제한 없음(틀린 비밀번호를 몇 번이고 받는다)"
LESSON = "4-3"
SOURCE = "[원본 확인됨] 회원·관리자 로그인이 실패를 세지 않음"
STACKS = ("spring",)

USER = "bob"
TRIES = 8                       # 계정 축 한도(5회)보다 조금 많게만


def clear_counters() -> None:
    try:
        from daon import stack
        keys = stack.redis_cli("--scan", "--pattern", "rl:*").stdout.split()
        if keys:
            stack.redis_cli("DEL", *keys)
    except Exception:           # 랩 도구가 없으면 지우지 않는다(키는 10분 뒤 사라짐)
        pass


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다.")
    codes = []
    try:
        with portal() as c:
            for i in range(TRIES):
                r = c.post("/sso/login", data={"username": USER, "password": f"lab-wrong-{i}"})
                codes.append(r.status_code)
            alice_ok = True
            try:
                portal_login(c, "alice")
            except Exception:
                alice_ok = False
    finally:
        clear_counters()
    blocked = [i + 1 for i, s in enumerate(codes) if s == 429]
    ev = [f"요청: 가짜 계정 {USER}로 틀린 비밀번호 {TRIES}번 (127.0.0.1:8085/sso/login)",
          "응답 코드: " + " ".join(str(s) for s in codes),
          f"처음 막힌 시도: {blocked[0]}번째" if blocked else "막힌 시도: 없음",
          f"같은 시각 alice 정상 로그인: {'성공' if alice_ok else '실패'}",
          "속도 제한 키를 지웠습니다"]
    if not blocked:
        return Result(True, f"틀린 비밀번호 {TRIES}번을 한 번도 막지 않았다", ev)
    if not alice_ok:
        return Result(True, "막기는 했지만 정상 사용자 alice도 로그인하지 못했다(오탐)", ev)
    return Result(False, f"{blocked[0]}번째 시도부터 HTTP 429로 막았고 alice는 로그인된다", ev)
