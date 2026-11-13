"""T11 · 비밀번호 재설정: 링크 주소를 요청이 정하고, 인증 번호가 응답에 실려 나간다(A07). 수업 4-5.

127.0.0.1의 다온마켓만 부른다. 가짜 계정 alice(가짜 번호 010-0000-0101)로
  ① 링크: Host 값을 reset.example.test로 바꿔 재설정 링크를 요청하고, 가짜 메일함(Mailpit)에 온 메일 속 링크의 호스트를 본다.
  ② 인증 번호: /sso/getAuthCode 응답 본문에 6자리 번호가 실려 오는지 본다.
둘 중 하나라도 그렇다면 레드. 링크와 번호를 받기만 하고 비밀번호는 바꾸지 않는다.
"""
from __future__ import annotations

import re
import time
from urllib.parse import urlparse

import httpx

from replay import NotReady, Result
from replay._http import portal

T = "T11"
TITLE = "재설정 링크 주소를 요청이 정하고, 인증 번호가 응답에 실림"
LESSON = "4-5"
SOURCE = "[교육용 추가] 링크 재설정 + [원본 기본값] 인증 번호를 응답에 실음"
STACKS = ("spring",)

USER = "alice"
PHONE = "010-0000-0101"
FAKE_HOST = "reset.example.test"
MAILPIT = "http://127.0.0.1:8025"


def latest_link(to: str, after: float, timeout: float = 8.0) -> str | None:
    t0 = time.time()
    with httpx.Client(base_url=MAILPIT, timeout=5) as m:
        while time.time() - t0 < timeout:
            rows = m.get("/api/v1/search", params={"query": f"to:{to}", "limit": 5}).json().get("messages") or []
            for row in rows:
                created = row.get("Created", "")
                text = m.get(f"/api/v1/message/{row['ID']}").json().get("Text", "")
                links = re.findall(r"https?://\S+/reset\?token=\S+", text)
                if links and _ts(created) >= after - 1:
                    return links[0]
            time.sleep(0.5)
    return None


def _ts(iso: str) -> float:
    from datetime import datetime
    try:
        return datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp()
    except ValueError:
        return 0.0


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s4-05/express.diff).")
    started = time.time()
    with portal() as c:
        c.post("/sso/reset/link", data={"username": USER}, headers={"Host": FAKE_HOST})
        code_resp = c.get("/sso/getAuthCode", params={"telephone": PHONE}).json()
    link = latest_link(f"{USER}@daon.example", started)
    host = urlparse(link).netloc if link else None
    data = code_resp.get("data")
    leaked = bool(data and re.fullmatch(r"\d{6}", str(data)))
    ev = [f"① Host: {FAKE_HOST} 로 재설정 링크 요청 → 메일 속 링크 호스트: {host or '메일 없음'}",
          f"② GET /sso/getAuthCode?telephone=가림 → 응답 data: {'●●●●●● (6자리 번호가 실려 옴)' if leaked else '인증 번호 없음'}",
          f"   응답 문구: {code_resp.get('message')!r}",
          "비밀번호는 바꾸지 않았습니다"]
    problems = []
    if host and host.split(":")[0] == FAKE_HOST:
        problems.append("메일 링크가 요청이 보낸 호스트로 바뀜")
    if leaked:
        problems.append("인증 번호가 응답에 실려 옴")
    if problems:
        return Result(True, ", ".join(problems), ev)
    if not host:
        return Result(True, "재설정 메일이 오지 않았다(정상 흐름 확인 필요)", ev)
    return Result(False, f"링크는 {host}로 고정되고, 인증 번호는 문자로만 간다", ev)
