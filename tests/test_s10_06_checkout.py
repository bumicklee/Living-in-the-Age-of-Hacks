"""10-6 카드 테스팅·사재기·프로모 남용 · T27(PART=card·hoard).

시작 지점(10-4 완료 상태): /pay/start에 한도가 없어 30번 모두 결제사까지 간다, 같은 카드 지문으로 여러 계정이 시도해도 된다,
          한정 상품을 한 계정이 20개 산다 → 레드.
완료 지점: ① 한 계정 6번째 결제 시도부터 429 + checkout.denied.rate_limit ② 같은 카드 지문으로 4번째 계정 429
          ③ 한정 상품 230 1인 2개 넘는 주문 거절 + purchase.denied.per_member_limit ④ T27 card 재연에서 결제사까지 5번 이하
          ⑤ 세 계정이 거절 카드로 12번 → payment.suspected.card_testing(실패율 경보)
정상 시험: alice 정상 결제 1건, bob 한정 상품 2개 주문.
시험마다 랩 카운터(rl:*)를 먼저 비운다(10분 창이 앞 시험과 섞이지 않게).
"""
import pytest

from replay import _logs
from replay import _money as m
from replay import t27_abuse as t27
from replay._http import new_trace_id, portal

NO_ORDER = 999999           # 없는 주문: 한도 검사는 주문을 찾기 전에 한다


def pay(user: str, order_id: int, token: str) -> int:
    with portal() as c:
        r = c.post("/pay/start", params={"orderId": order_id, "cardToken": token},
                   headers={"Authorization": m.login(c, user), "X-Trace-Id": new_trace_id("s106")})
    return r.status_code


def test_account_axis_limits_after_five():
    m.clear_counters()
    since = _logs.now()
    codes = [pay("bob", NO_ORDER, "tok_test_1111") for _ in range(7)]
    assert codes[:5] == [404] * 5 and codes[5] == 429, f"기대: 5번까지 404(없는 주문), 6번째부터 429. 실제 {codes}"
    e = _logs.wait_event("mall-portal", since, "checkout.denied.rate_limit", "10-6", timeout=8)
    assert e and e.get("actor") == "member:102" and "계정" in str(e.get("reason")), f"기대: checkout.denied.rate_limit(계정 축). 실제 {e}"


def test_card_axis_limits_fourth_account():
    m.clear_counters()
    codes = [pay(u, NO_ORDER, "tok_test_7777") for u in ("alice", "bob", "daon103", "daon104")]
    assert codes[:3] == [404] * 3 and codes[3] == 429, f"기대: 같은 카드 지문은 계정 3개까지, 4번째 계정 429. 실제 {codes}"


def test_limited_product_per_member():
    m.clear_counters()
    since = _logs.now()
    h = t27.hoard_scene("bob")
    assert not h["ordered"], f"기대: 한정 상품 20개 주문 거절. 실제 {h}"
    e = _logs.wait_event("mall-portal", since, "purchase.denied.per_member_limit", "10-6", timeout=8)
    assert e and e.get("target") == "product:230", f"기대: purchase.denied.per_member_limit(product:230). 실제 {e}"


def test_card_testing_replay_is_cut():
    r = t27.run("spring", part="card")
    assert not r.vulnerable, "기대: 결제 시도 30번 중 결제사까지는 5번 이하\n" + r.show()


def test_failure_rate_alert():
    m.clear_counters()
    since = _logs.now()
    orders = {u: m.new_order(u, 203) for u in ("alice", "bob", "daon103")}
    tokens = iter(m.declined_tokens(12))
    for u, o in orders.items():
        for _ in range(4):
            pay(u, o["id"], next(tokens))
    m.close_orders([o["id"] for o in orders.values()])
    e = _logs.wait_event("mall-portal", since, "payment.suspected.card_testing", "10-6", timeout=8)
    assert e and e.get("actor") == "system:checkout", f"기대: 거절 12번 → payment.suspected.card_testing. 실제 {e}"


@pytest.mark.normal
def test_normal_payment_passes():
    m.clear_counters()
    o = m.new_order("alice", 203)
    assert pay("alice", o["id"], "tok_test_4242") == 200, "기대: 정상 결제 시작 200"
    assert m.wait_status(o["id"], 1) == 1, "기대: 웹훅으로 결제 완료"


@pytest.mark.normal
def test_two_limited_items_are_fine():
    m.clear_counters()
    m.reset_purchase(102, 230)
    with portal() as c:
        h = {"Authorization": m.login(c, "bob"), "X-Trace-Id": new_trace_id("s106n")}
        cid = m.cart_line(c, h, 230, 2)
        o = m.order_of(m.place_order(c, h, "bob", cid))
    assert o, "기대: 한정 상품 2개(한도 안)는 주문됨"
    m.close_orders([o["id"]])
