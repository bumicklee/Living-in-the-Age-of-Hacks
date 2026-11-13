"""5-2 토큰 서명을 금고로 옮기고 키를 바꿀 수 있게 · T13.

완료 지점: 토큰은 ES256(kid 포함)으로 서명되고, 공개키 목록(jwks)이 열려 있고, 키를 회전해도 로그인한 사람이 튕기지 않으며,
겹침이 끝나 옛 버전을 폐기하면 옛 토큰만 거절된다. 시작 지점(옛 HS512)에서는 ①②③④가 레드다.
키 회전·폐기 시험은 끝에 폐기 해제(min_decryption_version=1)로 되돌린다. 키 버전은 늘어난 채로 남는다(지울 수 없다).
앱이 키 목록을 다시 읽는 간격(10초)을 기다린다.
"""
import base64
import json
import subprocess
import sys
import time

import httpx
import pytest

from replay import _logs
from daon.config import URLS
from replay._http import ACCOUNTS, portal
from replay.t09_login_guard import clear_counters

PORTAL_URL = URLS["spring"]["portal"]
APP_REFRESH_WAIT = 11          # 앱이 공개키 목록을 다시 읽는 간격(10초) + 여유


def header_of(token: str) -> dict:
    h = token.split(".")[0]
    return json.loads(base64.urlsafe_b64decode(h + "=" * (-len(h) % 4)))


def member_login(c, user="alice") -> str:
    clear_counters()
    return c.post("/sso/login", data={"username": user, "password": ACCOUNTS[user]["password"]}).json()["data"]["token"]


def info_code(c, token: str) -> int:
    return c.get("/sso/info", headers={"Authorization": "Bearer " + token}).json().get("code")


def jwks_kids() -> list[str]:
    r = httpx.get(PORTAL_URL + "/.well-known/jwks.json", timeout=10)
    assert r.status_code == 200, f"기대: 공개키 목록이 로그인 없이 열려야 함. 실제 HTTP {r.status_code}"
    return [k["kid"] for k in r.json()["keys"]]


def rotate(action: str) -> None:
    r = subprocess.run([sys.executable, "-m", "daon", "rotate-key", "jwt", action], capture_output=True, text=True, timeout=90)
    assert r.returncode == 0, f"키 {action} 실패: {r.stderr.strip() or r.stdout.strip()}"


@pytest.mark.normal
def test_unit_tests_pass(stack):
    """Java 단위 시험 묶음이 정상 실행되는지 확인한다. 시작 지점의 시험 파일 부재를 취약 증거로 세지 않는다."""
    import os
    r = subprocess.run(["make", "unit-test"], capture_output=True, text=True, timeout=600,
                       cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    assert "BUILD SUCCESS" in r.stdout and "Failures: 0, Errors: 0" in r.stdout, f"기대: JwtAlgorithmPinTest 통과. 실제:\n{r.stdout[-600:]}"


@pytest.mark.normal
def test_login_and_member_api_still_work(stack):
    with portal() as c:
        t = member_login(c)
        assert info_code(c, t) == 200, "기대: 정상 로그인 뒤 회원 정보 200"


def test_token_is_signed_with_es256_and_kid(stack):
    with portal() as c:
        h = header_of(member_login(c))
    assert h.get("alg") == "ES256" and str(h.get("kid", "")).startswith("daon-jwt:v"), \
        f"기대: 토큰 머리글이 ES256 + kid(daon-jwt:v…). 실제 alg={h.get('alg')!r}, kid={h.get('kid')!r}"


def test_public_key_list_has_no_private_part(stack):
    r = httpx.get(PORTAL_URL + "/.well-known/jwks.json", timeout=10)
    assert r.status_code == 200, f"기대: jwks 200. 실제 {r.status_code}"
    for k in r.json()["keys"]:
        assert "d" not in k and set(k) <= {"kty", "crv", "alg", "use", "kid", "x", "y"}, f"기대: 공개 칸만. 실제 {sorted(k)}"


def test_rotate_keeps_users_logged_in_then_retire_drops_old_tokens(stack):
    since = _logs.now()
    with portal() as c:
        old = member_login(c)
        before = set(jwks_kids())
        try:
            rotate("rotate")
            time.sleep(APP_REFRESH_WAIT)
            kids = set(jwks_kids())
            assert len(kids) == len(before) + 1, f"기대: 회전 뒤 공개키가 하나 늘어남(겹침 기간). 전 {len(before)} → 후 {len(kids)}"
            assert info_code(c, old) == 200, "기대: 겹침 기간에는 옛 토큰이 계속 통함(로그인한 사람이 튕기면 안 됨)"
            new = member_login(c)
            assert header_of(new)["kid"] != header_of(old)["kid"], "기대: 새 토큰은 새 키 버전으로 서명"
            assert info_code(c, new) == 200, "기대: 새 토큰 200"
            rotate("retire")
            time.sleep(APP_REFRESH_WAIT)
            assert info_code(c, old) == 401, "기대: 옛 키를 폐기하면 옛 토큰은 401"
            assert info_code(c, new) == 200, "기대: 새 토큰은 계속 200"
        finally:
            rotate("reset")
            time.sleep(APP_REFRESH_WAIT)
    e = _logs.wait_event("mall-portal", since, "auth.token.denied_retired_key", "5-2", stack)
    assert e, "기대: SECURITY auth.token.denied_retired_key(5-2)"
    assert old not in str(e), "기대: 이벤트에 토큰 원문이 없어야 함"
