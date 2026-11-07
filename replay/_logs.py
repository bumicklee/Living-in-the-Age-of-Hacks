"""⑤ 탐지 확인: 앱 로그에서 보안 이벤트(로거 SECURITY, 한 줄 JSON) 찾기. 랩-설계.md 10절.

이 PC의 랩 컨테이너 로그만 읽는다(docker compose logs).
"""
from __future__ import annotations

import json
import time
from datetime import datetime, timedelta, timezone

from daon import stack as st


def now() -> str:
    """로그를 읽기 시작할 때(조금 앞당겨 컨테이너와 PC 시계 차이를 흡수)."""
    return (datetime.now(timezone.utc) - timedelta(seconds=3)).strftime("%Y-%m-%dT%H:%M:%SZ")


def security_events(service: str, since: str, stack: str = "spring") -> list[dict]:
    r = st.compose(stack, "logs", "--no-log-prefix", "--since", since, service, check=False, capture=True)
    out = []
    for line in (r.stdout or "").splitlines():
        if " SECURITY " not in line or "{" not in line:
            continue
        try:
            out.append(json.loads(line[line.index("{"):]))
        except ValueError:
            continue
    return out


def wait_event(service: str, since: str, event: str, lesson: str, stack: str = "spring", timeout: float = 5.0) -> dict | None:
    t0 = time.time()
    while True:
        for e in security_events(service, since, stack):
            if e.get("event") == event and e.get("lesson") == lesson:
                return e
        if time.time() - t0 > timeout:
            return None
        time.sleep(0.5)
