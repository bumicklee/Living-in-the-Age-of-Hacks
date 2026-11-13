"""T20 · 관리자 계정으로 회원 대량 조회: 기록도 경보도 없다(A09, 점검표 F40). 수업 8-3(감사 로그)·8-4(경보).

관리자 admin이 고객센터 회원 목록(/daon/customer/list, 교육용 추가)을 한 번에 받는다(시드 회원 40명 남짓, 기준 30건을 넘는다).
같은 요청을 사유 없이 두 번 부른다(페이지를 바꿔 여러 번 부르는 경우 흉내).
  레드: 감사 로그(AUDIT)에 그 요청이 없거나(8-3 전), 기록은 있는데 아무에게도 알림이 가지 않는다(8-4 전)
  그린: AUDIT 줄이 요청마다 남고(count = 돌려준 건수), 정해진 시간 안에 Mailpit에 [다온 경보] data.read.bulk 메일이 온다
정상 계정의 정상 기능이라 「공격 코드」가 아니다. 값은 가짜 시드이고 화면에는 건수만 보인다.
"""
from __future__ import annotations

import os
import time

import httpx

from replay import NotReady, Result
from replay import _obs
from replay._http import admin, admin_login, new_trace_id

T = "T20"
TITLE = "관리자 계정의 회원 대량 조회가 기록·경보 없이 지나감"
LESSON = "8-3·8-4"
SOURCE = "[원본 기본값] 관리자 조회 감사 로그 없음(F40) + [교육용 추가] 회원 목록 /daon/customer/list"
STACKS = ("spring",)
CALLS = 2
EVENT = "data.read.bulk"


def bulk_read(trace_tag: str = "t20", calls: int = CALLS, reason: str | None = None) -> list[tuple[str, int, int]]:
    """admin으로 회원 목록을 calls번. [(trace_id, HTTP 상태, 돌려받은 건수)]"""
    from replay.t09_login_guard import clear_counters
    out = []
    try:
        with admin() as c:
            clear_counters()
            tok = admin_login(c, "admin")
            for _ in range(calls):
                tid = new_trace_id(trace_tag)
                h = {"Authorization": tok, "X-Trace-Id": tid} | ({"X-Access-Reason": reason} if reason else {})
                r = c.get("/daon/customer/list", headers=h)
                data = (r.json() or {}).get("data") or []
                out.append((r.headers.get("X-Trace-Id", tid), r.status_code, len(data)))
    except httpx.TransportError as e:
        raise NotReady(f"mall-admin에 닿지 않습니다: {e.__class__.__name__}")
    return out


def run(stack: str = "spring", dynamic_run: bool = True) -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s8-03).")
    start = _obs.now_utc()
    calls = bulk_read()
    ev = [f"요청: admin 토큰으로 GET /daon/customer/list × {len(calls)} (사유 헤더 없음)"]
    ev += [f"  응답 HTTP {s} · {n}건 · X-Trace-Id={tid}" for tid, s, n in calls]
    logged = [_obs.wait_audit(tid, stack, timeout=6 if i == 0 else 2) for i, (tid, _, _) in enumerate(calls)]
    have = [d for d in logged if d]
    ev.append(f"감사 로그(AUDIT, mall-admin {_obs.AUDIT_FILE}): {len(have)}/{len(calls)}줄"
              + (f" · 예: action={have[0].get('action')} actor={have[0].get('actor')} count={have[0].get('count')} reason={have[0].get('reason')}" if have else ""))
    if not have:
        return Result(True, "회원을 한 번에 받아 갔는데 누가·몇 명을·왜 봤는지 기록이 없다", ev)
    if not _obs.a05_on():
        ev.append("경보: 모으는 곳(A5)이 아직 없다 → 아무에게도 알림이 가지 않음(8-4)")
        return Result(True, "기록은 있는데 아무도 모른다(경보 없음)", ev)
    wait = float(os.environ.get("DAON_ALERT_WAIT", "150")) if dynamic_run else 0
    # 같은 계정의 경보가 이미 울리는 중이면 새 메일은 반복 간격 뒤에 온다: 최근 15분 안의 메일도 인정한다
    mail = _obs.wait_mail(EVENT, since=start - 900, timeout=min(wait, 30))
    if mail is None:
        got = _obs.wait_alert(EVENT, since=start - 900, actor_part="admin:admin", timeout=wait)
        if got is None:
            ev.append(f"Mailpit({_obs.MAILPIT}): [다온 경보] {EVENT} 메일 없음({int(wait)}초 기다림)")
            return Result(True, "기록은 있는데 경보 메일이 오지 않는다", ev)
        ev.append(got)
        return Result(False, "대량 조회가 감사 로그에 남고 경보가 울렸다", ev)
    ev.append(f"Mailpit: 「{mail['subject']}」 · 받는 사람 {', '.join(mail['to'])} · 요청 뒤 {max(0, int(mail['at'] - start))}초")
    return Result(False, "대량 조회가 감사 로그에 남고 경보 메일이 왔다", ev)
