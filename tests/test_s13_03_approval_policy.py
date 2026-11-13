"""13-3 승인 정책 엔진: 서명하는 쪽이 내용을 읽는다 · T34 · F48 · A10.

시작 지점: 관리자 앱이 반품 확인 요청의 returnAmount·handleMan을 그대로 저장하고, 확인하자마자 가짜 결제사에 환불(DaonRefundExecutor) → 레드.
완료 지점(A10 승인 서비스 + 서명된 승인 토큰):
  ① 남은 결제 금액을 넘는 환불은 승인 서비스가 거부(approval.denied.over_remaining, 관리자 앱이 받은 trace_id 그대로)·환불 0건
  ② 처리자는 로그인한 관리자(요청 본문의 handleMan을 쓰지 않음)
  ③ 같은 신청을 두 번 확인해도 환불은 한 번(조건부 상태 전이)
  ④ 실행기: 토큰 없이·다른 키로 서명한 토큰·금액을 바꾼 토큰·이미 쓴 토큰 → 모두 403 + refund.denied.no_approval, 환불 0건
  ⑤ 서명 분리: 관리자 앱의 금고 자격증명으로는 승인 키 서명이 403(확인만 됨), 관리자 앱에는 승인 서비스 접속 정보가 없다
정상 시험: 남은 금액 안의 10,000원 환불은 바로 1건 실행된다(시작 지점에서도 그린).
시험이 만든 반품 신청·승인 기록은 매 시험 앞에서 지운다(make approval-reset과 같은 일).
"""
import time

import httpx
import pytest

from daon import stack as st
from daon.config import HOST, port
from replay import _approval as ap
from replay import _logs
from replay._http import new_trace_id
from replay._money import mysql


@pytest.fixture(autouse=True)
def clean():
    ap.reset()
    yield


def _confirmed(rid: int) -> None:
    """실행기 시험용: 반품을 확인(1) 상태로(관리자 앱을 거치지 않는 랩 도구)"""
    mysql(f"UPDATE oms_order_return_apply SET status = 1 WHERE id = {int(rid)}")


def _token(rid: int, amount: int = 1000) -> str:
    ap.require_a10()
    body = ap.request_refund("staff_order", rid, amount)
    assert body.get("status") == "APPROVED" and body.get("token"), f"기대: 승인 서비스가 정책에 맞는 {amount:,}원을 승인하고 토큰을 줌. 실제 {body.get('status')}"
    return body["token"]


def test_over_remaining_refund_is_denied_by_approval_service():
    rid = ap.new_return(ap.ORDER)
    trace = new_trace_id("s13-03")
    since = _logs.now()
    ap.confirm_return("staff_order", rid, 3_000_000, handle_man="admin", trace=trace)
    time.sleep(2)
    rows = ap.refunds(rid)
    assert not rows, f"기대: 결제 39,000원 주문에 3,000,000원 환불은 0건. 실제 {len(rows)}건 {sum(int(r['amount']) for r in rows):,}원"
    e = _logs.wait_event("approval", since, "approval.denied.over_remaining", "13-3", timeout=8, trace_id=trace)
    assert e and e.get("target") == f"return:{rid}" and e.get("actor") == "admin:staff_order", \
        f"기대: 승인 서비스 SECURITY 줄 approval.denied.over_remaining(target return:{rid}, 관리자 앱이 받은 trace_id 그대로). 실제 {e}"
    assert "3000000" not in str(e) and "3,000,000" not in str(e), "기대: 금액은 이벤트에 넣지 않음"
    assert ap.return_row(rid)["status"] == 0, "기대: 거부되면 반품 확인을 되돌림(처리 대기 0)"


def test_handle_man_comes_from_login():
    rid = ap.new_return(ap.ORDER)
    ap.confirm_return("staff_order", rid, 10_000, handle_man="admin")
    row = ap.return_row(rid)
    assert row["handle_man"] == "staff_order", f"기대: 처리자 = 로그인한 staff_order(요청 본문 admin 무시). 실제 {row['handle_man']!r}"


def test_double_confirm_refunds_once():
    rid = ap.new_return(ap.ORDER)
    ap.confirm_return("staff_order", rid, 10_000, handle_man="staff_order")
    ap.confirm_return("staff_order", rid, 10_000, handle_man="staff_order")
    time.sleep(2)
    rows = ap.refunds(rid)
    assert len(rows) == 1, f"기대: 같은 신청을 두 번 확인해도 환불 1건(대기 → 확인 조건부 UPDATE). 실제 {len(rows)}건"


def test_execute_without_token_is_denied():
    rid = ap.new_return(ap.ORDER)
    _confirmed(rid)
    since = _logs.now()
    r = ap.execute("staff_order", rid, None)
    assert r.status_code == 403, f"기대: 승인 토큰 없이 실행 403. 실제 HTTP {r.status_code}"
    assert not ap.refunds(rid), "기대: 가짜 결제사 환불 0건"
    e = _logs.wait_event("mall-admin", since, "refund.denied.no_approval", "13-3", timeout=8)
    assert e and e.get("target") == f"return:{rid}", f"기대: refund.denied.no_approval(target return:{rid}). 실제 {e}"


def test_forged_token_is_denied():
    rid = ap.new_return(ap.ORDER)
    tok = _token(rid)
    _confirmed(rid)
    r = ap.execute("staff_order", rid, ap.forged(tok))
    assert r.status_code == 403, f"기대: 승인 서비스 키가 아닌 키로 서명한 토큰 403. 실제 HTTP {r.status_code}"
    assert not ap.refunds(rid), "기대: 가짜 결제사 환불 0건"


def test_amount_changed_token_is_denied():
    rid = ap.new_return(ap.ORDER)
    tok = _token(rid, 1000)
    _confirmed(rid)
    r = ap.execute("staff_order", rid, ap.tamper_amount(tok, 30_000))
    assert r.status_code == 403, f"기대: 금액 칸만 바꾼 토큰(서명 불일치) 403. 실제 HTTP {r.status_code}"
    assert not ap.refunds(rid), "기대: 가짜 결제사 환불 0건"


def test_reused_token_is_denied():
    rid = ap.new_return(ap.ORDER)
    tok = _token(rid, 1000)
    _confirmed(rid)
    first = ap.execute("staff_order", rid, tok)
    assert first.status_code == 200, f"기대: 처음 쓰는 진짜 토큰은 실행(200). 실제 HTTP {first.status_code}"
    again = ap.execute("staff_order", rid, tok)
    assert again.status_code == 403, f"기대: 같은 토큰 두 번째 403(1회용 번호). 실제 HTTP {again.status_code}"
    rows = ap.refunds(rid)
    assert len(rows) == 1 and int(rows[0]["amount"]) == 1000, f"기대: 환불 1건 1,000원(토큰 속 금액). 실제 {[(r['amount']) for r in rows]}"


def test_admin_app_can_verify_but_not_sign():
    ap.require_a10()
    has = st.compose("spring", "exec", "-T", "mall-admin", "sh", "-c", "test -e /run/approval && echo yes || echo no", check=False, capture=True)
    assert (has.stdout or "").strip().endswith("no"), "기대: 관리자 앱에는 승인 서비스 금고 접속 정보(/run/approval)가 붙지 않음"
    ids = st.compose("spring", "exec", "-T", "mall-admin", "sh", "-c", "cat /run/openbao/admin-role-id; echo; cat /run/openbao/admin-secret-id",
                     check=False, capture=True)
    role_id, secret_id = [l.strip() for l in (ids.stdout or "").splitlines() if l.strip()][:2]
    bao = f"http://{HOST}:{port('DAON_OPENBAO_PORT', 8200)}"
    login = httpx.post(f"{bao}/v1/auth/approle/login", json={"role_id": role_id, "secret_id": secret_id}, timeout=10).json()
    h = {"X-Vault-Token": login["auth"]["client_token"]}
    sign = httpx.post(f"{bao}/v1/transit/sign/daon-approval", headers=h, json={"input": "dGVzdA==", "marshaling_algorithm": "jws"}, timeout=10)
    assert sign.status_code == 403, f"기대: 관리자 앱 자격증명으로 승인 키 서명 403(서명은 승인 서비스만). 실제 HTTP {sign.status_code}"
    verify = httpx.post(f"{bao}/v1/transit/verify/daon-approval", headers=h,
                        json={"input": "dGVzdA==", "signature": "vault:v1:AAAA", "marshaling_algorithm": "jws"}, timeout=10)
    assert verify.status_code != 403, f"기대: 확인(verify)은 허용(형식 오류로 400이어도 403은 아님). 실제 HTTP {verify.status_code}"


@pytest.mark.normal
def test_small_refund_within_paid_amount_executes():
    rid = ap.new_return(ap.ORDER)
    ap.confirm_return("staff_order", rid, 10_000, handle_man="staff_order")
    rows = ap.wait_refunds(rid, 1, timeout=10)
    assert len(rows) == 1 and int(rows[0]["amount"]) == 10_000, f"기대: 남은 금액 안의 10,000원 환불 1건. 실제 {[(r['amount']) for r in rows]}"
