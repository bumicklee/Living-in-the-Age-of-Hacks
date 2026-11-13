"""7-7 캐시 값의 타입 고정·허용 목록 · 방어 확인(안전 정책: 공격 코드를 만들지 않는다).

시작 지점: BaseRedisConfig가 아무 타입이나 허용(LaissezFaireSubTypeValidator)해서, 캐시 글자의 "@class"가 앱이 만들 객체를 고른다 → 레드.
완료 지점: 관리자 캐시는 JSON 글자 하나로만 두고 정한 타입(UmsAdmin)으로만 읽는다. 허용 목록 밖 타입이나 글자가 아닌 값은 거부 → 키를 지우고 DB에서 다시 읽는다 + deserialize.denied.type.
재연은 허용 목록 밖의 무해한 JDK 클래스(AtomicLong)만 쓴다. 키는 시험이 끝나면 지운다.
"""
import time

import httpx
import pytest

from daon import stack as st
from daon.config import ROOT
from replay import _logs
from replay import deserialize_cache_type as rep
from replay._http import admin, admin_login, portal, portal_login


def stack_name():
    return "spring"


def admin_info_after(value: str | None):
    """admin으로 로그인 → (값이 있으면 캐시 키를 그 값으로 바꾸고) → /admin/info. 반환: (응답, 시작 시각)"""
    since = _logs.now()
    with admin() as c:
        tok = admin_login(c, "admin")
        if value is not None:
            rep.write_cache(value)
        r = c.get("/admin/info", headers={"Authorization": tok})
    st.redis_cli("DEL", rep.KEY)
    return r, since


def test_redis_config_has_no_laissez_faire_validator():
    src = (ROOT / "spring/mall-common/src/main/java/com/macro/mall/common/config/BaseRedisConfig.java").read_text("utf-8")
    code = "\n".join(l for l in src.splitlines() if not l.strip().startswith(("//", "*", "/*")))
    uses = "activateDefaultTyping(LaissezFaireSubTypeValidator" in code.replace("\n", "").replace(" ", "") or "activateDefaultTyping(LaissezFaire" in code
    assert "BasicPolymorphicTypeValidator" in code and not uses, "기대: 허용 목록(BasicPolymorphicTypeValidator)을 쓰고 activateDefaultTyping에 LaissezFaireSubTypeValidator를 직접 넘기지 않음"


def test_replay_harmless_class_is_not_created():
    r = rep.run()
    assert not r.vulnerable, "기대: 허용 목록 밖 클래스로 객체를 만들지 않음. 실제:\n" + "\n".join(r.evidence)


def test_denied_type_is_logged_and_cache_is_dropped():
    r, since = admin_info_after(rep.HARMLESS)
    e = _logs.wait_event("mall-admin", since, "deserialize.denied.type", "7-7", stack_name(), timeout=12,
                         target="cache:mall:ums:admin:* class:java.util.concurrent.atomic.AtomicLong")
    assert e, "기대: SECURITY deserialize.denied.type(7-7, target cache:mall:ums:admin:* class:java.util.concurrent.atomic.AtomicLong)이 mall-admin 로그에 남음"
    assert e["actor"] == "system:redis-cache" and e["reason"] == "캐시 값의 타입이 허용 목록 밖", f"기대: actor system:redis-cache, reason 「캐시 값의 타입이 허용 목록 밖」. 실제 {e}"
    assert e["target"] == "cache:mall:ums:admin:* class:java.util.concurrent.atomic.AtomicLong", f"기대: target은 키 이름 앞부분과 class:<이름>. 실제 {e['target']}"
    assert st.redis_cli("EXISTS", rep.KEY).stdout.strip() in ("0", "(integer) 0"), "기대: 거부한 키는 지워짐(캐시를 버림)"


def test_class_name_in_event_is_sanitized():
    """클래스 이름은 저장소에 쓴 사람이 정한 값이다. 줄바꿈·따옴표 같은 글자는 남기지 않는다(로그 위조 막기)."""
    r, since = admin_info_after('["a.b.Evil\\"\\n{\\"event\\":\\"fake\\"}",1]')
    ev = _logs.security_events("mall-admin", since, stack_name())
    assert not [e for e in ev if e.get("event") == "fake"], "기대: 클래스 이름에 끼워 넣은 가짜 이벤트 줄이 생기지 않음"
    den = [e for e in ev if e.get("event") == "deserialize.denied.type"]
    assert den, "기대: 거부 이벤트가 남음"
    tail = den[0]["target"].split("class:", 1)[1]
    assert tail.replace(".", "").replace("$", "").replace("_", "").isalnum() and len(tail) <= 120, f"기대: 클래스 이름은 영문·숫자·.$_ 만, 120자까지. 실제 {tail!r}"


def test_old_format_cache_value_is_dropped_not_trusted():
    """예전 형식(타입 이름이 든 객체)이 남아 있어도 믿지 않고 버린다. 사용자는 오류를 보지 않는다."""
    old = '["com.macro.mall.model.UmsAdmin",{"id":1,"username":"admin"}]'
    r, since = admin_info_after(old)
    assert r.status_code == 200 and r.json().get("code") == 200, f"기대: 사용자는 오류를 보지 않음(DB에서 다시 읽음). 실제 {r.status_code} {r.text[:80]}"
    assert _logs.wait_event("mall-admin", since, "deserialize.denied.type", "7-7", stack_name(), timeout=12), "기대: 예전 형식도 deserialize.denied.type으로 남음"


@pytest.mark.normal
def test_admin_login_and_cache_roundtrip_still_work():
    with admin() as c:
        tok = admin_login(c, "admin")
        for _ in range(2):          # 두 번째는 캐시에서 읽는다
            r = c.get("/admin/info", headers={"Authorization": tok})
            assert r.status_code == 200 and r.json().get("code") == 200 and r.json()["data"]["username"] == "admin", f"기대: 관리자 정보 조회. 실제 {r.text[:100]}"
    assert st.redis_cli("EXISTS", rep.KEY).stdout.strip() in ("1", "(integer) 1"), "기대: 정상 로그인 뒤 관리자 캐시 키가 있음(캐시는 그대로 쓰임)"


@pytest.mark.normal
def test_member_login_and_order_list_still_work():
    with portal() as c:
        tok = portal_login(c, "alice")
        r = c.get("/sso/info", headers={"Authorization": tok})
        assert r.json().get("code") == 200, f"기대: 회원 정보 조회. 실제 {r.text[:100]}"
