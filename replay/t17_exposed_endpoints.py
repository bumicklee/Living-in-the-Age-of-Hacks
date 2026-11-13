"""T17 · 로그인 없이 관리용 엔드포인트·API 문서 화면에 닿는다(점검만, 읽기 전용). 수업 6-5.

127.0.0.1의 두 앱(고객 API·관리자 API)에 로그인 없이 관리·문서 경로 목록을 한 번씩 요청해 응답 코드 표만 만든다.
열리는 경로가 하나라도 있으면 레드. 이 앱은 로그인 요구를 HTTP 200 + 본문 code 401로 돌려주므로, 본문 앞 2KB에서 code 401·403만 확인한다(본문은 저장·출력하지 않는다).
서버 오류 응답의 문구(F31)는 정적 근거라 단위 시험(GlobalExceptionHandlerTest)과 코드 화면으로 본다.
"""
from __future__ import annotations

import httpx

from daon.config import URLS
from replay import NotReady, Result

T = "T17"
TITLE = "관리용 엔드포인트·API 문서 화면이 로그인 없이 열림"
LESSON = "6-5"
SOURCE = "[원본 기본값]+[원본 확인됨]"
STACKS = ("spring",)

PATHS = ["/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config", "/actuator", "/actuator/health", "/actuator/env", "/actuator/heapdump", "/druid/index.html"]


def probe() -> dict[str, dict[str, int]]:
    out: dict[str, dict[str, int]] = {}
    for app in ("portal", "admin"):
        base = URLS["spring"][app]
        codes = {}
        try:
            with httpx.Client(base_url=base, timeout=5.0, follow_redirects=True) as c:
                for p in PATHS:
                    with c.stream("GET", p) as r:           # 본문 앞 2KB에서 「로그인 필요」 표시만 본다
                        head = next(r.iter_bytes(2048), b"")
                        denied = b'"code":401' in head or b'"code":403' in head
                        codes[p] = 401 if (r.status_code < 300 and denied) else r.status_code
        except httpx.TransportError as e:
            raise NotReady(f"{app}({base})에 닿지 않습니다. make up STACK=spring 먼저 실행하세요. ({e.__class__.__name__})")
        out[app] = codes
    return out


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다.")
    table = probe()
    open_paths = [(app, p, code) for app, codes in table.items() for p, code in codes.items() if 200 <= code < 300]
    ev = [f"{app:<6} {p:<22} → {code}" for app, codes in table.items() for p, code in codes.items()]
    if open_paths:
        return Result(True, f"로그인 없이 열린 관리·문서 경로 {len(open_paths)}곳", ev)
    return Result(False, "관리·문서 경로가 모두 닫혀 있다(2xx 없음)", ev)
