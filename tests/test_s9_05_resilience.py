"""9-5 재시도 폭주 막기: 재시도 한 곳·백오프·지터·재시도 예산·서킷 브레이커·DLQ(A6). 재연 T24.

측정은 모듈마다 한 번(약 75초): 결제사 스텁 3초 지연, 결제 요청 10건(1초 간격), 60초 동안 스텁이 받은 요청 수.
시작 지점: A6 없음(재시도 큐·DLQ 없음), 증폭 약 9배, 서킷·DLQ 이벤트 없음 → 레드.
완료 지점: daon.pay.retry·daon.pay.dlq가 있다, 증폭 1.2배 이하, resilience.circuit.opened와 queue.dead_lettered(측정 요청의 trace_id),
          DLQ에 메시지가 쌓인다.
정상 시험: 결제사가 실패하는 동안에도 결제 요청은 202 「결제 대기」로 받는다, 스텁을 되돌리면 새 결제가 성공한다.
"""
import time

import pytest

from daon import stack as st
from replay import _logs
from replay._faults import measure_retry, pay_stats, rabbit_queues, retry_table, set_fault
from replay._http import new_trace_id, portal, portal_login


@pytest.fixture(scope="module")
def measured():
    since = _logs.now()
    try:
        dlq_before = rabbit_queues().get("daon.pay.dlq", 0)
    except Exception:  # noqa: BLE001 - 시작 지점에는 큐가 없다
        dlq_before = 0
    m = measure_retry("full")
    m["since"] = since
    m["dlq_before"] = dlq_before
    return m


def test_a06_retry_queue_and_dlq_exist():
    assert "a06" in st.arch_layers(), "기대: infra/arch.txt에 a06(A6: 재시도 큐·DLQ)"
    q = rabbit_queues()
    for name in ("daon.pay.request", "daon.pay.retry", "daon.pay.dlq"):
        assert name in q, f"기대: RabbitMQ /mall에 {name}. 있는 큐 {sorted(q)}"


def test_amplification_within_limit(measured):
    assert measured["ratio"] <= measured["ok_max"], "기대: 결제사가 느려도 증폭 1.2배 이하\n" + "\n".join(retry_table(measured))


@pytest.mark.normal
def test_orders_are_accepted_while_payment_fails(measured):
    assert measured["accepted"] == measured["orders"], f"기대: 결제사가 실패하는 동안에도 결제 요청은 202 「결제 대기」. 실제 {measured['statuses']}"


def test_circuit_opened_and_dead_letter_logged(measured):
    e = _logs.wait_event("mall-portal", measured["since"], "resilience.circuit.opened", "9-5", timeout=5)
    assert e and e.get("target") == "dependency:pay-stub", f"기대: resilience.circuit.opened(dependency:pay-stub). 실제 {e}"
    dl = [x for x in _logs.security_events("mall-portal", measured["since"])
          if x.get("event") == "queue.dead_lettered" and x.get("trace_id") in measured["traces"]]
    assert dl, "기대: 측정 요청의 trace_id가 달린 queue.dead_lettered(메시지 헤더의 X-Trace-Id가 따라감)"
    q = rabbit_queues()
    assert q.get("daon.pay.dlq", 0) > measured["dlq_before"], f"기대: daon.pay.dlq에 끝내 실패한 결제 요청이 쌓임. 전 {measured['dlq_before']} 뒤 {q.get('daon.pay.dlq')}"


@pytest.mark.normal
def test_payment_succeeds_after_stub_recovers(measured):
    set_fault("pay", "ok")
    with portal() as c:
        tok = portal_login(c, "alice")
        t0 = time.time()
        while time.time() - t0 < 60:
            tid = new_trace_id("s95ok")
            r = c.post("/daon/order/9001/pay", params={"ref": f"ok{int(time.time())}"}, headers={"Authorization": tok, "X-Trace-Id": tid})
            assert r.status_code == 202, f"기대: 결제 요청 202. 실제 {r.status_code}"
            for _ in range(8):
                if any(i.get("trace_id") == tid and i.get("status") == 201 for i in pay_stats()["items"]):
                    return
                time.sleep(1)
    pytest.fail("기대: 스텁을 되돌린 뒤 60초 안에 새 결제가 성공(서킷이 반열림 → 닫힘)")
