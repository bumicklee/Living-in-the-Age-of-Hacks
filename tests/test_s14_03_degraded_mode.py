"""14-3 폭발 반경 줄이기와 축소 운영 · A06 · 9-4·9-5 이어서 · 회귀 재연 R14-3(T 번호 없음).

시작 지점(14-2 완료): 결제 확인(GET /pay/status)이 결제사를 5초까지 기다린다. 결제사가 느려지면 고객 앱 요청 처리 스레드(랩 24개)가
  모두 묶여 상품 목록까지 멈춘다. 축소 운영 스위치가 없다 → 레드.
완료 지점:
  ① 결제사 느림 모드에서 결제 확인 40개를 동시에 보내는 동안 상품 목록이 1.5초 안에 온다(벌크헤드 6자리·느린 호출 브레이커)
  ② 축소 운영은 권한 상승(13-6, 묶음 incident-response) 없이는 켜지지 않는다(403 + ops.denied.no_elevation)
  ③ 켜면: ops.mode.degraded(WARN) · 결제 시작은 202 「결제 대기」(대기 큐 daon.pay.pending, 결제사에는 아직 안 감)
     · 환불 실행 503 + 안내 문구 · 승인 서비스 출금 서킷 브레이커 OPEN(승인 토큰 자동 발급 멈춤)
  ④ 끄면 대기 결제가 다시 처리된다(같은 추적 번호의 결제 요청이 결제사에 닿음)
정상 시험: 축소 운영이 아닐 때 결제 확인은 결제사 상태를 그대로 돌려준다.
"""
import time

import httpx
import pytest

from replay import _approval as ap
from replay import _incident as inc
from replay import _logs
from replay._http import admin, new_trace_id, portal, portal_login


@pytest.fixture(autouse=True)
def clean():
    inc.reset_mode()
    yield
    inc.reset_mode()


def test_slow_payment_does_not_stall_product_list():
    m = inc.slow_dependency()
    assert m["worst"] <= m["limit"] and 0 not in m["product_codes"], (
        f"기대: 결제사가 느려도 상품 목록은 {m['limit']}초 안. 실제 {m['product_seconds']}초 · 결제 확인 {m['status_counts']}")
    assert any(k.endswith(":PENDING_CHECK") for k in m["status_counts"]), f"기대: 자리가 없으면 기다리지 않고 「확인 중」. 실제 {m['status_counts']}"


def test_degraded_mode_needs_elevation():
    since = _logs.now()
    r = inc.set_mode("degraded")
    assert r.status_code == 403, f"기대: 권한 상승 없이 축소 운영 켜기 403. 실제 HTTP {r.status_code}"
    e = _logs.wait_event("mall-admin", since, "ops.denied.no_elevation", "13-6", timeout=8, target="path:/daon/mode/payment")
    assert e, "기대: ops.denied.no_elevation(target path:/daon/mode/payment). 축소 운영은 고위험 기능(묶음 incident-response)"


def test_degraded_mode_queues_orders_and_stops_money_out():
    order = inc.ensure_pending_order()
    inc.elevate("admin")
    since = _logs.now()
    r = inc.set_mode("degraded")
    assert r.status_code == 200, f"기대: 권한 상승 뒤 축소 운영 켜기 200. 실제 HTTP {r.status_code} {r.text[:120]}"
    e = _logs.wait_event("mall-admin", since, "ops.mode.degraded", "14-3", timeout=8)
    assert e and e.get("actor") == "admin:admin" and e.get("target") == "feature:payment", f"기대: ops.mode.degraded(actor admin:admin). 실제 {e}"
    # 주문은 받는다: 결제 시작 → 202 결제 대기, 결제사에는 아직 가지 않음
    trace = new_trace_id("s14-3-pending")
    with portal() as c:
        p = c.post("/pay/start", params={"orderId": order}, headers={"Authorization": portal_login(c, "alice"), "X-Trace-Id": trace})
    body = p.json() if p.headers.get("content-type", "").startswith("application/json") else {}
    assert p.status_code == 202 and (body.get("data") or {}).get("status") == "결제 대기", f"기대: 202 「결제 대기」. 실제 HTTP {p.status_code} {str(body)[:120]}"
    time.sleep(3)
    assert inc.pay_seen(trace) == 0, "기대: 축소 운영 동안 결제 요청은 대기 큐에 있고 결제사에는 가지 않음"
    # 돈이 나가는 쪽은 멈춘다: 환불 실행 503 + 안내 문구
    rid = ap.new_return()
    with admin() as c:
        x = c.post("/daon/refund/execute", headers={"Authorization": ap.must_login("staff_order")}, json={"returnId": rid, "token": None})
    assert x.status_code == 503 and "멈췄습니다" in x.text, f"기대: 환불 실행 503 + 안내 문구. 실제 HTTP {x.status_code}"
    # 승인 토큰 자동 발급 멈춤 = 출금 서킷 브레이커 OPEN(13-4를 사람이 연 상태)
    b = httpx.get(ap.APPROVAL + "/breaker", timeout=5).json()
    assert b.get("state") == "OPEN", f"기대: 출금 서킷 브레이커 OPEN(축소 운영). 실제 {b}"
    # 끄면 대기 결제가 다시 처리된다(천천히)
    assert inc.set_mode("normal").status_code == 200, "기대: 축소 운영 끄기 200"
    t0 = time.time()
    while inc.pay_seen(trace) == 0 and time.time() - t0 < 30:
        time.sleep(1)
    assert inc.pay_seen(trace) >= 1, "기대: 축소 운영을 끄면 대기 결제가 결제사에 닿음(같은 추적 번호, 30초 안)"


@pytest.mark.normal
def test_payment_status_is_answered_normally():
    with portal() as c:
        r = c.get("/pay/status", params={"orderId": inc.STATUS_ORDER}, headers={"Authorization": portal_login(c, "alice")})
    assert r.status_code == 200 and (r.json().get("data") or {}).get("status") in ("APPROVED", "NONE", "DECLINED"), (
        f"기대: 평소에는 결제사 상태를 그대로(APPROVED·NONE). 실제 HTTP {r.status_code} {r.text[:120]}")
