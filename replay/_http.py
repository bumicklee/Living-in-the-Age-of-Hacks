"""재연·시험이 같이 쓰는 HTTP 도우미."""
from __future__ import annotations

import httpx

from daon.config import URLS
from replay import NotReady

# seed/spring/20-daon-accounts.sql 의 가짜 계정
ACCOUNTS = {
    "alice": {"password": "daon-pass-1!", "member_id": 101, "orders": [9001, 9002]},
    "bob": {"password": "daon-pass-1!", "member_id": 102, "orders": [9003]},
}

# 관리자 앱 계정. admin은 원본 데모 계정(슈퍼 관리자), staff_*는 다온마켓 직원(seed/spring/20-daon-accounts.sql)
ADMINS = {
    "admin": {"password": "macro123", "role": "슈퍼 관리자"},
    "staff_goods": {"password": "daon-pass-1!", "role": "상품 관리자"},
    "staff_order": {"password": "daon-pass-1!", "role": "주문 관리자"},
}


def new_trace_id(tag: str) -> str:
    """요청 추적 번호(X-Trace-Id, 랩-설계.md 11절). 재연이 보낸 번호로 앱 로그를 찾는다."""
    import uuid
    return f"replay-{tag}-{uuid.uuid4().hex[:12]}"


def client(base: str) -> httpx.Client:
    return httpx.Client(base_url=base, timeout=10.0)


def portal() -> httpx.Client:
    return client(URLS["spring"]["portal"])


def portal_login(c: httpx.Client, user: str) -> str:
    """mall-portal 로그인 → 'Bearer <토큰>'."""
    try:
        r = c.post("/sso/login", data={"username": user, "password": ACCOUNTS[user]["password"]})
    except httpx.TransportError as e:
        raise NotReady(f"mall-portal({c.base_url})에 닿지 않습니다. make up STACK=spring 먼저 실행하세요. ({e.__class__.__name__})")
    body = r.json()
    if body.get("code") != 200:
        raise NotReady(f"{user} 로그인 실패: {body.get('message')}. 시드가 들어갔는지 확인하세요(make down WIPE=1 후 make up).")
    return body["data"]["tokenHead"] + body["data"]["token"]


def admin() -> httpx.Client:
    return client(URLS["spring"]["admin"])


def admin_login(c: httpx.Client, user: str) -> str:
    """mall-admin 로그인 → 'Bearer <토큰>'."""
    try:
        r = c.post("/admin/login", json={"username": user, "password": ADMINS[user]["password"]})
    except httpx.TransportError as e:
        raise NotReady(f"mall-admin({c.base_url})에 닿지 않습니다. make up STACK=spring 먼저 실행하세요. ({e.__class__.__name__})")
    body = r.json()
    if body.get("code") != 200:
        raise NotReady(f"{user} 관리자 로그인 실패: {body.get('message')}. 직원 시드가 들어갔는지 확인하세요(make wipe 후 make up).")
    return body["data"]["tokenHead"] + body["data"]["token"]


def admin_get(user: str, path: str, **kw) -> httpx.Response:
    with admin() as c:
        token = admin_login(c, user)
        return c.get(path, headers={"Authorization": token}, **kw)
