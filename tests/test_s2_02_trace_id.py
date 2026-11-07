"""2-2 · 요청 추적 번호(trace_id, A3). 랩-설계.md 11절.

요청 하나가 보낸 X-Trace-Id가 응답 헤더와 SECURITY 로그 줄에 같은 값으로 남는지 본다.
T01 재연(bob이 alice의 주문을 조회)과 같은 요청을 쓴다.
"""
import pytest

from replay import _logs
from replay import t01_order_idor as t01
from replay._http import ACCOUNTS, new_trace_id


def test_trace_id_reaches_security_log(stack):
    since = _logs.now()
    tid = new_trace_id("a3")
    target = ACCOUNTS["alice"]["orders"][0]
    r = t01.order_detail_response("bob", target, tid)
    assert r.headers.get("X-Trace-Id") == tid, f"기대: 응답 헤더 X-Trace-Id={tid}. 실제: {r.headers.get('X-Trace-Id')}"
    e = _logs.wait_event("mall-portal", since, "access.denied.not_owner", "2-2", stack, trace_id=tid)
    assert e, f"기대: mall-portal SECURITY access.denied.not_owner 줄의 trace_id가 요청의 X-Trace-Id({tid})와 같아야 합니다"


@pytest.mark.normal
def test_trace_id_echoed_or_generated(stack):
    oid = ACCOUNTS["alice"]["orders"][0]
    tid = new_trace_id("a3")
    assert t01.order_detail_response("alice", oid, tid).headers.get("X-Trace-Id") == tid, \
        "기대: 보낸 X-Trace-Id를 응답 헤더로 그대로 돌려준다"
    made = t01.order_detail_response("alice", oid).headers.get("X-Trace-Id", "")
    assert len(made) == 32 and all(c in "0123456789abcdef" for c in made), \
        f"기대: 헤더가 없으면 32자리 새 번호를 만든다. 실제: {made!r}"
    bad = "x" * 80 + "'\"<>"
    got = t01.order_detail_response("alice", oid, bad).headers.get("X-Trace-Id", "")
    assert got != bad and len(got) == 32, "기대: 모양이 이상한 값(너무 길거나 따옴표·꺾쇠가 섞임)은 버리고 새로 만든다(로그 위조 막기)"
