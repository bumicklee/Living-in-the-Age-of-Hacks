"""5-3 SK텔레콤 평문 인증키: 저장 데이터 암호화가 지켜야 했던 것 · T14.

시작 지점(평문 DB): ①③ 레드. 완료 지점: 모두 그린.
④(OpenBao 감사 로그)는 OpenBao를 새로 만든 직후(make wipe HARD=1)에만 시작 지점에서 레드다. 한 번이라도 앱이 풀었으면 기록이 남는다.
5-3을 마치면 전화번호·주소 검색은 깨진다(암호문과 비교하므로). 그 복구는 5-4다 → 전체 시험(check-all)에서 전화번호로 찾는 4-5 시험은 5-3 완료 지점에서 레드, 5-4 완료 지점에서 다시 그린이다.
"""
import re
import subprocess
import time

import pytest

from replay import _logs
from replay import t14_pii_at_rest as t14
from replay._http import ACCOUNTS, ADMINS, admin, portal
from replay.t09_login_guard import clear_counters

PLAIN_PHONE = re.compile(r'"phone":"(\d{2,3}-?\d{3,4}-?\d{4})"')


def mysql(sql: str) -> str:
    return t14._mysql(sql)


def admin_headers(c) -> dict:
    clear_counters()
    t = c.post("/admin/login", json={"username": "admin", "password": ADMINS["admin"]["password"]}).json()["data"]["token"]
    return {"Authorization": "Bearer " + t}


def test_db_and_cache_have_no_plain_pii(stack):
    r = t14.run(stack)
    assert not r.vulnerable, "기대: 개인정보 칸 다섯 개와 Redis 회원 캐시에 평문이 없어야 합니다\n" + r.show()


@pytest.mark.normal
def test_screens_still_show_phone_and_address(stack):
    """잠근 뒤에도 화면(API)에서는 정상으로 보여야 한다"""
    with admin() as c:
        o = c.get("/order/9003", headers=admin_headers(c)).json()["data"]
    # 5-4를 마치면 관리자 응답의 번호는 가운데가 가려진다(010-****-0102). 5-3 시점엔 원문이다. 둘 다 정상으로 본다.
    assert o["receiverPhone"] in ("010-0000-0102", "010-****-0102") and o["receiverDetailAddress"], \
        f"기대: 관리자 주문 상세에 전화번호·주소가 정상으로 보임. 실제: {o.get('receiverPhone')!r}"
    with portal() as c:
        clear_counters()
        tok = c.post("/sso/login", data={"username": "bob", "password": ACCOUNTS["bob"]["password"]}).json()["data"]["token"]
        d = c.get("/order/detail/9003", headers={"Authorization": "Bearer " + tok}).json()["data"]
    assert d["receiverPhone"] == "010-0000-0102", f"기대: 고객 주문 상세에도 정상으로 보임. 실제: {d.get('receiverPhone')!r}"


def test_member_cache_keeps_only_masked_phone(stack):
    from daon import stack as lab
    keys = lab.redis_cli("--scan", "--pattern", "mall:ums:member:*").stdout.split()
    if keys:
        lab.redis_cli("DEL", *keys)                    # 앞 시험이 남긴 옛 캐시를 비우고 새로 채운다
    with portal() as c:
        clear_counters()
        c.post("/sso/login", data={"username": "alice", "password": ACCOUNTS["alice"]["password"]})
    plain = [k for k in lab.redis_cli("--scan", "--pattern", "mall:ums:member:*").stdout.split()
             if PLAIN_PHONE.search(lab.redis_cli("GET", k).stdout)]
    assert not plain, f"기대: 회원 캐시에는 전화번호를 가린 값만. 평문이 든 키 {len(plain)}개"


def test_openbao_audit_log_records_key_use(stack):
    out = subprocess.run(["docker", "exec", "daon-openbao-1", "sh", "-c",
                          "grep -c -E '\"path\":\"transit/(decrypt|datakey/plaintext)/daon-pii\"' /openbao/logs/audit.log || true"],
                         capture_output=True, text=True).stdout.strip()
    assert out.isdigit() and int(out) > 0, "기대: OpenBao 감사 로그에 앱의 데이터 키 풀기(또는 만들기) 기록이 있어야 합니다"


def test_decrypt_failure_is_logged_without_value(stack):
    since = _logs.now()
    row = mysql("SELECT id, phone FROM ums_member WHERE phone LIKE 'enc:1:%' ORDER BY id LIMIT 1").split("\t")
    if len(row) != 2:
        pytest.fail("기대: 잠긴 회원 행이 있어야 합니다(시험 전에 5-3 완료 지점에서 앱이 한 번 떠 있어야 함)")
    mid, original = row
    broken = original[:-4] + ("AAAA" if not original.endswith("AAAA") else "BBBB")   # 값이 바뀐 경우
    try:
        mysql(f"UPDATE ums_member SET phone = '{broken}' WHERE id = {mid}")
        with admin() as c:
            c.get("/daon/customer/list", headers=admin_headers(c))
    finally:
        mysql(f"UPDATE ums_member SET phone = '{original}' WHERE id = {mid}")
    e = _logs.wait_event("mall-admin", since, "crypto.decrypt.failed", "5-3", stack)
    assert e, "기대: SECURITY crypto.decrypt.failed(5-3)가 mall-admin 로그에 남아야 합니다"
    assert original not in str(e) and broken not in str(e), f"기대: 이벤트에 암호문·평문 값이 없어야 합니다. 실제: {e}"
