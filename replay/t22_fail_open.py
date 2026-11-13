"""T22 · 본인 확인 서버가 오류를 내면 고위험 작업의 본인 확인이 통과된다(fail-open, A10). 수업 9-2.

가짜 문자 스텁(9102)에 장애(500)를 넣고, alice가 틀린 인증 번호(000000)로 「문자 본인 확인」을 한다.
  레드: HTTP 200 「본인 확인을 마쳤습니다」(오류를 통과로 처리)
  그린: HTTP 503 「잠시 뒤 다시 시도해 주세요」(확인 불가로 거부) + auth.fail_closed.dependency_error
끝나면 스텁을 늘 정상(ok)으로 되돌린다. 값은 전부 랩 전용 가짜다.
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._faults import set_fault
from replay._http import new_trace_id, portal, portal_login

T = "T22"
TITLE = "본인 확인 서버 오류 때 본인 확인이 통과됨(fail-open)"
LESSON = "9-2"
SOURCE = "[교육용 추가] DaonStepUpClient.verify()의 catch → return true(「장애 때 고객 불편 줄이기」)"
STACKS = ("spring",)
WRONG = "000000"


def attempt(user: str = "alice", fault: str = "500", code: str = WRONG, ms: int = 0) -> tuple[int, dict, str]:
    """스텁을 fault 모드로 두고 code로 확인 한 번. (HTTP 상태, 본문, trace_id). 스텁은 되돌린다"""
    tid = new_trace_id("t22")
    with portal() as c:
        token = portal_login(c, user)
        try:
            set_fault("sms", fault, ms)
            r = c.post("/daon/verify/sms/check", params={"code": code}, headers={"Authorization": token, "X-Trace-Id": tid})
        finally:
            set_fault("sms", "ok")
    try:
        body = r.json()
    except ValueError:
        body = {}
    return r.status_code, body, r.headers.get("X-Trace-Id", tid)


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s9-02).")
    status, body, tid = attempt()
    ev = ["장애 넣기: 가짜 문자 스텁 → 500 (make stub-fault S=sms MODE=500과 같음)",
          f"요청: alice 토큰으로 POST /daon/verify/sms/check?code={WRONG} (틀린 번호)",
          f"응답: HTTP {status} · code={body.get('code')} · {body.get('message')} · X-Trace-Id={tid}",
          "스텁은 정상(ok)으로 되돌렸습니다"]
    if status == 200 and body.get("data") == "verified":
        return Result(True, "본인 확인 서버가 오류를 내자 틀린 번호로도 본인 확인이 통과됐다", ev)
    if status == 503:
        return Result(False, "확인할 수 없어서 503으로 거부했다(fail-closed)", ev)
    return Result(False, f"통과되지 않았다(HTTP {status})", ev)
