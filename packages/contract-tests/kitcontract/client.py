"""계약 시험 도우미: 예제 앱 하나(127.0.0.1)에 HTTP로 묻는다. 값은 모두 가짜다(CONTRACT.md).

이 파일은 secure-kit과 같은 MIT 라이선스다. 회사 서비스에 부품을 붙인 뒤 같은 시험을 돌려도 된다(주소만 바꾼다).
"""
from __future__ import annotations

import base64
import json
import os
import time
import uuid

import httpx

APPS = {
    "spring-demo": os.environ.get("KIT_SPRING_DEMO", "http://127.0.0.1:18181"),
    "express-demo": os.environ.get("KIT_EXPRESS_DEMO", "http://127.0.0.1:18182"),
    "spring-shop": os.environ.get("KIT_SPRING_SHOP", "http://127.0.0.1:18183"),
    "express-shop": os.environ.get("KIT_EXPRESS_SHOP", "http://127.0.0.1:18184"),
}
USER = ("kit-user", "kit-pass-1!")
ADMIN = ("kit-admin", "kit-admin-pass-1!")
GENERIC_ERROR = "요청을 처리하지 못했습니다"
LOGIN_FAILED = "아이디 또는 비밀번호가 올바르지 않습니다"


class KitNotReady(Exception):
    """예제 앱이 떠 있지 않다(make kit-up)."""


class Kit:
    def __init__(self, base: str, name: str = "") -> None:
        self.base = base.rstrip("/")
        self.name = name or base
        self.c = httpx.Client(base_url=self.base, timeout=15.0, follow_redirects=False)

    # ── 기본 ──
    def req(self, method: str, path: str, **kw) -> httpx.Response:
        try:
            return self.c.request(method, path, **kw)
        except httpx.TransportError as e:
            raise KitNotReady(f"{self.name}({self.base})에 닿지 않습니다: {e.__class__.__name__}. make kit-up 먼저") from e

    def get(self, path: str, **kw) -> httpx.Response:
        return self.req("GET", path, **kw)

    def post(self, path: str, **kw) -> httpx.Response:
        return self.req("POST", path, **kw)

    def ready(self) -> bool:
        try:
            self.c.get("/kit/public" if "demo" in self.name else "/api/health", timeout=3.0)
            return True
        except httpx.TransportError:
            return False

    # ── 랩 전용 엔드포인트 ──
    def reset(self) -> None:
        r = self.post("/_kit/reset")
        assert r.status_code == 204, f"/_kit/reset → 204 기대, 실제 {r.status_code}"

    def seq(self) -> int:
        r = self.get("/_kit/events", params={"after": 0})
        assert r.status_code == 200, f"/_kit/events 없음(HTTP {r.status_code}): 부품의 랩 전용 엔드포인트가 꺼져 있다"
        return int(r.json()["seq"])

    def events(self, after: int) -> list[dict]:
        return self.get("/_kit/events", params={"after": after}).json()["events"]

    def wait_event(self, after: int, name: str, timeout: float = 3.0, **match) -> dict | None:
        t0 = time.time()
        while True:
            for e in self.events(after):
                if e.get("event") == name and all(e.get(k) == v for k, v in match.items()):
                    return e
            if time.time() - t0 > timeout:
                return None
            time.sleep(0.2)

    def audit(self) -> list[dict]:
        r = self.get("/_kit/audit")
        assert r.status_code == 200, f"/_kit/audit 없음(HTTP {r.status_code}): 감사 로그 부품이 붙지 않았다"
        return r.json()["records"]

    # ── 로그인 ──
    def login(self, user: tuple[str, str] = USER) -> str:
        r = self.post("/kit/login", json={"username": user[0], "password": user[1]})
        assert r.status_code == 200, f"{user[0]} 로그인 실패 HTTP {r.status_code} {r.text[:120]}"
        return r.json()["token"]

    @staticmethod
    def bearer(token: str) -> dict:
        return {"Authorization": f"Bearer {token}"}


def trace() -> str:
    return f"kit-{uuid.uuid4().hex[:16]}"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def b64url_decode(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def jwt_parts(token: str) -> tuple[dict, dict]:
    h, p, _ = token.split(".")
    return json.loads(b64url_decode(h)), json.loads(b64url_decode(p))


def alg_none(token: str) -> str:
    """같은 내용에 머리만 alg=none으로 바꾸고 서명을 뺀 토큰(받아들이면 안 된다)"""
    _, p = jwt_parts(token)
    return f"{b64url(json.dumps({'alg': 'none', 'typ': 'JWT'}).encode())}.{b64url(json.dumps(p).encode())}."


PNG = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\rIHDR" + b"\x00" * 64
