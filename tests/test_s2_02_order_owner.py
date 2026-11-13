"""2-2 소유권 검사를 어디에 둘까 · T01(IDOR)."""
import pytest

from replay import _logs
from replay import t01_order_idor as t01
from replay._http import ACCOUNTS, new_trace_id


def test_bob_cannot_read_alice_order(stack):
    r = t01.run(stack)
    assert not r.vulnerable, "기대: bob 토큰으로 alice의 주문을 조회하면 주문 정보가 오지 않아야 합니다\n" + r.show()


def test_other_order_looks_like_missing_order(stack):
    other = t01.order_detail("bob", ACCOUNTS["alice"]["orders"][0])
    missing = t01.order_detail("bob", 987654321)
    assert other.get("code") != 200 and other.get("code") == missing.get("code"), \
        "기대: 남의 주문과 없는 주문의 응답이 같아야 합니다(주문이 있다는 사실도 새지 않게)"


def test_denial_is_logged(stack):
    since = _logs.now()
    target = ACCOUNTS["alice"]["orders"][0]
    tid = new_trace_id("s22deny")
    t01.order_detail_response("bob", target, tid)
    e = _logs.wait_event("mall-portal", since, "access.denied.not_owner", "2-2", stack,
                         timeout=15, trace_id=tid)
    assert e, "기대: 거부하면 mall-portal 로그에 SECURITY access.denied.not_owner(2-2) 한 줄이 남아야 합니다"
    assert e.get("actor") == f"member:{ACCOUNTS['bob']['member_id']}" and e.get("target") == f"order:{target}", \
        f"기대: actor=member:102, target=order:{target}(ID만). 실제: {e}"


@pytest.mark.normal
def test_alice_reads_own_order(stack):
    for oid in ACCOUNTS["alice"]["orders"]:
        body = t01.order_detail("alice", oid)
        assert body.get("code") == 200 and body["data"]["id"] == oid, f"기대: alice는 자기 주문 {oid}을 그대로 볼 수 있어야 합니다"
