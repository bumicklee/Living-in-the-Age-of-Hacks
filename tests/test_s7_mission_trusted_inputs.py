"""7-미션 믿는 입력에 같은 확인을: 회원 캐시·권한 목록 캐시의 형식 고정, 방어 스위치 파일 서명.

7-7에서 관리자 캐시(UmsAdmin)에 쓴 형식 고정·허용 목록을 회원 캐시(UmsMember)와 권한 목록 캐시(List<UmsResource>)에도 쓴다.
앱이 믿는 설정 파일(.daon/switches.env)에는 서명을 붙이고, 서명이 맞지 않는 「꺼짐」은 믿지 않는다.
재연은 허용 목록 밖의 무해한 JDK 클래스(AtomicLong)만 쓴다. 값은 가짜, 서명 키도 랩 전용 가짜 값이다.
"""
import hashlib
import hmac
import subprocess
import time

import pytest

from daon import lab, stack as st
from daon.config import ROOT
from replay import _logs
from replay import deserialize_cache_type as rep
from replay import t01_order_idor as t01
from replay._http import admin, admin_login, portal, portal_login

BAD = rep.HARMLESS


def test_member_cache_rejects_type_chosen_by_stored_text():
    since = _logs.now()
    with portal() as c:
        tok = portal_login(c, "alice")
        key = "mall:ums:member:alice"
        r = st.redis_cli("SET", key, BAD, "EX", "300")
        assert "OK" in r.stdout, "시험 준비: Redis에 쓰지 못함"
        resp = c.get("/sso/info", headers={"Authorization": tok})
    st.redis_cli("DEL", key)
    assert resp.json().get("code") == 200, f"기대: 사용자는 오류를 보지 않음(DB에서 다시 읽음). 실제 {resp.text[:100]}"
    e = _logs.wait_event("mall-portal", since, "deserialize.denied.type", "7-7", "spring", timeout=12, target="cache:mall:ums:member:* class:java.util.concurrent.atomic.AtomicLong")
    assert e, "기대: 회원 캐시도 deserialize.denied.type(target cache:mall:ums:member:* class:java.util.concurrent.atomic.AtomicLong)"


def test_resource_list_cache_rejects_type_chosen_by_stored_text():
    since = _logs.now()
    with admin() as c:
        tok = admin_login(c, "admin")
        c.get("/admin/info", headers={"Authorization": tok})
        keys = [k for k in st.redis_cli("--scan", "--pattern", "mall:ums:resourceList:*").stdout.split() if not k.endswith(":sig")]
        assert keys, "시험 준비: 권한 목록 캐시 키가 있어야 함(관리자 로그인 뒤)"
        for k in keys:                      # 어느 관리자 번호인지 몰라도 되게 모든 권한 목록 키를 바꾼다(서명 키는 그대로)
            assert "OK" in st.redis_cli("SET", k, BAD, "EX", "300").stdout, "시험 준비: Redis에 쓰지 못함"
        c.get("/admin/info", headers={"Authorization": tok})
        resp = c.get("/product/list", headers={"Authorization": tok})
    st.redis_cli("DEL", *keys)
    e = _logs.wait_event("mall-admin", since, "deserialize.denied.type", "7-7", "spring", timeout=12, target="cache:mall:ums:resourceList:* class:java.util.concurrent.atomic.AtomicLong")
    assert e, "기대: 권한 목록 캐시도 deserialize.denied.type(target cache:mall:ums:resourceList:* class:java.util.concurrent.atomic.AtomicLong)"
    assert resp.status_code == 200 and resp.json().get("code") == 200, f"기대: 권한은 DB에서 다시 읽어 정상 응답. 실제 {resp.text[:100]}"


@pytest.mark.normal
def test_switch_signature_unit_tests_pass():
    if not (ROOT / "spring/mall-security/src/test/java/com/macro/mall/security/securekit/SwitchSignatureTest.java").exists():
        return  # 시작 지점에는 이 단위 시험이 없다.
    r = subprocess.run(["docker", "run", "--rm", "-v", f"{ROOT}/spring:/src", "-v", "daon-m2:/root/.m2", "-w", "/src", "maven:3.9.12-eclipse-temurin-17",
                        "mvn", "-B", "-pl", "mall-security", "-am", "test", "-Dtest=SwitchSignatureTest", "-Dsurefire.failIfNoSpecifiedTests=false", "-Ddocker.skip=true", "-DskipTests=false"],
                       capture_output=True, text=True, timeout=900)
    out = r.stdout + r.stderr
    assert "Tests run: 4, Failures: 0, Errors: 0" in out, "기대: SwitchSignatureTest 4개 통과\n" + "\n".join(l for l in out.splitlines() if "ERROR" in l or "Tests run" in l)[:500]


@pytest.mark.normal
def test_make_switch_signs_the_off_lines(tmp_path, monkeypatch):
    monkeypatch.setattr(lab, "DATA", tmp_path)
    monkeypatch.setattr(lab, "STATE", tmp_path / "switches.json")
    monkeypatch.setattr(lab, "ENV_FILE", tmp_path / "switches.env")
    values = {k: True for k in lab.SWITCHES}
    values["ownership"] = False
    values["upload"] = False
    lab._write(values)
    lines = (tmp_path / "switches.env").read_text("utf-8").splitlines()
    sig = [l for l in lines if l.startswith("SECUREKIT_SIG=")]
    off = sorted(l for l in lines if l.endswith("=false"))
    assert off == ["SECUREKIT_OWNERSHIP_ENABLED=false", "SECUREKIT_UPLOAD_ENABLED=false"], f"꺼진 줄 {off}"
    key = lab.ENV.get("DAON_SWITCH_SIGN_KEY", lab.SIGN_KEY_DEFAULT).encode()
    assert sig and sig[0].split("=", 1)[1] == hmac.new(key, "\n".join(off).encode(), hashlib.sha256).hexdigest(), "기대: 서명 = 꺼진 줄을 정렬해 이은 글자의 HMAC-SHA256"
    values["ownership"] = values["upload"] = True
    lab._write(values)
    assert "SECUREKIT_SIG" not in (tmp_path / "switches.env").read_text("utf-8"), "기대: 꺼진 스위치가 없으면 서명 줄도 없음"


def _recreate():
    st.compose("spring", "up", "-d", "--no-deps", "--force-recreate", "mall-portal")
    st.wait_healthy("spring")


def test_hand_edited_switch_file_is_not_trusted_but_signed_one_is():
    """① 서명 없이 손으로 ownership을 끈 파일: 앱은 믿지 않고 켜진 채 뜬다(T01 그린, config.signature.invalid). ② make switch로 서명해서 끄면: 믿는다(T01 레드)."""
    if not t01.__name__:
        raise AssertionError
    saved = lab.ENV_FILE.read_text("utf-8") if lab.ENV_FILE.exists() else None
    saved_state = lab.STATE.read_text("utf-8") if lab.STATE.exists() else None
    try:
        lab.ENV_FILE.write_text("SECUREKIT_OWNERSHIP_ENABLED=false\n", "utf-8")          # 손으로 고친 파일: 서명 없음
        since = _logs.now()
        _recreate()
        r = t01.run("spring")
        assert not r.vulnerable, "기대: 서명이 없는 꺼짐은 믿지 않아 소유권 검사가 켜진 채 뜸(T01 그린). 실제:\n" + "\n".join(r.evidence)
        e = _logs.wait_event("mall-portal", since, "config.signature.invalid", "7-mission", "spring", timeout=10)
        assert e and e["target"] == "config:switches.env", f"기대: SECURITY config.signature.invalid가 남음. 실제 {e}"
        values = {k: True for k in lab.SWITCHES}
        values["ownership"] = False
        lab._write(values)                                                                  # make switch와 같은 방식: 서명 포함
        _recreate()
        r2 = t01.run("spring")
        assert r2.vulnerable, "기대: 서명이 맞는 꺼짐은 따른다(영상용 스위치가 동작) → T01 레드. 실제:\n" + "\n".join(r2.evidence)
    finally:
        lab._write({k: True for k in lab.SWITCHES})
        if saved_state is not None and saved_state.strip():
            pass
        _recreate()
