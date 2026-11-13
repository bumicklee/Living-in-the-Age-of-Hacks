"""8-3 관리자 조회에 감사 로그(AUDIT) 남기기 · 재연 T20.

시작 지점: 관리자 조회 4개를 불러도 AUDIT 줄이 없다 → 레드.
완료 지점: 조회마다 AUDIT 줄 하나(count = 돌려준 건수, trace_id = 응답 헤더 X-Trace-Id), 전화번호·토큰 없음, 앱 로그에 섞이지 않음,
          30건 이상 조회는 SECURITY data.read.bulk(8-3). 응답 본문은 그대로.
"""
import uuid

import pytest

from daon import stack as st
from replay import _logs, _obs
from replay._http import admin, admin_login


def tid(tag: str) -> str:
    return f"lab-s83-{tag}-{uuid.uuid4().hex[:10]}"


def admin_reads(reason: str | None = None) -> list[tuple[str, str, int, dict]]:
    """(action, trace_id, 돌려받은 건수, 응답 JSON)"""
    from replay.t09_login_guard import clear_counters
    out = []
    with admin() as c:
        clear_counters()
        tok = admin_login(c, "admin")
        for action, path, params in [("customer.list", "/daon/customer/list", {}),
                                     ("order.list", "/order/list", {"pageNum": 1, "pageSize": 5}),
                                     ("order.detail", "/order/9001", {}),
                                     ("return.list", "/returnApply/list", {"pageNum": 1, "pageSize": 5})]:
            t = tid(action.replace(".", ""))
            h = {"Authorization": tok, "X-Trace-Id": t} | ({"X-Access-Reason": reason} if reason else {})
            r = c.get(path, params=params, headers=h)
            body = r.json()
            data = body.get("data")
            n = len(data) if isinstance(data, list) else len((data or {}).get("list") or []) if isinstance(data, dict) and "list" in data else (1 if data else 0)
            out.append((action, r.headers.get("X-Trace-Id", t), n, body))
    return out


def test_each_admin_read_writes_one_audit_line():
    reads = admin_reads()
    for action, t, n, _ in reads:
        line = _obs.wait_audit(t, timeout=6)
        assert line, f"기대: {action} 조회가 감사 로그(AUDIT, {_obs.AUDIT_FILE})에 한 줄(trace_id={t})"
        assert line.get("action") == action and line.get("actor") == "admin:admin" and line.get("result") == "ok", f"기대: action·actor·result. 실제 {line}"
        assert line.get("count") == n, f"기대: count는 실제로 돌려준 건수({n}). 실제 {line.get('count')}"
        for k in ("ts", "trace_id", "actor", "action", "target", "count", "reason", "result"):
            assert k in line, f"기대: 필수 필드 {k}"
    lines = [l for l in _obs.audit_lines(tail=400) if l.get("trace_id") in {r[1] for r in reads}]
    assert len(lines) == len(reads), f"기대: 조회 하나에 줄 하나. 실제 {len(lines)}줄"


def test_audit_lines_have_no_phone_or_token():
    reads = admin_reads()
    _obs.wait_audit(reads[-1][1], timeout=6)
    text = "\n".join(str(l) for l in _obs.audit_lines(tail=400) if l.get("trace_id") in {r[1] for r in reads})
    assert text, "기대: 감사 로그 줄이 있음"
    assert not _obs.PHONE.search(text) and not _obs.JWT.search(text), "기대: 감사 로그에 전화번호·토큰 모양 문자열이 없음"


def test_audit_lines_are_not_mixed_into_app_log():
    since = _logs.now()
    reads = admin_reads()
    _obs.wait_audit(reads[-1][1], timeout=6)
    out = st.compose("spring", "logs", "--no-log-prefix", "--since", since, "mall-admin", check=False, capture=True).stdout or ""
    assert reads[0][1] in "\n".join(l.get("trace_id", "") for l in _obs.audit_lines(tail=100)), "기대: 감사 로그 파일에는 있음"
    mixed = [ln for ln in out.splitlines() if " AUDIT " in ln and "{" in ln]
    assert not mixed, "기대: AUDIT 줄은 앱 로그에 섞이지 않음(additivity=false)"


def test_bulk_read_leaves_security_event():
    since = _logs.now()
    reads = admin_reads()
    cust = reads[0]
    assert cust[2] >= 30, f"기대: 시드 회원이 기준(30)보다 많음. 실제 {cust[2]}명"
    e = _logs.wait_event("mall-admin", since, "data.read.bulk", "8-3", timeout=8, trace_id=cust[1])
    assert e, "기대: 30건 이상 조회에 SECURITY data.read.bulk(8-3, INFO)"
    assert e.get("actor") == "admin:admin" and e.get("target") == "action:customer.list", f"기대: actor admin:admin, target action:customer.list. 실제 {e}"


def test_reason_header_is_recorded():
    reads = admin_reads(reason="CS-2026-1110")
    line = _obs.wait_audit(reads[0][1], timeout=6)
    assert line and line.get("reason") == "CS-2026-1110", f"기대: X-Access-Reason이 reason에. 실제 {line}"


@pytest.mark.normal
def test_admin_responses_unchanged():
    reads = admin_reads()
    cust = reads[0][3]
    assert cust.get("code") == 200 and isinstance(cust.get("data"), list) and cust["data"], "기대: 회원 목록은 그대로 옴"
    assert {"id", "username", "phone"} <= set(cust["data"][0]), f"기대: 응답 모양 그대로. 실제 키 {list(cust['data'][0])}"
    assert reads[1][3].get("code") == 200 and reads[2][3].get("code") == 200, "기대: 주문 목록·상세 그대로"
