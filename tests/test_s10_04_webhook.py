"""10-4 결제 콜백 멱등 처리와 단방향 상태 전이 · T26(점검표 F02·F44).

시작 지점: bob이 /order/paySuccess로 alice 주문을 결제 완료로 바꾼다, 웹훅 수신(/pay/webhook)·결제 시작(/pay/start)이 없다 → 레드.
완료 지점: ① paySuccess 410·상태 그대로 + payment.denied.client_call ② 같은 웹훅 두 번(resend)에도 결제 완료·재고 차감 한 번
          ③ 서명이 틀린 웹훅 401 + payment.denied.bad_signature ④ 금액이 다른 웹훅 422·상태 그대로 + payment.denied.amount_mismatch
          ⑤ 이미 결제 완료인 주문에 새 결제 알림 409 + payment.denied.bad_transition ⑥ 고객 웹 흐름(/pay/start → 웹훅)으로 정상 결제.
시험이 「결제사가 보낸 것처럼」 만드는 웹훅은 랩 전용 가짜 비밀(lab-fake-webhook-secret)로 서명한다(replay/_money.sign).
"""
import json
import time
import uuid

import pytest

from replay import _logs
from replay import _money as m
from replay import t26_webhook as t26
from replay._http import new_trace_id, portal


def hook(order_sn: str, amount, pid: str | None = None) -> bytes:
    return json.dumps({"event": "payment.approved", "payment_id": pid or ("LAB" + uuid.uuid4().hex[:10].upper()),
                       "order_sn": order_sn, "amount": amount, "ts": int(time.time())}, ensure_ascii=False).encode()


def test_client_pay_success_is_gone():
    since = _logs.now()
    a = t26.client_call_scene()
    assert a["status"] == 0, f"기대: bob의 paySuccess 뒤에도 alice 주문은 결제 대기(0). 실제 상태 {a['status']} (HTTP {a['http']})"
    assert a["http"] in (404, 410), f"기대: HTTP 410(없앤 API). 실제 {a['http']}"
    e = _logs.wait_event("mall-portal", since, "payment.denied.client_call", "10-4", timeout=8)
    assert e and e.get("actor") == "member:102", f"기대: SECURITY payment.denied.client_call(actor member:102). 실제 {e}"


def test_duplicate_webhook_applied_once():
    b = t26.duplicate_scene()
    assert b is not None, "기대: POST /pay/start가 있음(10-4 정답)"
    assert b["status_after_first"] == 1, f"기대: 결제사 웹훅 뒤 결제 완료(1). 실제 {b}"
    assert b["stock_drop_total"] == 1 and b["stock_drop_first"] == 1, f"기대: 재고 차감 한 번. 실제 {b}"
    assert b["hooks"] and all(s == 200 for s in b["hooks"]) and len(b["hooks"]) >= 2, f"기대: 같은 웹훅 두 번 모두 200. 실제 {b['hooks']}"
    assert b["event_rows"] == 1, f"기대: oms_payment_event 한 줄(중복은 기록하지 않음). 실제 {b['event_rows']}"


def test_bad_signature_is_401_and_logged():
    since = _logs.now()
    o = m.new_order("alice", 203)
    try:
        r = m.post_webhook(hook(o["orderSn"], o["payAmount"]), "0" * 64)
        assert r.status_code == 401, f"기대: 서명이 틀린 웹훅은 401. 실제 {r.status_code}"
        assert m.order_status(o["id"]) == 0, "기대: 주문 상태 그대로"
        e = _logs.wait_event("mall-portal", since, "payment.denied.bad_signature", "10-4", timeout=8)
        assert e and e.get("actor") == "anonymous", f"기대: SECURITY payment.denied.bad_signature. 실제 {e}"
    finally:
        m.close_orders([o["id"]])


def test_amount_mismatch_is_rejected():
    since = _logs.now()
    o = m.new_order("alice", 203)
    try:
        body = hook(o["orderSn"], float(o["payAmount"]) + 1000)
        r = m.post_webhook(body, m.sign(body))
        assert r.status_code == 422, f"기대: 금액이 다른 웹훅은 422(상태 바꾸지 않음). 실제 {r.status_code} {r.text[:80]}"
        assert m.order_status(o["id"]) == 0, "기대: 주문 상태 그대로(0)"
        e = _logs.wait_event("mall-portal", since, "payment.denied.amount_mismatch", "10-4", timeout=8)
        assert e and e.get("target") == f"order:{o['id']}", f"기대: SECURITY payment.denied.amount_mismatch(target order:{o['id']}). 실제 {e}"
    finally:
        m.close_orders([o["id"]])


def test_second_payment_on_paid_order_is_bad_transition():
    since = _logs.now()
    o = m.new_order("alice", 203)
    first = hook(o["orderSn"], o["payAmount"])
    r1 = m.post_webhook(first, m.sign(first))
    assert r1.status_code == 200 and m.order_status(o["id"]) == 1, f"기대: 서명·금액이 맞는 첫 알림으로 결제 완료. 실제 {r1.status_code} {r1.text[:80]}"
    second = hook(o["orderSn"], o["payAmount"])                       # 다른 결제 번호로 같은 주문을 또 결제 완료로
    r2 = m.post_webhook(second, m.sign(second))
    assert r2.status_code == 409, f"기대: 결제 완료 → 결제 완료 같은 중복 전이는 409. 실제 {r2.status_code}"
    e = _logs.wait_event("mall-portal", since, "payment.denied.bad_transition", "10-4", timeout=8)
    assert e and e.get("target") == f"order:{o['id']}", f"기대: SECURITY payment.denied.bad_transition. 실제 {e}"


def test_customer_flow_pays_through_webhook():
    o = m.new_order("alice", 203)
    with portal() as c:
        r = c.post("/pay/start", params={"orderId": o["id"], "cardToken": "tok_test_4242"},
                   headers={"Authorization": m.login(c, "alice"), "X-Trace-Id": new_trace_id("s104ok")})
    assert r.status_code == 200, f"기대: 결제 시작 200. 실제 {r.status_code} {r.text[:80]}"
    assert m.wait_status(o["id"], 1) == 1, "기대: 가짜 결제사 웹훅이 와서 결제 완료(1)"


@pytest.mark.normal
def test_order_creation_still_works():
    o = m.new_order("alice", 203)
    m.close_orders([o["id"]])
    assert o.get("orderSn") and o.get("status") == 0, f"기대: 주문은 결제 대기로 만들어짐. 실제 {o}"
