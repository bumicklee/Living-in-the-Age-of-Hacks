"""가짜 택배사(다온택배) 배송 조회 스텁. 시드의 송장 번호(DT + 10자리)에 늘 같은 답을 준다.

GET /v1/tracking/<송장 번호>   → {"invoice": "...", "status": "배송 완료", "events": [...]}
GET /health
송장 번호 끝자리로 단계를 정한다(0~2 접수, 3~4 집하, 5~6 배송 중, 7~9 배송 완료). 형식이 다르면 404.
"""
from __future__ import annotations

import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

INVOICE = re.compile(r"^DT\d{10}$")
STEPS = ["접수", "집하", "배송 중", "배송 완료"]
PLACES = ["다온 물류센터(가상)", "가상 허브(가짜)", "예시 대리점(가짜)", "받는 곳"]


def track(invoice: str) -> dict:
    last = int(invoice[-1])
    upto = 0 if last <= 2 else 1 if last <= 4 else 2 if last <= 6 else 3
    events = [{"step": STEPS[i], "place": PLACES[i], "at": f"2026-11-0{i + 1} 1{i}:00"} for i in range(upto + 1)]
    return {"invoice": invoice, "company": "다온택배", "status": STEPS[upto], "events": events}


class Handler(BaseHTTPRequestHandler):
    server_version = "daon-fake-courier/1"

    def reply(self, code: int, data) -> None:
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802
        if self.path == "/health":
            return self.reply(200, {"ok": True})
        if self.path.startswith("/v1/tracking/"):
            inv = self.path.rsplit("/", 1)[1]
            if INVOICE.match(inv):
                return self.reply(200, track(inv))
            return self.reply(404, {"error": "없는 송장 번호"})
        self.reply(404, {"error": "없는 주소"})


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
