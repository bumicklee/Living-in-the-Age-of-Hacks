"""4-2 비밀번호 저장과 유출 비밀번호 거부 · T09(규칙 부분)."""
import pytest

from replay import _logs
from replay._http import portal, portal_login

# 랩 유출 목록(seed/password/breached-top.txt)의 앞 줄. 가짜 목록이다
BREACHED = "password123"


def register(c, password: str):
    return c.post("/sso/register", data={"username": "lab-policy-check", "password": password,
                                         "telephone": "01000007001", "authCode": "000000"}).json()


def test_breached_password_is_rejected(stack):
    with portal() as c:
        r = register(c, BREACHED)
    assert "유출" in (r.get("message") or ""), \
        f"기대: 유출 목록에 있는 비밀번호는 인증 번호를 보기 전에 거부돼야 합니다. 실제: {r.get('message')!r}"


def test_short_password_is_rejected(stack):
    with portal() as c:
        r = register(c, "abc12")
    assert "8자" in (r.get("message") or ""), f"기대: 8자 미만 비밀번호 거부. 실제: {r.get('message')!r}"


def test_rejection_is_logged_without_password(stack):
    since = _logs.now()
    with portal() as c:
        register(c, BREACHED)
    e = _logs.wait_event("mall-portal", since, "auth.password.rejected_breached", "4-2", stack)
    assert e, "기대: SECURITY auth.password.rejected_breached(4-2)가 남아야 합니다"
    assert BREACHED not in str(e), f"기대: 이벤트에 비밀번호가 없어야 합니다. 실제: {e}"


@pytest.mark.normal
def test_alice_logs_in(stack):
    from replay.t09_login_guard import clear_counters
    clear_counters()
    with portal() as c:
        assert portal_login(c, "alice"), "기대: alice 정상 로그인(목록에 없는 비밀번호)"
