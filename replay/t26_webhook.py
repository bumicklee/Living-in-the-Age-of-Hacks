"""T26 · 결제 상태를 바꾸는 입구가 고객에게 열려 있고, 같은 결제 알림이 두 번 처리될 수 있다. 수업 10-3(사고)·10-4(막기). 점검표 F02·F44.

장면 A(F02): alice의 결제 대기 주문을 하나 만들고, bob 토큰으로 POST /order/paySuccess?orderId=<그 주문>&payType=1.
  레드: 주문 상태가 「결제 완료」(1)로 바뀜   그린: 410(없앤 API) 또는 404, 상태 그대로(0) + payment.denied.client_call
장면 B(중복 웹훅, 10-4 정답 뒤에만): alice가 POST /pay/start로 가짜 결제사에 결제를 시작 → 결제사가 서명한 웹훅이 와서 결제 완료 →
  가짜 결제사 resend로 같은 웹훅을 한 번 더. 두 웹훅 모두 200이지만 결제 완료·재고 차감은 한 번뿐이어야 한다.
  (/pay/start가 없으면 장면 B는 건너뛴다: 시작 지점에는 웹훅 수신이 없다)
만든 결제 대기 주문은 끝에 닫는다. 값은 전부 랩의 가짜 값이다.
"""
from __future__ import annotations

import time

from replay import NotReady, Result
from replay import _money as m
from replay._http import new_trace_id, portal

T = "T26"
TITLE = "고객이 부르는 「결제 성공」·같은 결제 알림 두 번 처리(결제 상태 입구)"
LESSON = "10-4"
SOURCE = "[원본 확인됨] /order/paySuccess를 로그인한 회원 누구나 부름(F02), /alipay/** 로그인 없이 열림·알림 금액 대조와 중복 기록 없음(F44)"
STACKS = ("spring",)
PRODUCT = 203
SKU = 2003


def client_call_scene() -> dict:
    o = m.new_order("alice", PRODUCT)
    with portal() as c:
        r = c.post("/order/paySuccess", params={"orderId": o["id"], "payType": 1},
                   headers={"Authorization": m.login(c, "bob"), "X-Trace-Id": new_trace_id("t26a")})
    st = m.order_status(o["id"])
    m.close_orders([o["id"]])
    return {"order": o["id"], "http": r.status_code, "status": st}


def pay_start(user: str, order_id: int, token: str = "tok_test_4242") -> tuple[int, dict]:
    with portal() as c:
        r = c.post("/pay/start", params={"orderId": order_id, "cardToken": token},
                   headers={"Authorization": m.login(c, user), "X-Trace-Id": new_trace_id("t26b")})
    try:
        return r.status_code, r.json()
    except ValueError:
        return r.status_code, {}


def duplicate_scene() -> dict | None:
    o = m.new_order("alice", PRODUCT)
    stock0, _ = m.sku_stock(SKU)
    http, body = pay_start("alice", o["id"])
    data = body.get("data") if isinstance(body, dict) else None
    if http != 200 or not isinstance(data, dict):
        m.close_orders([o["id"]])
        return None                                   # /pay/start 없음(10-4 이전)
    pid = data.get("paymentId")
    st1 = m.wait_status(o["id"], 1)
    stock1, _ = m.sku_stock(SKU)
    resent = m.pay_resend(pid) if pid else None
    time.sleep(2)
    st2 = m.order_status(o["id"])
    stock2, _ = m.sku_stock(SKU)
    hooks = [w for w in m.pay_webhooks() if w.get("payment_id") == pid]
    events = int(m.one(f"SELECT COUNT(*) FROM oms_payment_event WHERE payment_id='{pid}'") or 0) if pid and m.table_exists("oms_payment_event") else 0
    m.close_orders([o["id"]])
    return {"order": o["id"], "start": http, "pid": pid, "status_after_first": st1, "status_after_resend": st2,
            "stock_drop_first": stock0 - stock1, "stock_drop_total": stock0 - stock2, "resend": resent,
            "hooks": [w.get("status") for w in hooks], "event_rows": events}


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 Spring만(Express는 Stripe 웹훅이 서명·멱등을 갖춤, diffs/s10-04는 E26).")
    a = client_call_scene()
    ev = [f"장면 A: alice 결제 대기 주문 {a['order']} → bob 토큰으로 POST /order/paySuccess → HTTP {a['http']}, 주문 상태 {a['status']}"
          f" ({'결제 완료로 바뀜' if a['status'] == 1 else '그대로'})"]
    b = duplicate_scene()
    if b is None:
        ev.append("장면 B: POST /pay/start가 없습니다(웹훅 수신은 10-4에서 만든다). 건너뜀")
    else:
        ev += [f"장면 B: alice 주문 {b['order']} → /pay/start HTTP {b['start']} · 결제 {b['pid']} → 웹훅 뒤 상태 {b['status_after_first']}",
               f"  resend(HTTP {b['resend']}) 뒤 상태 {b['status_after_resend']} · 결제사가 보낸 웹훅 응답 {b['hooks']}",
               f"  재고 차감: 첫 웹훅 {b['stock_drop_first']}개, 모두 합쳐 {b['stock_drop_total']}개 · oms_payment_event {b['event_rows']}줄"]
    bad = []
    if a["status"] == 1:
        bad.append("다른 회원이 남의 주문을 결제 완료로 바꿈")
    if b is not None and b["stock_drop_total"] > 1:
        bad.append("같은 웹훅이 두 번 처리됨(재고 두 번 차감)")
    if bad:
        return Result(True, " · ".join(bad), ev)
    return Result(False, "결제 완료는 서명된 웹훅 한 번만 바꾼다", ev)
