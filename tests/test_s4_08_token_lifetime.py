"""4-8 세션·JWT 수명과 로그아웃이 정말 끝나는지 · T13."""
from replay import _logs
from replay import t13_token_lifetime as t13
from replay._http import ACCOUNTS, portal
from replay.t09_login_guard import clear_counters


def member_login(c, user="alice") -> dict:
    clear_counters()
    return c.post("/sso/login", data={"username": user, "password": ACCOUNTS[user]["password"]}).json()["data"]


def info_code(c, token: str) -> int:
    return c.get("/sso/info", headers={"Authorization": "Bearer " + token}).json().get("code")


def skip_minutes(minutes: int) -> None:
    from daon import stack
    stack.redis_cli("SET", "lab:clock:offset", str(minutes * 60 * 1000))


def reset_clock() -> None:
    from daon import stack
    stack.redis_cli("DEL", "lab:clock:offset")


def test_admin_logout_ends_token_and_lifetime_is_short(stack):
    r = t13.run(stack)
    assert not r.vulnerable, "기대: 로그아웃한 토큰은 401, 수명 15분, exp는 초\n" + r.show()


def test_member_logout_ends_token(stack):
    since = _logs.now()
    with portal() as c:
        d = member_login(c)
        assert info_code(c, d["token"]) == 200
        c.post("/sso/logout", headers={"Authorization": "Bearer " + d["token"]}, data={"refreshToken": d.get("refreshToken", "")})
        assert info_code(c, d["token"]) == 401, "기대: 회원 로그아웃 뒤 같은 토큰은 401"
        again = c.post("/sso/refreshToken", data={"refreshToken": d.get("refreshToken", "")}).json()
    assert again.get("code") == 401, f"기대: 로그아웃하면 갱신 토큰도 끊김. 실제: {again}"
    e = _logs.wait_event("mall-portal", since, "auth.token.revoked_used", "4-8", stack)
    assert e, "기대: SECURITY auth.token.revoked_used(4-8)"


def test_access_token_expires_after_15_minutes(stack):
    with portal() as c:
        d = member_login(c)
        try:
            skip_minutes(16)
            import time
            time.sleep(1.2)                                # 앱이 랩 시계 값을 1초마다 다시 읽는다
            code = info_code(c, d["token"])
        finally:
            reset_clock()
    assert code == 401, f"기대: 16분 뒤 같은 액세스 토큰은 401. 실제: {code}"


def test_refresh_reuse_kills_family(stack):
    since = _logs.now()
    with portal() as c:
        d = member_login(c)
        r1 = d["refreshToken"]
        r2 = c.post("/sso/refreshToken", data={"refreshToken": r1}).json()["data"]["refreshToken"]
        reuse = c.post("/sso/refreshToken", data={"refreshToken": r1}).json()
        after = c.post("/sso/refreshToken", data={"refreshToken": r2}).json()
    assert reuse.get("code") == 401 and after.get("code") == 401, \
        f"기대: 회전된 갱신 토큰을 다시 쓰면 401이고 새 갱신 토큰도 끊김. 실제: {reuse.get('code')} / {after.get('code')}"
    e = _logs.wait_event("mall-portal", since, "auth.refresh.reuse_detected", "4-8", stack)
    assert e and r1 not in str(e), f"기대: SECURITY auth.refresh.reuse_detected, 토큰 원문 없음. 실제: {e}"


def test_normal_refresh_keeps_working(stack):
    with portal() as c:
        d = member_login(c)
        rt = d["refreshToken"]
        for _ in range(3):
            n = c.post("/sso/refreshToken", data={"refreshToken": rt}).json()
            assert n.get("code") == 200, f"기대: 정상 갱신은 계속 됨. 실제: {n}"
            rt = n["data"]["refreshToken"]
            assert info_code(c, n["data"]["token"]) == 200
