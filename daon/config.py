"""랩 경로와 주소. 포트는 infra/.env(없으면 infra/.env.example)에서 읽는다."""
from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INFRA = ROOT / "infra"
DATA = ROOT / ".daon"          # 진도·빌드 도장 등 랩 상태(git에 넣지 않음)
STACKS = ("spring", "express")


def _env() -> dict[str, str]:
    out: dict[str, str] = {}
    for name in (".env.example", ".env"):          # .env가 .env.example을 덮는다
        p = INFRA / name
        if p.exists():
            for line in p.read_text("utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    out[k.strip()] = v.strip()
    out.update({k: v for k, v in os.environ.items() if k.startswith("DAON_")})
    return out


ENV = _env()


def port(name: str, default: int) -> int:
    return int(ENV.get(name, default))


HOST = "127.0.0.1"   # 재연·시험은 이 PC의 랩만 부른다

URLS = {
    "spring": {
        "portal": f"http://{HOST}:{port('DAON_PORTAL_API_PORT', 8085)}",
        "admin": f"http://{HOST}:{port('DAON_ADMIN_API_PORT', 8080)}",
        "admin_web": f"http://{HOST}:{port('DAON_ADMIN_WEB_PORT', 8090)}",
        "shop_gw": f"http://{HOST}:{port('DAON_GATEWAY_SHOP_PORT', 8086)}",     # A7(11-3): 게이트웨이 고객 API 입구(/api/ → Varnish → mall-portal)
        "partner_gw": f"{HOST}:{port('DAON_GATEWAY_PARTNER_PORT', 8087)}",      # A9(12-2): 파트너 입구. 12-2는 http, 12-3부터 https(daon.partner.base()가 고른다)
        "web": f"http://{HOST}:{port('DAON_WEB_PORT', 8088)}",                   # 고객 웹(/api/ → mall-portal)
        "approval": f"http://{HOST}:{port('DAON_APPROVAL_PORT', 8097)}",         # A10(13-3): 승인 서비스(시험·재연·두 번째 승인자)
        "payment": f"http://{HOST}:{port('DAON_PAYMENT_PORT', 9101)}",           # 가짜 결제사(13장 환불 목록 /v1/refunds)
    },
    "express": {
        "shop": f"http://{HOST}:{port('DAON_EVERSHOP_PORT', 3000)}",
    },
}


def current_stack() -> str:
    s = os.environ.get("STACK") or os.environ.get("DAON_STACK") or "spring"
    if s not in STACKS:
        raise SystemExit(f"STACK은 spring 또는 express입니다: {s}")
    return s
