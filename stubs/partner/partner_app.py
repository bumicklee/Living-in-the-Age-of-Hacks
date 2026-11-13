"""가짜 파트너 앱 「다온 배송 파트너」(수업 12장). 다온마켓 파트너 API만 부르는 클라이언트 프로그램이다(서버가 아니다).

다른 스텁(결제사·문자·택배)은 다온마켓이 부르는 「밖」이고, 이 스텁은 다온마켓을 부르는 「밖」이다. 그래서 컨테이너가 아니라
PC에서 도는 파이썬 파일 하나다(표준 라이브러리만). 재연(replay/t33_partner_token.py)·시험·`make partner-app`이 이 파일을 쓴다.

    python stubs/partner/partner_app.py --app courier-01 orders        # 배정 주문을 쪽마다 받아 건수만 찍는다
    python stubs/partner/partner_app.py --app analytics-01 export      # 내보내기 한 번(건수만)

- 다온마켓 쪽 상태에 맞춰 스스로 고른다(수업마다 코드를 바꾸지 않아도 되게):
    12-2 시작   POST /partner/token이 없다(404) → 고정 키 X-Partner-Key로 부른다
    12-2 완료   POST /partner/token(클라이언트 자격 증명)으로 15분 토큰 → 만료(401 invalid_token)면 한 번 다시 받는다(갱신 로직)
    12-3 완료   파트너 입구가 https면 자기 인증서(cert·key)로 mTLS. 서버 인증서는 랩 CA(ca)로 확인한다
- 토큰·비밀값·받는 사람 정보는 화면에 찍지 않는다(건수와 주문 번호만). 값은 모두 랩 전용 가짜 값이다.
- 다온마켓 밖으로는 아무 데도 부르지 않는다(base는 127.0.0.1만 받는다).
"""
from __future__ import annotations

import argparse
import json
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

# 랩 전용 가짜 값(DaonPartnerSchema의 시드와 같다)
SEED = {
    app: {"secret": f"lab-fake-partner-secret-{app}-v1", "api_key": f"lab-fake-partner-key-{app}"}
    for app in ("courier-01", "analytics-01", "legacy-01")
}


class PartnerError(Exception):
    def __init__(self, status: int, body: dict):
        super().__init__(f"HTTP {status} {body.get('error', '')}")
        self.status = status
        self.body = body


class PartnerApp:
    def __init__(self, base: str, client_id: str, client_secret: str | None = None, api_key: str | None = None,
                 cert: str | None = None, key: str | None = None, ca: str | None = None, timeout: float = 10.0):
        host = urllib.parse.urlsplit(base).hostname
        if host not in ("127.0.0.1", "localhost"):
            raise ValueError("파트너 앱 스텁은 이 PC의 랩(127.0.0.1)만 부릅니다")
        self.base = base.rstrip("/")
        self.client_id = client_id
        self.client_secret = client_secret or SEED.get(client_id, {}).get("secret")
        self.api_key = api_key or SEED.get(client_id, {}).get("api_key")
        self.cert, self.key, self.ca = cert, key, ca
        self.timeout = timeout
        self.mode: str | None = None          # "token" | "static"
        self.access_token: str | None = None
        self.scope: str | None = None
        self.calls = 0
        self.reissued = 0

    # ── HTTP ──
    def _ctx(self) -> ssl.SSLContext | None:
        if not self.base.startswith("https://"):
            return None
        ctx = ssl.create_default_context(cafile=self.ca) if self.ca else ssl._create_unverified_context()  # noqa: S323 (CA가 없을 때만)
        if self.cert and self.key:
            ctx.load_cert_chain(self.cert, self.key)
        return ctx

    def request(self, method: str, path: str, *, form: dict | None = None, headers: dict | None = None) -> tuple[int, dict]:
        data = urllib.parse.urlencode(form).encode() if form is not None else None
        h = {"Accept": "application/json", "User-Agent": "daon-partner-stub/1"}
        if form is not None:
            h["Content-Type"] = "application/x-www-form-urlencoded"
        h.update(headers or {})
        req = urllib.request.Request(self.base + path, data=data, headers=h, method=method)
        self.calls += 1
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=self._ctx()) as r:
                return r.status, _json(r.read())
        except urllib.error.HTTPError as e:
            return e.code, _json(e.read())

    # ── 토큰 ──
    def token(self, scope: str | None = None) -> str | None:
        """토큰 발급. 다온마켓에 발급 주소가 없으면(12-2 시작) 고정 키 방식으로 바꾸고 None."""
        form = {"grant_type": "client_credentials", "client_id": self.client_id, "client_secret": self.client_secret or ""}
        if scope:
            form["scope"] = scope
        status, body = self.request("POST", "/partner/token", form=form)
        if status in (404, 405) and self.mode != "token":      # 발급 주소 없음(405: 정적 자원 처리기가 POST를 거절)
            self.mode = "static"
            return None
        if status != 200:
            raise PartnerError(status, body)
        self.mode = "token"
        self.access_token = body.get("access_token")
        self.scope = body.get("scope")
        return self.access_token

    def _auth(self) -> dict:
        if self.mode is None:
            self.token()
        if self.mode == "static":
            return {"X-Partner-Key": self.api_key or ""}
        if not self.access_token:
            self.token()
        return {"Authorization": f"Bearer {self.access_token}"}

    def get(self, path: str) -> tuple[int, dict]:
        status, body = self.request("GET", path, headers=self._auth())
        if status == 401 and self.mode == "token" and body.get("error") == "invalid_token" \
                and body.get("error_description") == "토큰 만료":
            self.reissued += 1                # 짧은 수명: 만료되면 비밀값으로 다시 받는다
            self.token(self.scope if self.scope and self.scope != "orders:read:assigned" else None)
            status, body = self.request("GET", path, headers=self._auth())
        return status, body

    # ── 기능 ──
    def orders(self, page: int = 1, size: int = 50) -> dict:
        status, body = self.get(f"/partner/orders?page={page}&size={size}")
        if status != 200:
            raise PartnerError(status, body)
        return body

    def all_orders(self, size: int = 50, max_pages: int = 50) -> list[dict]:
        """쪽을 끝까지 넘긴다(쪽 수 상한 50: 랩 안전 장치)."""
        out: list[dict] = []
        for page in range(1, max_pages + 1):
            body = self.orders(page, size)
            items = body.get("items") or []
            out += items
            if not items or len(out) >= int(body.get("total") or 0):
                break
        return out

    def order(self, order_id: int) -> tuple[int, dict]:
        return self.get(f"/partner/orders/{int(order_id)}")

    def export(self) -> tuple[int, dict]:
        return self.get("/partner/orders/export")

    def rotate(self) -> str:
        status, body = self.request("POST", "/partner/credentials/rotate",
                                    form={"client_id": self.client_id, "client_secret": self.client_secret or ""})
        if status != 200:
            raise PartnerError(status, body)
        return body["client_secret"]


def _json(raw: bytes) -> dict:
    try:
        d = json.loads(raw.decode("utf-8") or "{}")
        return d if isinstance(d, dict) else {"data": d}
    except ValueError:
        return {"raw": raw[:200].decode("utf-8", "replace")}


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="가짜 파트너 앱(다온 배송 파트너). 랩 전용")
    p.add_argument("--base", default="http://127.0.0.1:8087", help="파트너 입구(12-2 시작이면 http://127.0.0.1:8085)")
    p.add_argument("--app", default="courier-01")
    p.add_argument("--cert"), p.add_argument("--key"), p.add_argument("--ca")
    p.add_argument("action", choices=["orders", "export", "token"])
    a = p.parse_args(argv)
    app = PartnerApp(a.base, a.app, cert=a.cert, key=a.key, ca=a.ca)
    try:
        if a.action == "orders":
            rows = app.all_orders()
            print(f"[{a.app}] 받은 주문 {len(rows)}건 (방식 {app.mode}, 요청 {app.calls}번). 주문 번호 앞 5개: {[r.get('id') for r in rows[:5]]}")
        elif a.action == "export":
            status, body = app.export()
            print(f"[{a.app}] 내보내기 HTTP {status}, 건수 {body.get('count', '-')} {body.get('error', '')}")
        else:
            app.token()
            print(f"[{a.app}] 방식 {app.mode}, 범위 {app.scope or '*(고정 키)'} (토큰 값은 찍지 않음)")
    except PartnerError as e:
        print(f"[{a.app}] 거절: HTTP {e.status} {e.body.get('error', '')} {e.body.get('error_description', '')}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
