"""섹션 12 재연·시험 도우미: 파트너 입구(A9) 주소·인증서·날것 요청·토큰 내용 보기.

안전 기준(수업 계획 12장): 127.0.0.1의 랩만, 가짜 파트너·가짜 비밀값·랩 CA가 만든 인증서만 쓴다.
「훔친 토큰」 장면은 같은 PC 안에서 **다른 클라이언트 설정**(인증서 없음·다른 파트너 인증서)으로 같은 토큰을 내미는 수준이다.
토큰을 빼내는 방법·동의 피싱 화면은 다루지 않는다. 토큰·비밀값은 화면·로그에 찍지 않는다.
"""
from __future__ import annotations

import base64
import json
import ssl
from urllib.parse import quote

import httpx

from daon import partner as dp
from daon import stack as st
from daon.config import URLS
from replay import NotReady

COURIER, ANALYTICS, LEGACY = "courier-01", "analytics-01", "legacy-01"
CUSTOMER_PROBE = "/member/address/list"       # 로그인이 필요한 고객 API(파트너 토큰을 내밀면 401이어야 한다)


def base() -> str:
    return dp.base()


def on_gateway() -> bool:
    return dp.gateway_scheme() is not None


def require_a9() -> None:
    if not on_gateway():
        raise NotReady("파트너 입구(A9, 127.0.0.1:8087)가 없습니다. make goto L=12-2 AT=done 뒤 make up STACK=spring")


def app(app_id: str = COURIER, **kw):
    try:
        return dp.app(app_id, **kw)
    except OSError as e:
        raise NotReady(f"파트너 인증서를 읽지 못했습니다: {e}. make partner-certs")


def client(cert_of: str | None = None) -> httpx.Client:
    """파트너 입구로 부르는 httpx 클라이언트. cert_of: 그 파트너의 인증서로 mTLS(https일 때만), None이면 인증서 없이."""
    b = base()
    verify: ssl.SSLContext | bool = True
    if b.startswith("https://"):
        paths = dp.cert_paths(cert_of)
        ctx = ssl.create_default_context(cafile=paths["ca"])
        if cert_of:
            ctx.load_cert_chain(paths["cert"], paths["key"])
        verify = ctx
    return httpx.Client(base_url=b, timeout=10.0, verify=verify)


def get(path: str, token: str | None, *, cert_of: str | None = None, headers: dict | None = None) -> httpx.Response:
    h = dict(headers or {})
    if token:
        h["Authorization"] = f"Bearer {token}"
    try:
        with client(cert_of) as c:
            return c.get(path, headers=h)
    except httpx.TransportError as e:
        raise NotReady(f"파트너 주소({base()})에 닿지 않습니다: {e.__class__.__name__}. make up STACK=spring")


def token(app_id: str = COURIER, *, scope: str | None = None, cert_of: str | None = "self") -> str:
    """파트너 토큰 하나(발급 주소가 없으면 시작 지점 = 실패로 본다)."""
    a = app(app_id, cert_of=cert_of)
    try:
        t = a.token(scope)
    except Exception as e:  # PartnerError
        raise AssertionError(f"파트너 토큰 발급 실패: {e}") from None
    if t is None:
        raise AssertionError("파트너 토큰 발급 주소(POST /partner/token)가 없습니다(12-2 시작 지점: 고정 키만 있음)")
    return t


def payload(tok: str) -> dict:
    """JWT 내용(서명은 보지 않는다. 화면에 찍을 때는 cnf·scope·exp만)."""
    try:
        part = tok.split(".")[1]
        return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))
    except (IndexError, ValueError):
        return {}


def cert_header(app_id: str) -> str:
    """게이트웨이가 넘기는 모양(URL 인코딩 PEM)과 같은 헤더 값. 「밖에서 꾸며 넣은 헤더」 시험에만 쓴다."""
    paths = dp.cert_paths(app_id) if base().startswith("https://") else {}
    pem = open(paths["cert"]).read() if paths.get("cert") else "-----BEGIN CERTIFICATE-----\nZmFrZQ==\n-----END CERTIFICATE-----\n"
    return quote(pem, safe="")


def customer_get(path: str, tok: str, where: str = "portal") -> httpx.Response:
    """고객 입구로 부르기: portal(앱 8085)·web(고객 웹 8088 /api)·shop_gw(고객 API 입구 8086 /api)."""
    url = {"portal": URLS["spring"]["portal"] + path, "web": URLS["spring"]["web"] + "/api" + path,
           "shop_gw": URLS["spring"]["shop_gw"] + "/api" + path}[where]
    try:
        return httpx.get(url, headers={"Authorization": f"Bearer {tok}"}, timeout=10)
    except httpx.TransportError as e:
        raise NotReady(f"고객 입구({where})에 닿지 않습니다: {e.__class__.__name__}")


def assigned(app_id: str = COURIER) -> set[int]:
    ids = dp.assigned_ids(app_id)
    if not ids:
        raise NotReady("daon_partner_order 표가 비었습니다(앱이 뜰 때 DaonPartnerSchema가 만든다). make up STACK=spring")
    return ids


def unassigned_order(app_id: str = COURIER) -> int:
    mine = assigned(app_id)
    for oid in range(9004, 9301):
        if oid not in mine:
            return oid
    raise NotReady("배정 밖 주문을 찾지 못했습니다")


def skip_minutes(minutes: int) -> None:
    """랩 시계(LabClock, A13)를 민다. 4-8 시험과 같은 장치. 끝나면 reset_clock()."""
    st.redis_cli("SET", "lab:clock:offset", str(minutes * 60 * 1000))
    import time
    time.sleep(1.5)                 # 앱이 1초마다 다시 읽는다


def reset_clock() -> None:
    st.redis_cli("DEL", "lab:clock:offset")
    import time
    time.sleep(1.5)
