"""4-4 응답 시간으로 회원 여부가 샌다 · T10."""
import pytest

from replay import _logs
from replay import t10_login_timing as t10
from replay.t09_login_guard import clear_counters
from replay._http import admin, portal, portal_login


def test_unknown_and_known_accounts_look_the_same(stack):
    r = t10.run(stack)
    assert not r.vulnerable, "기대: 없는 아이디와 있는 아이디의 응답 시간 중앙값 차가 30ms 미만이고, 문구가 같아야 합니다\n" + r.show()


def test_admin_messages_are_one(stack):
    try:
        with admin() as c:
            a = c.post("/admin/login", json={"username": "lab-nouser", "password": "lab-wrong"}).json()
            b = c.post("/admin/login", json={"username": "staff_goods", "password": "lab-wrong"}).json()
    finally:
        clear_counters()
    assert a.get("message") == b.get("message"), f"기대: 관리자 로그인 문구가 하나. 실제: {a.get('message')!r} / {b.get('message')!r}"


def test_reset_path_does_not_reveal_phone(stack):
    with portal() as c:
        r = c.post("/sso/updatePassword", data={"telephone": "01000009999", "password": "Lab-New-Pass-77!", "authCode": "000000"})
    msg = r.json().get("message") or ""
    assert "존재하지 않는" not in msg, f"기대: 없는 전화번호도 인증 번호 오류와 같은 문구. 실제: {msg!r}"


def test_unknown_account_burst_is_logged(stack):
    clear_counters()
    since = _logs.now()
    try:
        with portal() as c:
            for i in range(12):               # 아이디마다 달라 계정 축에 안 걸리고, IP 축(50회)보다 적다
                c.post("/sso/login", data={"username": f"lab-scan-{i}", "password": "lab-wrong"})
    finally:
        clear_counters()
    e = _logs.wait_event("mall-portal", since, "auth.login.unknown_account_burst", "4-4", stack)
    assert e, "기대: 없는 아이디로 10번째 시도가 오면 SECURITY auth.login.unknown_account_burst(4-4)가 남아야 합니다"
    assert "lab-scan" not in str(e), f"기대: 이벤트에 시도한 아이디를 적지 않아야 합니다. 실제: {e}"


@pytest.mark.normal
def test_alice_logs_in(stack):
    clear_counters()
    with portal() as c:
        assert portal_login(c, "alice"), "기대: alice 정상 로그인"
