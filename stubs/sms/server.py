"""가짜 문자(SMS) 발송사 스텁. 실제로 보내지 않고 받은 문자를 메모리에 쌓아 보여 준다.

POST /v1/messages   {"to": "010-0000-0101", "text": "인증번호 123456"}  → 201
GET  /v1/messages   (?to=010-0000-0101)                               → 받은 문자(최근 200개)
GET  /health
받는 번호는 랩의 가짜 번호(010-0000-xxxx)만 받는다. 그 밖의 번호는 400.
"""
from __future__ import annotations

import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

FAKE = re.compile(r"^010-0000-\d{4}$")
MESSAGES: list[dict] = []
LOCK = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    server_version = "daon-fake-sms/1"

    def reply(self, code: int, data) -> None:
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802
        u = urlparse(self.path)
        if u.path == "/health":
            return self.reply(200, {"ok": True})
        if u.path == "/v1/messages":
            to = parse_qs(u.query).get("to", [None])[0]
            with LOCK:
                rows = [m for m in MESSAGES if not to or m["to"] == to]
            return self.reply(200, rows[-200:])
        self.reply(404, {"error": "없는 주소"})

    def do_POST(self):  # noqa: N802
        if urlparse(self.path).path != "/v1/messages":
            return self.reply(404, {"error": "없는 주소"})
        try:
            n = int(self.headers.get("Content-Length") or 0)
            d = json.loads(self.rfile.read(min(n, 16 * 1024)) or b"{}")
        except (ValueError, json.JSONDecodeError):
            return self.reply(400, {"error": "JSON 본문이 필요합니다"})
        to, text = str(d.get("to", "")), str(d.get("text", ""))
        if not FAKE.match(to):
            return self.reply(400, {"error": "랩에서는 가짜 번호(010-0000-xxxx)로만 보냅니다"})
        if not text or len(text) > 500:
            return self.reply(400, {"error": "text는 1~500자입니다"})
        with LOCK:
            m = {"id": len(MESSAGES) + 1, "to": to, "text": text, "ts": int(time.time())}
            MESSAGES.append(m)
        self.reply(201, m)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
