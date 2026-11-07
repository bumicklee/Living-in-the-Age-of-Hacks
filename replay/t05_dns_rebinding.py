"""T05 · 로컬 관리 도구가 Host 확인 없이 응답(DNS 리바인딩, P5). 수업 2-6.

DNS 리바인딩이 성공하면 브라우저는 공격자 이름(rebind.attacker.example)으로 이 PC의 127.0.0.1:8095에 요청을 보낸다.
이때 요청의 Host 헤더에는 공격자 이름이 그대로 실린다. 이 재연은 브라우저·DNS 없이 그 요청 하나만 똑같이 만든다:
127.0.0.1:8095에 Host: rebind.attacker.example:8095를 달아 보낸다. 설정 값이 돌아오면 레드.
"""
from __future__ import annotations

import httpx

from daon.config import HOST, port
from replay import NotReady, Result

T = "T05"
TITLE = "로컬 관리 도구가 낯선 Host에도 응답(DNS 리바인딩)"
LESSON = "2-6"
SOURCE = "[교육용 추가] 다온 운영 도구(8095, 로그인 없음)"
STACKS = ("spring",)

PATH = "/ops/config"


def ops_port() -> int:
    return port("DAON_OPS_PORT", 8095)


def ops_get(host_header: str | None, path: str = PATH) -> httpx.Response:
    headers = {"Host": host_header} if host_header else {}
    try:
        return httpx.get(f"http://{HOST}:{ops_port()}{path}", headers=headers, timeout=5.0)
    except httpx.TransportError as e:
        raise NotReady(f"운영 도구({HOST}:{ops_port()})에 닿지 않습니다. make up STACK=spring 먼저 실행하세요. ({e.__class__.__name__})")


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s2-06/express.diff로 확인).")
    evil = f"rebind.attacker.example:{ops_port()}"
    r = ops_get(evil)
    ev = [f"요청: GET http://{HOST}:{ops_port()}{PATH}  Host: {evil}  (리바인딩된 브라우저가 보내는 모양)",
          f"응답: HTTP {r.status_code} {r.text[:120]!r}"]
    if r.status_code == 200 and "daon-ops" in r.text:
        return Result(True, "운영 도구가 공격자 이름으로 온 요청에도 설정 값을 돌려준다", ev)
    return Result(False, "운영 도구는 허용하지 않은 Host를 거부한다", ev)
