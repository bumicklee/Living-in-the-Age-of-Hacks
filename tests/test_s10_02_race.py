"""10-2 DB로 막기: 조건부 UPDATE·유니크 제약 · T25(점검표 F43·F38).

시작 지점(10-1과 같다): 쿠폰 받기 100개 동시에 alice가 쿠폰 101을 여러 장, 포인트 1,000점으로 동시 주문 여러 개 → 레드.
완료 지점: 쿠폰 1장(성공 1·거절 99, 줄어든 수량 = 저장된 행), 주문 1개·남은 포인트 0, coupon.denied.limit_reached·points.denied.insufficient.
정상 시험: bob이 쿠폰 102를 한 장 받는다, bob이 포인트를 조금 써서 주문한다.
재연 장면(100개·10개 동시)은 이 파일에서 한 번만 돌리고 결과를 나눠 쓴다.
"""
import functools

import pytest

from replay import _logs
from replay import _money as m
from replay import t25_race as t25
from replay._http import new_trace_id, portal


@functools.lru_cache(maxsize=1)
def coupon():
    since = _logs.now()
    return t25.coupon_scene(), since


@functools.lru_cache(maxsize=1)
def pts():
    since = _logs.now()
    return t25.points_scene(), since


def test_coupon_once_under_100_concurrent():
    s, _ = coupon()
    assert s["rows"] == 1 and s["ok"] == 1, f"기대: 100개 동시 받기에서 alice의 쿠폰 101은 1장(성공 1). 실제 성공 {s['ok']} · 저장 {s['rows']}장"


def test_coupon_count_drop_equals_rows():
    s, _ = coupon()
    assert s["drop"] == s["rows"], f"기대: 줄어든 수량 = 저장된 행 수. 실제 줄어든 수량 {s['drop']}, 행 {s['rows']}"


def test_coupon_denial_logged():
    s, since = coupon()
    e = _logs.wait_event("mall-portal", since, "coupon.denied.limit_reached", "10-2", timeout=8)
    assert e, f"기대: SECURITY coupon.denied.limit_reached(10-2). 거절 {s['denied']}건"
    assert e.get("actor") == "member:101" and e.get("target") == "coupon:101", f"실제 {e}"


def test_points_once_under_10_concurrent():
    s, _ = pts()
    assert s["orders"] == 1, f"기대: 포인트 1,000점으로 동시 주문 10개 중 1개만 성공. 실제 {s['orders']}개"
    assert s["left"] == 0, f"기대: 남은 포인트 0. 실제 {s['left']}"


def test_points_denial_logged():
    s, since = pts()
    e = _logs.wait_event("mall-portal", since, "points.denied.insufficient", "10-2", timeout=8)
    assert e and e.get("target") == "member:101", f"기대: SECURITY points.denied.insufficient(10-2, member:101). 실제 {e}"


@pytest.mark.normal
def test_bob_receives_coupon_once():
    m.reset_coupon(102, 102)
    with portal() as c:
        h = {"Authorization": m.login(c, "bob"), "X-Trace-Id": new_trace_id("s102c")}
        first = c.post("/member/coupon/add/102", headers=h).json()
    assert first.get("code") == 200, f"기대: bob이 쿠폰 102를 받음. 실제 {first}"
    assert m.coupon_state(102, 102)["rows"] == 1


@pytest.mark.normal
def test_bob_orders_with_some_points():
    m.set_points("bob", 1000)
    with portal() as c:
        h = {"Authorization": m.login(c, "bob"), "X-Trace-Id": new_trace_id("s102p")}
        cid = m.cart_line(c, h, 202, 1)
        r = m.place_order(c, h, "bob", cid, 100)
    o = m.order_of(r)
    try:
        assert o, f"기대: 포인트 100점을 쓰는 정상 주문 성공. 실제 {r.text[:120]}"
        assert m.points(102) == 900, f"기대: 남은 포인트 900. 실제 {m.points(102)}"
    finally:
        m.close_orders([o.get("id")] if o else [])
