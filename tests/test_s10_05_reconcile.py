"""10-5 원장 대사: 숫자가 맞는지 매일 확인 · (F38의 결과).

시작 지점: 대사 작업이 없다(POST /daon/reconcile/run 없음), 주문에 포인트를 써도 원장(ums_integration_change_history)에 줄이 없다 → 레드.
완료 지점: ① ledger-break(원장 없이 포인트만 바꿈)한 회원 하나를 대사가 집어냄 + ledger.mismatch(mall-admin)
          ② 손대지 않은 시드 회원은 불일치 목록에 없음 ③ 포인트를 쓴 주문 뒤에도 그 회원은 원장과 맞음(원장 줄을 남김)
          ④ 결제사 승인 기록 없이 결제 완료가 된 주문(F02 같은 길)을 대사가 집어냄.
시험용 회원: ledger-break는 bulk 시드 고객 139(daon139), 깨끗한 기준은 140(daon140). 끝나면 139를 원장 합계로 되돌린다.
「시드 상태에서 불일치 0」은 make wipe 직후에만 맞다(앞 수업 재연이 포인트를 바꿈) → 2단계 실측(구현 메모).
"""
import pytest

from daon import money
from replay import _logs
from replay import _money as m
from replay._http import new_trace_id, portal

BROKEN, CLEAN = 139, 140


def test_broken_member_is_found():
    m.ledger_fix(BROKEN)
    since = _logs.now()
    m.ledger_break(BROKEN)
    try:
        d = money.reconcile()
        assert BROKEN in (d.get("members") or []), f"기대: 대사가 회원 {BROKEN}을 집어냄. 실제 {d.get('members')}"
        e = _logs.wait_event("mall-admin", since, "ledger.mismatch", "10-5", timeout=8, target=f"member:{BROKEN}")
        assert e and e.get("actor") == "system:reconcile", f"기대: SECURITY ledger.mismatch(target member:{BROKEN}). 실제 {e}"
    finally:
        m.ledger_fix(BROKEN)


def test_untouched_member_is_clean():
    d = money.reconcile()
    assert CLEAN not in (d.get("members") or []), f"기대: 손대지 않은 시드 회원 {CLEAN}은 잔액 = 원장 합계"


def test_points_order_keeps_ledger():
    m.set_points("bob", 1000)
    with portal() as c:
        h = {"Authorization": m.login(c, "bob"), "X-Trace-Id": new_trace_id("s105")}
        cid = m.cart_line(c, h, 202, 1)
        o = m.order_of(m.place_order(c, h, "bob", cid, 100))
    assert o, "기대: 포인트 100점을 쓰는 주문 성공"
    m.close_orders([o["id"]])
    assert m.points(102) == m.ledger_sum(102), f"기대: 주문 뒤 bob 잔액 {m.points(102)} = 원장 합계 {m.ledger_sum(102)}(원장 줄을 남김)"
    d = money.reconcile()
    assert 102 not in (d.get("members") or []), "기대: 대사에서 bob은 맞음"


def test_paid_without_pg_record_is_found():
    o = m.new_order("alice", 203)
    m.mysql(f"UPDATE oms_order SET status=1, payment_time=NOW() WHERE id={int(o['id'])}")      # 결제사 승인 없이 결제 완료(F02 같은 길을 흉내)
    d = money.reconcile()
    assert o["id"] in (d.get("orders") or []), f"기대: 결제사 승인 기록 없는 결제 완료 주문 {o['id']}을 대사가 집어냄. 실제 {d.get('orders')}"


@pytest.mark.normal
def test_points_order_succeeds():
    m.set_points("bob", 1000)
    with portal() as c:
        h = {"Authorization": m.login(c, "bob"), "X-Trace-Id": new_trace_id("s105n")}
        cid = m.cart_line(c, h, 202, 1)
        o = m.order_of(m.place_order(c, h, "bob", cid, 100))
    assert o, "기대: 포인트를 쓰는 정상 주문은 그대로 된다"
    m.close_orders([o["id"]])
