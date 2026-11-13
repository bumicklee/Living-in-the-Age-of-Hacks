"""6-4 저장소를 PC에 열어 두지 않기 · T16 · 권한 캐시를 믿지 않기(F25).

저장소 포트·계정·망 분리·게이트웨이는 별도 저장소 인증 시험과 함께 확인한다.
캐시 시험은 로그인한 직원의 정확한 키만 변경한다. 원문·서명·남은 TTL은 예외에도 복구한다.
서명 손상과 본문 변경을 나눠 보고 정상 권한 유지·금지 권한 거부를 함께 검사한다.
"""
import socket
import time
import uuid

import httpx
import pytest

from daon import stack as lab
from daon.config import HOST, ENV, port
from replay import _logs, _cache_probe as cache
from replay import t16_open_stores as t16
from replay._http import ADMINS, admin, portal, portal_login
from replay.t09_login_guard import clear_counters

CLOSED = {"MySQL": ("DAON_MYSQL_PORT", 3306), "Redis": ("DAON_REDIS_PORT", 6379), "RabbitMQ": ("DAON_RABBIT_PORT", 5672),
          "RabbitMQ 관리 화면": ("DAON_RABBIT_UI_PORT", 15672), "MongoDB": ("DAON_MONGO_PORT", 27017), "MinIO 관리 화면": ("DAON_MINIO_UI_PORT", 9001)}


def test_t16_replay_is_green():
    r = t16.run()
    assert not r.vulnerable, "기대: T16 그린(PC에서 저장소에 바로 들어갈 수 없음). 실제:\n" + "\n".join(r.evidence)


def test_store_ports_not_reachable_from_pc():
    open_ = []
    for name, (env, default) in CLOSED.items():
        with socket.socket() as s:
            s.settimeout(0.5)
            if s.connect_ex((HOST, port(env, default))) == 0:
                open_.append(name)
    assert not open_, f"기대: 저장소 포트는 PC에 열리지 않음. 열린 곳: {open_}"


@pytest.mark.normal
def test_apps_and_shop_images_still_work(stack):
    with portal() as c:
        assert portal_login(c, "alice").startswith("Bearer ")
    r = httpx.get(f"http://{HOST}:{port('DAON_MINIO_PORT', 9000)}/minio/health/live", timeout=5)
    assert r.status_code == 200, "기대: 상품 사진을 받는 MinIO 주소(9000)는 그대로"


def _admin_headers(c, user="staff_goods") -> dict:
    clear_counters()
    tok = c.post("/admin/login", json={"username": user, "password": ADMINS[user]["password"]}).json()["data"]["token"]
    return {"Authorization": "Bearer " + tok}


@pytest.mark.parametrize("changed", ["signature", "body"])
def test_tampered_cache_is_discarded_and_normal_permission_survives(stack, changed):
    with admin() as c:
        h = _admin_headers(c)
        aid = cache.admin_id("staff_goods")
        key = f"mall:ums:resourceList:{aid}"
        sig = key + ":sig"
        with cache.preserved(key, sig):
            assert c.get("/product/list", headers=h).json().get("code") == 200
            original = cache.get(key)
            assert original is not None and cache.get(sig) is not None
            if changed == "signature":
                cache.set_value(sig, '\"not-a-valid-signature\"')
            else:
                # 형식은 유지하고 본문만 비운다. 서명은 원래 값 그대로 둔다.
                cache.set_value(key, cache.replace_rows(original, []))
            since = _logs.now()
            r = c.get("/product/list", headers=h)
            assert r.status_code == 200 and r.json().get("code") == 200
            event = _logs.wait_event("mall-admin", since, "cache.integrity.failed", "6-4", stack,
                                    target=f"cache:resourceList:{aid}")   # 다른 직원(관리자 등)의 같은 이벤트와 섞이지 않게
            assert event and event.get("target") == f"cache:resourceList:{aid}"
            assert cache.resource_rows(cache.get(key)), "DB에서 권한을 다시 읽어야 함"


def test_changed_body_cannot_grant_order_permission(stack):
    with admin() as c:
        h = _admin_headers(c)
        aid = cache.admin_id("staff_goods")
        key = f"mall:ums:resourceList:{aid}"
        with cache.preserved(key, key + ":sig"):
            assert c.get("/product/list", headers=h).json().get("code") == 200
            assert c.get("/order/list", headers=h).json().get("code") == 403
            super_h = _admin_headers(c, "admin")
            assert c.get("/order/list", headers=super_h).json().get("code") == 200
            super_id = cache.admin_id("admin")
            super_rows = cache.resource_rows(cache.get(f"mall:ums:resourceList:{super_id}"))
            order = next(x for x in super_rows if cache.fields(x).get("url") == "/order/**")
            order_id = cache.fields(order)["id"]
            original = cache.get(key)
            rows = cache.resource_rows(original)
            assert all(cache.fields(x).get("id") != order_id for x in rows)
            cache.set_value(key, cache.replace_rows(original, rows + [order]))
            since = _logs.now()
            r = c.get("/order/list", headers=h)
            assert r.status_code == 403 or r.json().get("code") == 403
            event = _logs.wait_event("mall-admin", since, "cache.integrity.failed", "6-4", stack,
                                    target=f"cache:resourceList:{aid}")   # 다른 직원(관리자 등)의 같은 이벤트와 섞이지 않게
            assert event and event.get("target") == f"cache:resourceList:{aid}"
            assert all(cache.fields(x).get("id") != order_id for x in cache.resource_rows(cache.get(key)))
            assert c.get("/product/list", headers=h).json().get("code") == 200


@pytest.mark.normal          # 시험 도구(_cache_probe) 자체 시험: 앱 수정과 무관해 시작 지점에서도 그린
@pytest.mark.parametrize("ttl", [None, -1, 300000])  # 전체 회귀 중 Docker 지연이 있어도 시험용 키가 먼저 만료되지 않게
def test_cache_restores_original_value_and_ttl_after_exception(ttl):
    key = "mall:lab:cache-probe:" + uuid.uuid4().hex
    try:
        if ttl is not None:
            cache.set_value(key, "original")
            if ttl > 0:
                cache.redis("PEXPIRE", key, str(ttl))
        with pytest.raises(RuntimeError, match="intentional"):
            with cache.preserved(key):
                cache.set_value(key, "changed")
                time.sleep(0.05)
                raise RuntimeError("intentional")
        expected = None if ttl is None else "original"
        assert cache.get(key) == expected
        remaining = int(cache.redis("PTTL", key))
        if ttl is None:
            assert remaining == -2
        elif ttl == -1:
            assert remaining == -1
        else:
            assert 0 < remaining < ttl, "복구 때 TTL을 처음 값으로 연장하지 않는다"
    finally:
        cache.redis("DEL", key)


@pytest.mark.normal          # 시험 도구(_cache_probe) 자체 시험
def test_cache_expired_during_probe_is_not_resurrected():
    key = "mall:lab:cache-probe:" + uuid.uuid4().hex
    try:
        cache.set_value(key, "original")
        cache.redis("PEXPIRE", key, "2000")
        with cache.preserved(key):
            cache.set_value(key, "changed")
            time.sleep(2.1)
        assert cache.get(key) is None, "시험 중 만료된 원문을 복구하며 되살리지 않는다"
    finally:
        cache.redis("DEL", key)
