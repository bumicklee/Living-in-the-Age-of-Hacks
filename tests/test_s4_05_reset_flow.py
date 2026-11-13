"""4-5 비밀번호 재설정 링크 주소는 누가 정하나 · T11 (+ 인증 번호 확장).

비밀번호를 바꾸는 시험은 가짜 계정 bob을 같은 가짜 비밀번호(daon-pass-1!)로 다시 정한다(다른 시험에 영향 없음).
"""
import re
import time
from urllib.parse import parse_qs, urlparse

import httpx
import pytest

from replay import _logs
from replay import t11_reset_flow as t11
from replay._http import ACCOUNTS, portal, portal_login
from replay.t09_login_guard import clear_counters

PASS = ACCOUNTS["bob"]["password"]

BOB_PHONE = "010-0000-0102"
SMS = "http://127.0.0.1:9102"


def bob_link_token() -> str:
    started = time.time()
    with portal() as c:
        c.post("/sso/reset/link", data={"username": "bob"})
    link = t11.latest_link("bob@daon.example", started)
    assert link, "기대: bob 재설정 메일이 가짜 메일함에 와야 합니다"
    return parse_qs(urlparse(link).query)["token"][0]


def bob_sms_code() -> str:
    clear_counters()             # 10-7 뒤에는 같은 번호 60초 1번 예산이 있다(rl:sms:budget:*). 시험끼리 예산을 물려받지 않게
    with portal() as c:
        c.get("/sso/getAuthCode", params={"telephone": BOB_PHONE})
    rows = httpx.get(f"{SMS}/v1/messages", params={"to": BOB_PHONE}).json()
    return re.search(r"\d{6}", rows[-1]["text"]).group(0)


def test_link_host_fixed_and_code_not_in_response(stack):
    r = t11.run(stack)
    assert not r.vulnerable, "기대: 메일 링크는 설정된 공개 주소, 인증 번호는 문자로만\n" + r.show()


def test_reset_token_is_single_use(stack):
    since = _logs.now()
    token = bob_link_token()
    with portal() as c:
        first = c.post("/sso/reset/confirm", data={"token": token, "password": PASS}).json()
        second = c.post("/sso/reset/confirm", data={"token": token, "password": PASS}).json()
    assert first.get("code") == 200, f"기대: 처음 쓰면 성공. 실제: {first}"
    assert second.get("code") != 200, f"기대: 같은 토큰을 다시 쓰면 실패. 실제: {second}"
    assert _logs.wait_event("mall-portal", since, "auth.reset.token_reused", "4-5", stack), \
        "기대: SECURITY auth.reset.token_reused(4-5)"


def test_reset_token_ttl_is_15_minutes(stack):
    from daon import stack as lab

    def tokens() -> set[str]:
        return {k for k in lab.redis_cli("--scan", "--pattern", "pwreset:*").stdout.split()
                if not k.startswith(("pwreset:used:", "pwreset:sms:"))}
    before = tokens()
    bob_link_token()
    keys = tokens() - before                       # 방금 만든 토큰만(앞 시험·스위치를 끈 재연이 남긴 키는 빼고)
    ttls = [int(lab.redis_cli("TTL", k).stdout.strip()) for k in keys]
    assert ttls and max(ttls) <= 900, f"기대: 재설정 토큰 만료 15분(900초) 이하. 실제 TTL: {ttls}"


def test_auth_code_is_single_use(stack):
    code = bob_sms_code()
    with portal() as c:
        first = c.post("/sso/updatePassword", data={"telephone": BOB_PHONE, "password": PASS, "authCode": code}).json()
        second = c.post("/sso/updatePassword", data={"telephone": BOB_PHONE, "password": PASS, "authCode": code}).json()
    assert first.get("code") == 200, f"기대: 맞는 번호로 한 번은 성공. 실제: {first}"
    assert second.get("code") != 200, f"기대: 같은 번호를 다시 쓰면 실패. 실제: {second}"


def test_auth_code_discarded_after_five_misses(stack):
    since = _logs.now()
    code = bob_sms_code()
    wrong = "000000" if code != "000000" else "111111"
    with portal() as c:
        for _ in range(5):
            c.post("/sso/updatePassword", data={"telephone": BOB_PHONE, "password": PASS, "authCode": wrong})
        late = c.post("/sso/updatePassword", data={"telephone": BOB_PHONE, "password": PASS, "authCode": code}).json()
    assert late.get("code") != 200, f"기대: 5번 틀린 뒤에는 맞는 번호도 거부(번호 폐기). 실제: {late}"
    e = _logs.wait_event("mall-portal", since, "auth.reset.code_failed_burst", "4-5", stack)
    assert e and BOB_PHONE not in str(e), f"기대: SECURITY auth.reset.code_failed_burst, 전화번호 원문 없음. 실제: {e}"


@pytest.mark.normal
def test_bob_and_alice_log_in(stack):
    clear_counters()
    with portal() as c:
        assert portal_login(c, "alice") and portal_login(c, "bob"), "기대: 시험 뒤에도 alice·bob 정상 로그인"
