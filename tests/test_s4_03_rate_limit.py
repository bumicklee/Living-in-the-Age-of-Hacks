"""4-3 크리덴셜 스터핑 탐지와 속도 제한 · T09 · A3(Redis 접근 제어)."""
import subprocess

import pytest

from replay import _logs
from replay import t09_login_guard as t09
from replay._http import admin, portal, portal_login


def test_wrong_passwords_are_limited(stack):
    r = t09.run(stack)
    assert not r.vulnerable, "기대: 같은 계정에 틀린 비밀번호가 이어지면 HTTP 429로 막고, alice는 그대로 로그인돼야 합니다\n" + r.show()


def test_limited_response_has_retry_after_and_one_message(stack):
    try:
        with portal() as c:
            for i in range(t09.TRIES):
                r = c.post("/sso/login", data={"username": "bob", "password": f"lab-wrong-{i}"})
            ghost = None
            for i in range(t09.TRIES):
                ghost = c.post("/sso/login", data={"username": "lab-no-such-user", "password": f"lab-wrong-{i}"})
    finally:
        t09.clear_counters()
    assert r.status_code == 429 and r.headers.get("Retry-After"), f"기대: 429와 Retry-After. 실제: {r.status_code} {dict(r.headers)}"
    assert ghost is not None and ghost.status_code == 429, "기대: 없는 계정도 같은 규칙으로 막아야 합니다"
    assert r.json().get("message") == ghost.json().get("message"), \
        "기대: 있는 계정과 없는 계정의 429 문구가 같아야 합니다(계정 존재를 드러내지 않음)"


def test_rate_limited_event_is_logged(stack):
    since = _logs.now()
    try:
        with portal() as c:
            for i in range(t09.TRIES):
                c.post("/sso/login", data={"username": "bob", "password": f"lab-wrong-{i}"})
    finally:
        t09.clear_counters()
    e = _logs.wait_event("mall-portal", since, "auth.login.rate_limited", "4-3", stack)
    assert e, "기대: 막을 때 mall-portal 로그에 SECURITY auth.login.rate_limited(4-3)가 남아야 합니다"
    assert "lab-wrong" not in str(e), f"기대: 이벤트에 비밀번호가 없어야 합니다. 실제: {e}"


def test_admin_login_is_limited_too(stack):
    try:
        with admin() as c:
            codes = [c.post("/admin/login", json={"username": "staff_goods", "password": f"lab-wrong-{i}"}).status_code
                     for i in range(t09.TRIES)]
    finally:
        t09.clear_counters()
    assert 429 in codes, f"기대: 관리자 로그인에도 같은 제한이 걸려야 합니다. 응답 코드: {codes}"


def test_redis_default_user_is_refused(stack):
    out = subprocess.run(["docker", "exec", "daon-redis-1", "redis-cli", "PING"], capture_output=True, text=True)
    assert "NOAUTH" in out.stdout + out.stderr, \
        f"기대: A3에서는 비밀번호 없이 Redis에 붙으면 거부돼야 합니다. 실제: {(out.stdout + out.stderr).strip()!r}"


@pytest.mark.normal
def test_alice_logs_in(stack):
    with portal() as c:
        assert portal_login(c, "alice"), "기대: alice 정상 로그인"
