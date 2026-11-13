"""T12 · 오래된 로그인 토큰 하나로 환불 계좌가 바뀐다(재인증 없음, A07). 수업 4-7.

127.0.0.1의 다온마켓에 가짜 계정 alice로 로그인하고, 랩 시계를 10분 앞으로 민 뒤(로그인한 지 10분 지난 상태)
환불 계좌를 가짜 값으로 바꿔 본다. 그대로 바뀌면 레드. 끝나면 원래 값으로 되돌리고 랩 시계를 되돌린다.
"""
from __future__ import annotations

import time

from replay import NotReady, Result
from replay._http import ACCOUNTS, portal
from replay.t09_login_guard import clear_counters

T = "T12"
TITLE = "오래된 로그인 토큰 하나로 환불 계좌 변경(재인증 없음)"
LESSON = "4-7"
SOURCE = "[교육용 추가] 환불 계좌 변경이 로그인 토큰만 확인"
STACKS = ("spring",)

USER = "alice"
FAKE_BANK, FAKE_ACCOUNT = "랩은행", "000-9999-0000-00"


def lab_clock(minutes: int | None) -> None:
    from daon import stack
    if minutes:
        stack.redis_cli("SET", "lab:clock:offset", str(minutes * 60 * 1000))
    else:
        stack.redis_cli("DEL", "lab:clock:offset")
    time.sleep(1.2)                                   # 앱이 랩 시계 값을 1초마다 다시 읽는다


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s4-07/express.diff).")
    clear_counters()
    with portal() as c:
        token = c.post("/sso/login", data={"username": USER, "password": ACCOUNTS[USER]["password"]}).json()["data"]["token"]
        h = {"Authorization": "Bearer " + token}
        before = c.get("/member/refund-account", headers=h).json().get("data") or {}
        try:
            lab_clock(10)
            r = c.post("/member/refund-account", headers=h, data={"bank": FAKE_BANK, "account": FAKE_ACCOUNT})
            body = r.json()
        finally:
            lab_clock(None)
        changed = r.status_code == 200 and body.get("code") == 200
        if changed and not before.get("bank"):        # 시드에는 환불 계좌가 없다 → 재연이 넣은 값을 지워 원래대로
            from daon import stack as lab
            lab.redis_cli("DEL", f"mall:refund:{ACCOUNTS[USER]['member_id']}")
    ev = [f"가짜 계정 {USER} 로그인 → 랩 시계 10분 뒤 → POST /member/refund-account (은행 {FAKE_BANK}, 계좌 가짜 값)",
          f"응답: HTTP {r.status_code} · code={body.get('code')} · {body.get('message')!r}" +
          (f" · data={body.get('data')!r}" if body.get('data') == 'step_up_required' else ""),
          "랩 시계를 되돌렸습니다" + (" · 바뀐 환불 계좌를 지웠습니다" if changed else "")]
    if changed:
        return Result(True, "로그인한 지 10분 지난 토큰만으로 환불 계좌가 바뀌었다", ev)
    return Result(False, "로그인한 지 오래된 토큰으로는 바꿀 수 없고 재인증(step_up_required)을 요구한다", ev)
