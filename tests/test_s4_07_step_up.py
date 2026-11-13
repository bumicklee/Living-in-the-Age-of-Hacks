"""4-7 MFA 피로·SMS 단일 인증과 KT 소액결제 · T12 (+ F11 관리자 비밀번호 변경)."""
import httpx
import pytest

from replay import _logs
from replay import t12_high_risk_change as t12
from replay._http import ACCOUNTS, ADMINS, admin, portal
from replay.t09_login_guard import clear_counters

AUTHAPP = "http://127.0.0.1:9104"


def alice(c) -> dict:
    clear_counters()
    from daon import stack
    stack.redis_cli("DEL", f"rl:push:{ACCOUNTS['alice']['member_id']}")
    t = c.post("/sso/login", data={"username": "alice", "password": ACCOUNTS["alice"]["password"]}).json()["data"]["token"]
    return {"Authorization": "Bearer " + t}


def latest_number() -> str:
    rows = httpx.get(f"{AUTHAPP}/v1/push", params={"to": "alice"}).json()
    return rows[-1]["number"]


def test_old_token_cannot_change_refund_account(stack):
    r = t12.run(stack)
    assert not r.vulnerable, "기대: 로그인한 지 10분 지난 토큰으로 환불 계좌를 바꾸면 401 step_up_required\n" + r.show()


def test_step_up_then_change_succeeds_and_is_logged(stack):
    since = _logs.now()
    with portal() as c:
        h = alice(c)
        try:
            t12.lab_clock(10)
            assert c.post("/member/refund-account", headers=h, data={"bank": "랩은행", "account": "000-1111-2222-33"}).status_code == 401
            assert c.post("/sso/step-up/start", headers=h).json().get("data") is None, "기대: 번호를 응답에 싣지 않음"
            ok = c.post("/sso/step-up/confirm", headers=h,
                        data={"password": ACCOUNTS["alice"]["password"], "number": latest_number()}).json()
            assert ok.get("code") == 200, f"기대: 비밀번호 + 인증 앱 번호로 재인증 성공. 실제: {ok}"
            h2 = {"Authorization": "Bearer " + ok["data"]["token"]}
            done = c.post("/member/refund-account", headers=h2, data={"bank": "랩은행", "account": "000-1111-2222-33"}).json()
        finally:
            t12.lab_clock(None)
            from daon import stack as lab
            lab.redis_cli("DEL", f"mall:refund:{ACCOUNTS['alice']['member_id']}")
    assert done.get("code") == 200, f"기대: 재인증 뒤 변경 성공. 실제: {done}"
    e = _logs.wait_event("mall-portal", since, "account.change.high_risk", "4-7", stack)
    assert e and "000-1111" not in str(e), f"기대: SECURITY account.change.high_risk, 계좌 값 없음. 실제: {e}"


def test_wrong_step_up_is_logged(stack):
    since = _logs.now()
    with portal() as c:
        h = alice(c)
        c.post("/sso/step-up/start", headers=h)
        bad = c.post("/sso/step-up/confirm", headers=h, data={"password": "lab-wrong", "number": latest_number()}).json()
    clear_counters()
    assert bad.get("code") != 200, "기대: 비밀번호가 틀리면 재인증 실패"
    assert _logs.wait_event("mall-portal", since, "auth.stepup.failed", "4-7", stack), "기대: SECURITY auth.stepup.failed(4-7)"


def test_push_flood_is_limited(stack):
    since = _logs.now()
    with portal() as c:
        h = alice(c)
        codes = [c.post("/sso/step-up/start", headers=h).json().get("code") for _ in range(4)]
    from daon import stack as lab
    lab.redis_cli("DEL", f"rl:push:{ACCOUNTS['alice']['member_id']}")
    assert codes[:3] == [200, 200, 200] and codes[3] != 200, f"기대: 10분에 3번까지만. 실제: {codes}"
    assert _logs.wait_event("mall-portal", since, "auth.mfa.push_flood", "4-7", stack), "기대: SECURITY auth.mfa.push_flood(4-7)"


def test_admin_update_cannot_change_password(stack):
    target, new_pw = "staff_order", "Lab-Changed-Pass-9!"
    with admin() as c:
        tok = c.post("/admin/login", json={"username": "admin", "password": ADMINS["admin"]["password"]}).json()["data"]["token"]
        h = {"Authorization": "Bearer " + tok}
        row = next(a for a in c.get("/admin/list", headers=h, params={"keyword": target, "pageSize": 5}).json()["data"]["list"]
                   if a["username"] == target)
        changed = False
        try:
            c.post(f"/admin/update/{row['id']}", headers=h, json={**row, "password": new_pw})
            clear_counters()
            changed = c.post("/admin/login", json={"username": target, "password": new_pw}).json().get("code") == 200
        finally:
            if changed:
                c.post(f"/admin/update/{row['id']}", headers=h, json={**row, "password": ADMINS[target]["password"]})
            clear_counters()
    assert not changed, "기대: 관리자 정보 수정으로는 다른 관리자의 비밀번호가 바뀌지 않아야 합니다(현재 비밀번호를 요구하는 변경으로만)"


@pytest.mark.normal
def test_alice_reads_refund_account(stack):
    with portal() as c:
        assert c.get("/member/refund-account", headers=alice(c)).json().get("code") == 200
