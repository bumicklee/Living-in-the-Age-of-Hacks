"""가짜 결제사(PG) 스텁. 실제 돈·카드 번호는 다루지 않는다. 표준 라이브러리만 쓴다.

POST /v1/payments            {"order_sn": "...", "amount": 39000, "callback_url": "http://…/webhook"}
                             → 바로 승인하고 callback_url로 웹훅을 보낸다(서명 헤더 X-Daon-Signature)
POST /v1/payments/<id>/resend → 같은 웹훅을 한 번 더(중복 웹훅·멱등성 수업, 10장)
GET  /v1/payments            → 지금까지의 결제
GET  /v1/webhooks            → 보낸 웹훅과 응답 코드
GET  /health
서명: hex(HMAC-SHA256(PAYMENT_WEBHOOK_SECRET, 본문 바이트)). 비밀값은 랩 전용 가짜 값(compose의 환경 변수).
"""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SECRET = os.environ.get("PAYMENT_WEBHOOK_SECRET", "lab-fake-webhook-secret").encode()
PAYMENTS: dict[str, dict] = {}
WEBHOOKS: list[dict] = []
LOCK = threading.Lock()


def send_webhook(p: dict) -> None:
    if not p.get("callback_url"):
        return
    body = json.dumps({"event": "payment.approved", "payment_id": p["payment_id"], "order_sn": p["order_sn"],
                       "amount": p["amount"], "ts": int(time.time())}, ensure_ascii=False).encode()
    sig = hmac.new(SECRET, body, hashlib.sha256).hexdigest()
    req = urllib.request.Request(p["callback_url"], data=body, method="POST",
                                 headers={"Content-Type": "application/json", "X-Daon-Signature": sig})
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            code = r.status
    except Exception as e:  # noqa: BLE001 - 받는 쪽이 없으면 기록만 한다
        code = getattr(e, "code", None) or f"{type(e).__name__}"
    with LOCK:
        WEBHOOKS.append({"payment_id": p["payment_id"], "url": p["callback_url"], "status": code, "ts": int(time.time())})


class Handler(BaseHTTPRequestHandler):
    server_version = "daon-fake-pg/1"

    def reply(self, code: int, data) -> None:
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_json(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        if n > 64 * 1024:
            raise ValueError("본문이 너무 큽니다")
        return json.loads(self.rfile.read(n) or b"{}")

    def do_GET(self):  # noqa: N802
        if self.path == "/health":
            return self.reply(200, {"ok": True})
        if self.path == "/v1/payments":
            with LOCK:
                return self.reply(200, list(PAYMENTS.values()))
        if self.path == "/v1/webhooks":
            with LOCK:
                return self.reply(200, WEBHOOKS[-200:])
        self.reply(404, {"error": "없는 주소"})

    def do_POST(self):  # noqa: N802
        try:
            if self.path == "/v1/payments":
                d = self.read_json()
                if not d.get("order_sn") or not isinstance(d.get("amount"), (int, float)) or d["amount"] <= 0:
                    return self.reply(400, {"error": "order_sn과 amount(양수)가 필요합니다"})
                with LOCK:
                    pid = f"PAY{len(PAYMENTS) + 1:06d}"
                    p = {"payment_id": pid, "order_sn": str(d["order_sn"]), "amount": d["amount"], "status": "approved",
                         "callback_url": d.get("callback_url"), "ts": int(time.time())}
                    PAYMENTS[pid] = p
                threading.Thread(target=send_webhook, args=(p,), daemon=True).start()
                return self.reply(201, {k: v for k, v in p.items() if k != "callback_url"})
            parts = self.path.strip("/").split("/")
            if len(parts) == 4 and parts[:2] == ["v1", "payments"] and parts[3] == "resend":
                with LOCK:
                    p = PAYMENTS.get(parts[2])
                if not p:
                    return self.reply(404, {"error": "없는 결제"})
                threading.Thread(target=send_webhook, args=(p,), daemon=True).start()
                return self.reply(202, {"resent": p["payment_id"]})
            self.reply(404, {"error": "없는 주소"})
        except (ValueError, json.JSONDecodeError) as e:
            self.reply(400, {"error": str(e)})


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
