"""도구(make kev·upstream-status·check_pins·deploy)가 보안 이벤트를 남기는 곳. 랩-설계.md 10절.

앱의 SECURITY 로그와 같은 키의 한 줄 JSON을 화면에 찍고, reports/security-events.jsonl 에도 덧붙인다(8-4에서 수집기가 읽는다).
reports/ 는 git에 넣지 않는다. DAON_REPORTS_DIR 로 다른 곳을 고를 수 있다(시험이 쓴다).
값(토큰·비밀번호·개인정보)은 넣지 않는다. target에는 이름·주소·번호만 넣는다.
"""
from __future__ import annotations

import json
import os
import time
from datetime import datetime
from pathlib import Path


def now() -> "datetime":
    """지금 시각. DAON_FAKE_NOW=+74h(또는 +3d, 또는 2026-11-12T09:00)이면 시계를 돌린 시각(7-3 영상·시험용, 랩 전용)."""
    import re
    from datetime import datetime, timedelta
    real = datetime.now().astimezone()
    v = (os.environ.get("DAON_FAKE_NOW") or "").strip()
    if not v:
        return real
    m = re.fullmatch(r"([+-])(\d+(?:\.\d+)?)([hdm])", v)
    if m:
        n = float(m.group(2)) * (1 if m.group(1) == "+" else -1)
        return real + {"h": timedelta(hours=n), "d": timedelta(days=n), "m": timedelta(minutes=n)}[m.group(3)]
    return datetime.fromisoformat(v).astimezone()


def reports_dir() -> Path:
    return Path(os.environ.get("DAON_REPORTS_DIR") or Path(__file__).resolve().parent.parent / "reports")


def emit(event: str, lesson: str, actor: str, target: str, reason: str, *, count: int = 1, level: str = "WARN", echo: bool = True) -> dict:
    line = {"event": event, "lesson": lesson, "actor": actor, "target": target, "ip": None, "ua": None,
            "reason": reason, "count": count, "ts": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "trace_id": None}
    text = json.dumps(line, ensure_ascii=False)
    try:
        d = reports_dir()
        d.mkdir(parents=True, exist_ok=True)
        with (d / "security-events.jsonl").open("a", encoding="utf-8") as f:
            f.write(text + "\n")
    except OSError:
        pass                                  # 파일에 못 써도 화면 줄은 남긴다(CI 안에서는 로그가 기록이다)
    if echo:
        print(f"{level} SECURITY {text}")
    return line


def read(event: str | None = None) -> list[dict]:
    p = reports_dir() / "security-events.jsonl"
    out = []
    if p.exists():
        for ln in p.read_text("utf-8").splitlines():
            try:
                d = json.loads(ln)
            except ValueError:
                continue
            if event is None or d.get("event") == event:
                out.append(d)
    return out


if __name__ == "__main__":      # 셸 스크립트(deploy.sh)가 쓴다: python tools/secevent.py emit <event> <lesson> <actor> <target> <reason> [INFO|WARN]
    import sys
    if len(sys.argv) >= 7 and sys.argv[1] == "emit":
        emit(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6], level=sys.argv[7] if len(sys.argv) > 7 else "WARN")
    else:
        sys.exit("사용법: secevent.py emit <event> <lesson> <actor> <target> <reason> [INFO|WARN]")
