"""가짜 문자(SMS) 발송사 스텁. 실제로 보내지 않고 받은 문자를 메모리에 쌓아 보여 준다.

POST /v1/messages   {"to": "010-0000-0101", "text": "인증번호 123456"}  → 201
GET  /v1/messages   (?to=010-0000-0101)                               → 받은 문자(최근 200개)
GET  /health
받는 번호는 랩의 가짜 번호(010-0000-xxxx)만 받는다. 그 밖의 번호는 400.

본인 확인(문자 사업자의 「인증 번호 확인 API」 흉내, 9-2 T22)
POST /v1/verify/start  {"to": "010-0000-0101"}                  → 202, 여섯 자리 번호를 문자 목록에 쌓는다
POST /v1/verify/check  {"to": "010-0000-0101", "code": "123456"} → 200 {"valid": true|false} (번호는 한 번만 맞는다, 5분)

랩 도구(장애 넣기, make stub-fault S=sms MODE=500|slow|ok [MS=3000])
POST /_lab/fault  {"mode": "ok"|"500"|"slow", "ms": 3000}  → /v1/ 아래 모든 요청에 장애(500 응답, 또는 ms만큼 늦게 답함, 최대 5000)
GET  /_lab/fault                                           → 지금 장애 모드
GET  /_lab/stats                                           → 쌓인 문자 수와 형식이 틀려 거절한 수(10-7 문자 예산 재연). 번호 값은 주지 않는다
"""
from __future__ import annotations

import json
import random
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

FAKE = re.compile(r"^010-0000-\d{4}$")
MESSAGES: list[dict] = []
CODES: dict[str, tuple[str, float]] = {}      # 번호 → (인증 번호, 만든 시각)
FAULT = {"mode": "ok", "ms": 0}
LOCK = threading.Lock()
REJECTED = {"count": 0}                        # 형식이 틀려 거절한 발송 요청 수(10-7: 앱이 거르지 않고 그대로 부른 흔적)


class Handler(BaseHTTPRequestHandler):
    server_version = "daon-fake-sms/1"

    def reply(self, code: int, data) -> None:
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def fault(self) -> bool:
        """장애 모드면 그대로 답하고 True(500), 늦게 답할 때는 기다린 뒤 False(정상 처리)."""
        mode, ms = FAULT["mode"], FAULT["ms"]
        if mode == "500":
            self.reply(500, {"error": "랩 장애 모드(500)"})
            return True
        if mode == "slow":
            time.sleep(min(ms, 5000) / 1000)
        return False

    def read_json(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(min(n, 16 * 1024)) or b"{}")

    def do_GET(self):  # noqa: N802
        u = urlparse(self.path)
        if u.path == "/health":
            return self.reply(200, {"ok": True})
        if u.path == "/_lab/fault":
            return self.reply(200, FAULT)
        if u.path == "/_lab/stats":
            with LOCK:
                return self.reply(200, {"messages": len(MESSAGES), "last_id": MESSAGES[-1]["id"] if MESSAGES else 0,
                                        "rejected_format": REJECTED["count"]})
        if u.path.startswith("/v1/") and self.fault():
            return
        if u.path == "/v1/messages":
            to = parse_qs(u.query).get("to", [None])[0]
            with LOCK:
                rows = [m for m in MESSAGES if not to or m["to"] == to]
            return self.reply(200, rows[-200:])
        self.reply(404, {"error": "없는 주소"})

    def do_POST(self):  # noqa: N802
        path = urlparse(self.path).path
        try:
            d = self.read_json()
            if not isinstance(d, dict):
                raise ValueError("객체가 아님")
        except (ValueError, json.JSONDecodeError):
            return self.reply(400, {"error": "JSON 본문이 필요합니다"})
        if path == "/_lab/fault":
            mode = str(d.get("mode", "ok"))
            if mode not in ("ok", "500", "slow") or not str(d.get("ms") or 0).isdigit():
                return self.reply(400, {"error": "mode는 ok·500·slow, ms는 0~5000"})
            FAULT.update(mode=mode, ms=max(0, min(int(d.get("ms") or 0), 5000)))
            return self.reply(200, FAULT)
        if path.startswith("/v1/") and self.fault():
            return
        if path in ("/v1/verify/start", "/v1/verify/check"):
            return self.verify(path, d)
        if path != "/v1/messages":
            return self.reply(404, {"error": "없는 주소"})
        to, text = str(d.get("to", "")), str(d.get("text", ""))
        if not FAKE.match(to):
            with LOCK:
                REJECTED["count"] += 1
            return self.reply(400, {"error": "랩에서는 가짜 번호(010-0000-xxxx)로만 보냅니다"})
        if not text or len(text) > 500:
            return self.reply(400, {"error": "text는 1~500자입니다"})
        with LOCK:
            m = {"id": len(MESSAGES) + 1, "to": to, "text": text, "ts": int(time.time())}
            MESSAGES.append(m)
        self.reply(201, m)

    def verify(self, path: str, d: dict) -> None:
        to = str(d.get("to", ""))
        if not FAKE.match(to):
            return self.reply(400, {"error": "랩에서는 가짜 번호(010-0000-xxxx)로만 보냅니다"})
        if path == "/v1/verify/start":
            code = f"{random.randint(0, 999999):06d}"
            with LOCK:
                CODES[to] = (code, time.time())
                MESSAGES.append({"id": len(MESSAGES) + 1, "to": to, "text": f"[다온마켓] 인증번호 {code}", "ts": int(time.time())})
            return self.reply(202, {"to": to, "status": "sent"})
        code = str(d.get("code", ""))
        with LOCK:
            want = CODES.get(to)
            ok = bool(want) and want[0] == code and time.time() - want[1] < 300
            if ok:
                CODES.pop(to, None)                 # 한 번만 맞는다
        self.reply(200, {"to": to, "valid": ok})


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
