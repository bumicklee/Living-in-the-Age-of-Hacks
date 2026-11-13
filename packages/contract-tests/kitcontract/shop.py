"""15-4 가게 예제의 계약 셋(CONTRACT.md 2절): error-response · rate-limit.login · @Audited.

부품을 붙이기 전(시작 지점)에는 셋 다 실패하고, 붙인 뒤(완료 지점)에는 Spring·Express 가게 앱 모두 통과해야 한다.
"""
from __future__ import annotations

from kitcontract.client import GENERIC_ERROR, Kit, trace

SHOP_ADMIN = ("shop-admin", "shop-pass-1!")


def reset(k: Kit) -> None:
    """부품이 붙었으면 /_kit/reset, 아니면 가게 앱 자체의 /api/_reset"""
    r = k.post("/_kit/reset")
    if r.status_code != 204:
        k.post("/api/_reset")


def login(k: Kit) -> str:
    r = k.post("/api/login", json={"username": SHOP_ADMIN[0], "password": SHOP_ADMIN[1]})
    assert r.status_code == 200, f"shop-admin 로그인 실패 HTTP {r.status_code}"
    return r.json()["token"]


def error_response(k: Kit) -> None:
    tid = trace()
    r = k.get("/api/orders/x", headers={"X-Trace-Id": tid})
    assert r.status_code == 500, f"없는 주문 번호 형식 → 500 기대, 실제 {r.status_code}"
    for word in ("Exception", "oms_order", "SQL"):
        assert word not in r.text, f"오류 응답에 내부 문구 {word!r}가 나간다(error-response 부품 전)"
    body = r.json()
    assert body.get("message") == GENERIC_ERROR and body.get("traceId") == tid, f"일반 문구 + 추적 번호 기대. 실제 {body}"


def rate_limit_login(k: Kit) -> None:
    reset(k)
    codes = [k.post("/api/login", json={"username": SHOP_ADMIN[0], "password": f"wrong-{i}"}).status_code for i in range(6)]
    assert codes[:5] == [401] * 5, f"틀린 비밀번호 5번은 401. 실제 {codes}"
    assert codes[5] == 429, f"6번째 → 429 기대(rate-limit.login 부품 전에는 계속 401). 실제 {codes[5]}"
    reset(k)


def audited_member_read(k: Kit) -> None:
    reset(k)
    token = login(k)
    tid = trace()
    r = k.get("/api/admin/members/3", headers={"Authorization": f"Bearer {token}", "X-Access-Reason": "CS-0002", "X-Trace-Id": tid})
    assert r.status_code == 200 and str(r.json().get("phone", "")).startswith("010-****-"), f"회원 상세 200(가린 번호). 실제 {r.status_code}"
    rec = next((x for x in reversed(k.audit()) if x.get("trace_id") == tid), None)
    assert rec, "감사 기록이 없다(@Audited 전)"
    want = {"action": "member.read", "target": "member:3", "actor": "admin:shop-admin", "count": 1, "reason": "CS-0002", "result": "ok"}
    assert {x: rec.get(x) for x in want} == want, f"감사 기록 값. 실제 {rec}"


CHECKS = {"error-response": error_response, "rate-limit.login": rate_limit_login, "audit": audited_member_read}
