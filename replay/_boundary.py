"""섹션 11 재연·시험이 같이 쓰는 도우미: 게이트웨이 고객 API 입구(A7, 8086), 날것의 HTTP 요청 한 번, 캐시 비우기.

안전 기준(수업 계획 11장):
  - 요청 경계 「방어 확인」 요청은 세 가지뿐이다(길이 표시 둘, Content-Length 두 줄, 접힌 헤더 줄).
    길이 표시 둘은 두 해석(Content-Length·chunked)이 **같은 바이트에서 끝나게** 만든다. 본문은 끝 표시(0 청크) 하나뿐이고
    안에 다른 요청이 들어 있지 않다. 「거절되는가」와 「이벤트가 남는가」만 본다.
  - 127.0.0.1의 랩에만 보낸다. 연결마다 요청 하나, 응답을 다 읽으면 닫는다.
"""
from __future__ import annotations

import socket
import uuid

from daon.config import HOST, port
from replay import NotReady

SHOP_PORT = port("DAON_GATEWAY_SHOP_PORT", 8086)
SHOP = f"http://{HOST}:{SHOP_PORT}"


def gateway_up() -> bool:
    with socket.socket() as s:
        s.settimeout(1.0)
        return s.connect_ex((HOST, SHOP_PORT)) == 0


def require_gateway() -> None:
    if not gateway_up():
        raise NotReady(f"게이트웨이 고객 API 입구({SHOP})가 없습니다. A7은 11-3 완료 지점부터입니다(make goto L=11-3 AT=done && make up)")


def tag(prefix: str) -> str:
    """요청마다 다른 User-Agent. 게이트웨이 이벤트 줄을 이 값으로 찾는다."""
    return f"daon-lab-{prefix}-{uuid.uuid4().hex[:10]}"


def raw(head_lines: list[str], body: bytes = b"", timeout: float = 5.0) -> tuple[int | None, str]:
    """요청 한 번을 바이트 그대로 보낸다(httpx는 이상한 헤더를 고쳐 보내므로). 응답의 (상태 코드, 머리) 를 돌려준다."""
    data = ("\r\n".join(head_lines) + "\r\n\r\n").encode("latin-1") + body
    try:
        with socket.create_connection((HOST, SHOP_PORT), timeout=timeout) as s:
            s.sendall(data)
            chunks = []
            while True:
                try:
                    b = s.recv(65536)
                except socket.timeout:
                    break
                if not b:
                    break
                chunks.append(b)
                if sum(map(len, chunks)) > 256 * 1024:
                    break
    except OSError as e:
        raise NotReady(f"게이트웨이({SHOP})에 닿지 않습니다: {e.__class__.__name__}")
    text = b"".join(chunks).decode("latin-1", "replace")
    head = text.split("\r\n\r\n", 1)[0]
    first = head.split("\r\n", 1)[0]
    try:
        return int(first.split()[1]), head
    except (IndexError, ValueError):
        return None, head


def host_line() -> str:
    return f"Host: {HOST}:{SHOP_PORT}"


# ── 방어 확인 요청 세 가지(본문에 다른 요청 없음) ──
def probe_two_lengths(ua: str) -> int | None:
    """Content-Length와 Transfer-Encoding이 함께. 두 해석 모두 같은 곳(0 청크 끝)에서 끝난다."""
    body = b"0\r\n\r\n"
    return raw(["POST /api/sso/login HTTP/1.1", host_line(), f"User-Agent: {ua}",
                "Content-Type: application/x-www-form-urlencoded", f"Content-Length: {len(body)}",
                "Transfer-Encoding: chunked", "Connection: close"], body)[0]


def probe_duplicate_length(ua: str) -> int | None:
    """Content-Length 두 줄(값은 같은 0). 본문 없음."""
    return raw(["POST /api/sso/login HTTP/1.1", host_line(), f"User-Agent: {ua}",
                "Content-Type: application/x-www-form-urlencoded", "Content-Length: 0", "Content-Length: 0",
                "Connection: close"])[0]


def probe_folded_header(ua: str) -> int | None:
    """옛 문법의 접힌 헤더 줄(공백으로 시작하는 이어 쓰기 줄). 본문 없음."""
    return raw(["GET /api/product/search?pageNum=1&pageSize=5 HTTP/1.1", host_line(), f"User-Agent: {ua}",
                "X-Daon-Note: first", " second", "Connection: close"])[0]

