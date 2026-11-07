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
