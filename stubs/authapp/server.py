"""가짜 「다온 인증 앱」 스텁(수업 4-7, 작성자 결정 2026-11-07 #14). 실제 앱이 아니라 받은 요청을 메모리에 쌓아 보여 준다.

POST /v1/push   {"to": "alice", "number": "42", "text": "다온마켓 재인증 요청"}  → 201
GET  /v1/push   (?to=alice)                                                   → 받은 요청(최근 100개, JSON)
GET  /          받은 요청을 화면으로(인증 앱 화면 대신, 영상 캡처용)
GET  /health
번호 일치 방식: 다온마켓 서버가 두 자리 숫자를 이 앱으로 보내고, 사용자는 앱에 뜬 숫자를 고객 웹에 입력한다.
받는 사람은 랩의 가짜 계정 이름(영문 소문자·숫자·밑줄, 32자 이하)만 받는다.
"""
from __future__ import annotations

import html
import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

USER = re.compile(r"^[a-z0-9_]{1,32}$")
PUSHES: list[dict] = []
LOCK = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    server_version = "daon-fake-authapp/1"

    def log_message(self, fmt, *args):  # 접속 기록은 남기지 않는다
        pass

    def reply(self, code: int, data, ctype="application/json; charset=utf-8") -> None:
        body = data.encode() if isinstance(data, str) else json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802
        u = urlparse(self.path)
        if u.path == "/health":
            return self.reply(200, {"ok": True})
        to = parse_qs(u.query).get("to", [None])[0]
        with LOCK:
            rows = [p for p in PUSHES if not to or p["to"] == to][-100:]
        if u.path == "/v1/push":
            return self.reply(200, rows)
        if u.path == "/":
            cards = "".join(
                f"<div class=c><div class=t>{html.escape(p['text'])}</div><div class=n>{html.escape(p['number'])}</div>"
                f"<div class=m>{html.escape(p['to'])} · {time.strftime('%H:%M:%S', time.localtime(p['ts']))}</div></div>"
                for p in reversed(rows[-6:])) or "<p class=e>받은 요청이 없습니다</p>"
            page = ("<!doctype html><meta charset=utf-8><title>다온 인증 앱(가짜)</title><style>"
                    "body{margin:0;font-family:'Pretendard','Apple SD Gothic Neo',sans-serif;background:#0f172a;color:#e2e8f0}"
                    "h1{font-size:22px;margin:0;padding:18px 24px;background:#1e293b}.w{padding:20px 24px;display:grid;gap:14px;max-width:520px}"
                    ".c{background:#1e293b;border-radius:16px;padding:18px 22px}.t{color:#94a3b8;font-size:16px}"
                    ".n{font-size:64px;font-weight:800;letter-spacing:8px;color:#facc15;margin:6px 0}.m{color:#64748b;font-size:14px}"
                    ".e{color:#64748b}.b{background:#fde68a;color:#78350f;text-align:center;font-size:13px;padding:6px}</style>"
                    "<div class=b>교육용 랩 · 127.0.0.1 · 값은 가짜</div><h1>다온 인증 앱(가짜)</h1><div class=w>" + cards + "</div>")
            return self.reply(200, page, "text/html; charset=utf-8")
        self.reply(404, {"error": "없는 주소"})

    def do_POST(self):  # noqa: N802
        if urlparse(self.path).path != "/v1/push":
            return self.reply(404, {"error": "없는 주소"})
        try:
            n = int(self.headers.get("Content-Length") or 0)
            d = json.loads(self.rfile.read(min(n, 8 * 1024)) or b"{}")
        except (ValueError, json.JSONDecodeError):
            return self.reply(400, {"error": "JSON 본문이 필요합니다"})
        to, number, text = str(d.get("to", "")), str(d.get("number", "")), str(d.get("text", "재인증 요청"))
        if not USER.match(to) or not re.fullmatch(r"\d{2}", number) or len(text) > 200:
            return self.reply(400, {"error": "to는 가짜 계정 이름, number는 두 자리 숫자입니다"})
        with LOCK:
            p = {"id": len(PUSHES) + 1, "to": to, "number": number, "text": text, "ts": int(time.time())}
            PUSHES.append(p)
        self.reply(201, p)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
