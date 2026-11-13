"""13-4 두 사람 승인·별도 경로 확인·지연 실행 · T34(두 번째 재연) · A10.

시작 지점(13-3 완료): 정책(남은 금액 이하)만 맞으면 고액도 요청자 혼자 바로 승인·실행 → 레드.
완료 지점(가짜 기준: 고액 300,000원 이상, 지연 10초, 시간당 6건·1,000,000원):
  ① 고액은 PENDING_SECOND. 확인 번호는 메일(Mailpit)로만, 응답에는 없다
  ② 요청자가 스스로 두 번째 승인 → 403 + approval.denied.self_approval
  ③ 틀린 확인 번호 → 403 + approval.denied.bad_confirm
  ④ 재무 담당(staff_finance)이 메일 번호로 승인 → SCHEDULED, 지연 전 환불 0건, 지연 뒤 실행기가 1건(토큰 속 금액)
  ⑤ 창 안에서 취소 → CANCELLED + approval.cancelled.in_window(INFO), 지연이 지나도 환불 0건
  ⑥ 한 시간 안에 6건을 넘기면 payout.breaker.open, 이후 HELD. 다시 닫기는 서로 다른 두 사람
정상 시험: 고액 기준 미만(10,000원)은 13-3처럼 바로 실행.
"""
import re
import time

import pytest

from replay import _approval as ap
from replay import _logs

HIGH = 400_000


@pytest.fixture(autouse=True)
def clean():
    ap.require_a10()
    ap.reset()
    ap.ensure_high_order()
    yield
    ap.reset()


def _pending(via_admin_app: bool = False) -> tuple[int, str]:
    rid = ap.new_return(ap.HIGH_ORDER)
    if via_admin_app:
        ap.confirm_return("staff_order", rid, HIGH, handle_man="staff_order")
        a = ap.latest_approval(rid)
        status, aid = (a or {}).get("status"), (a or {}).get("id")
    else:
        body = ap.request_refund("staff_order", rid, HIGH)
        status, aid = body.get("status"), body.get("id")
    assert status == "PENDING_SECOND", f"기대: 고액({HIGH:,}원)은 두 번째 승인 대기. 실제 {status}"
    return rid, aid


def test_high_value_needs_second_person_and_code_is_not_in_response():
    rid = ap.new_return(ap.HIGH_ORDER)
    body = ap.request_refund("staff_order", rid, HIGH)
    assert body.get("status") == "PENDING_SECOND", f"기대: PENDING_SECOND. 실제 {body.get('status')}"
    assert "token" not in body and not re.search(r"\b[0-9]{6}\b", str(body)), f"기대: 응답에 토큰·확인 번호가 없음. 실제 {sorted(body)}"
    assert ap.confirm_code(body["id"]), "기대: 확인 번호 메일이 Mailpit(재무 담당 받은 편지함)에 옴"
    time.sleep(2)
    assert not ap.refunds(rid), "기대: 두 번째 승인 전 환불 0건"


def test_requester_cannot_be_second_approver():
    rid, aid = _pending()
    code = ap.confirm_code(aid)
    since = _logs.now()
    r = ap.ap("POST", f"/approvals/{aid}/approve", "staff_order", json={"code": code})
    assert r.status_code == 403, f"기대: 요청자 스스로 두 번째 승인 403. 실제 HTTP {r.status_code}"
    e = _logs.wait_event("approval", since, "approval.denied.self_approval", "13-4", timeout=8)
    assert e and e.get("actor") == "admin:staff_order" and e.get("target") == f"approval:{aid}", f"기대: approval.denied.self_approval. 실제 {e}"


def test_wrong_code_is_denied():
    rid, aid = _pending()
    real = ap.confirm_code(aid)
    wrong = "111111" if real != "111111" else "222222"
    since = _logs.now()
    r = ap.ap("POST", f"/approvals/{aid}/approve", "staff_finance", json={"code": wrong})
    assert r.status_code == 403, f"기대: 틀린 확인 번호 403. 실제 HTTP {r.status_code}"
    assert _logs.wait_event("approval", since, "approval.denied.bad_confirm", "13-4", timeout=8), "기대: approval.denied.bad_confirm"


def test_finance_approval_waits_then_executes():
    rid, aid = _pending(via_admin_app=True)
    r = ap.ap("POST", f"/approvals/{aid}/approve", "staff_finance", json={"code": ap.confirm_code(aid)})
    assert r.status_code == 200 and r.json().get("status") == "SCHEDULED", f"기대: 재무 담당 + 메일 번호 → SCHEDULED. 실제 HTTP {r.status_code} {r.text[:80]}"
    time.sleep(3)
    assert not ap.refunds(rid), "기대: 지연 실행 창(10초) 안에서는 환불 0건"
    rows = ap.wait_refunds(rid, 1, timeout=30)
    assert len(rows) == 1 and int(rows[0]["amount"]) == HIGH, f"기대: 창이 끝나면 실행기가 {HIGH:,}원 1건. 실제 {[(x['amount']) for x in rows]}"
    assert ap.return_row(rid)["return_amount"].startswith(str(HIGH)), "기대: 반품 신청의 환불 금액 = 토큰 속 금액"


def test_cancel_in_window_stops_execution():
    rid, aid = _pending()
    ap.ap("POST", f"/approvals/{aid}/approve", "staff_finance", json={"code": ap.confirm_code(aid)})
    since = _logs.now()
    r = ap.ap("POST", f"/approvals/{aid}/cancel", "staff_order")
    assert r.status_code == 200 and r.json().get("status") == "CANCELLED", f"기대: 창 안에서 요청자가 취소. 실제 HTTP {r.status_code}"
    assert _logs.wait_event("approval", since, "approval.cancelled.in_window", "13-4", timeout=8), "기대: approval.cancelled.in_window(INFO)"
    time.sleep(16)
    assert not ap.refunds(rid), "기대: 취소한 환불은 지연이 지나도 0건"


def test_breaker_opens_and_needs_two_people_to_close():
    since = _logs.now()
    got = []
    for order in (9001, 9001, 9001, 9002, 9002, 9003, 9003):
        rid = ap.new_return(order)
        got.append(ap.request_refund("staff_order", rid, 1000).get("status"))
    assert got[:6] == ["APPROVED"] * 6 and got[6] == "HELD_BY_BREAKER", f"기대: 6건 승인 뒤 7번째는 브레이커가 멈춤. 실제 {got}"
    assert _logs.wait_event("approval", since, "payout.breaker.open", "13-4", timeout=8), "기대: payout.breaker.open(즉시 경보)"
    one = ap.ap("POST", "/breaker/close", "staff_finance").json().get("state")
    assert one.startswith("OPEN"), f"기대: 한 사람만으로는 닫히지 않음. 실제 {one}"
    two = ap.ap("POST", "/breaker/close", "staff_lead").json().get("state")
    assert two == "CLOSED", f"기대: 서로 다른 두 번째 사람이 닫음. 실제 {two}"


@pytest.mark.normal
def test_below_threshold_refund_executes_immediately():
    rid = ap.new_return(ap.ORDER)
    ap.confirm_return("staff_order", rid, 10_000, handle_man="staff_order")
    rows = ap.wait_refunds(rid, 1, timeout=10)
    assert len(rows) == 1, f"기대: 고액 기준 미만은 바로 1건(승인 피로를 만들지 않음). 실제 {len(rows)}건"
