"""가짜 결제사(PG) 스텁. 실제 돈·카드 번호는 다루지 않는다. 표준 라이브러리만 쓴다.

POST /v1/payments            {"order_sn": "...", "amount": 39000, "callback_url": "http://…/webhook", "card_token": "tok_test_4242"}
                             → 승인하면 callback_url로 웹훅을 보낸다(서명 헤더 X-Daon-Signature)
                             card_token(선택, 10-6)은 랩의 가짜 테스트 카드 토큰(tok_test_ + 숫자 4~16자리)이다. 실제 카드 번호는 받지 않는다.
                               끝자리가 2 또는 4면 승인, 그 밖은 거절(HTTP 402 {"status":"declined"}). 토큰이 없으면 늘 승인(9장 결제 요청).
                               응답에 가짜 카드 지문 card_fingerprint(= "fp_" + 토큰 SHA-256 앞 16자)를 준다. 같은 토큰이면 같은 지문
POST /v1/payments/<id>/resend → 같은 웹훅을 한 번 더(중복 웹훅·멱등성 수업, 10장)
GET  /v1/payments            → 지금까지의 결제
GET  /v1/payments/by-order/<주문 번호> → 그 주문의 마지막 결제 상태(14장 결제 확인). 느림 모드면 이것도 늦게 답한다(14-3)
GET  /v1/webhooks            → 보낸 웹훅과 응답 코드
POST /v1/refunds             {"order_sn": "...", "amount": 10000, "return_id": 31, "approval": "<1회용 번호 또는 빈 값>"}  (13장)
                             → 환불 한 건을 기록만 한다(진짜 돈은 없다). 같은 결제사 쪽 상한 확인은 하지 않는다: 금액을 판단하는 것은 다온마켓 쪽이다
GET  /v1/refunds             → 지금까지의 환불(JSON). ?return_id=31 이면 그 반품 신청 것만
GET  /_stub/refunds          → 같은 목록을 사람이 보는 표(HTML, 13-3 화면 캡처용)
GET  /health
서명: hex(HMAC-SHA256(PAYMENT_WEBHOOK_SECRET, 본문 바이트)). 비밀값은 랩 전용 가짜 값(compose의 환경 변수).

랩 도구(9-4·9-5 재시도 폭주 측정, make stub-fault S=pay · make measure M=retry)
POST /_lab/fault  {"mode": "ok"|"500"|"slow", "ms": 3000}  → POST /v1/payments에 장애(500, 또는 ms만큼 늦게 답함, 최대 5000). slow는 결제 확인(by-order)에도(14-3)
GET  /_lab/fault                                           → 지금 장애 모드
GET  /_lab/stats                                           → 받은 결제 요청 수와 최근 2000건(시각·X-Trace-Id·Idempotency-Key·응답 코드)
POST /_lab/reset                                           → 받은 요청 기록만 비운다(결제 목록은 그대로)
늦게 답하는 모드에서도 결제는 끝까지 처리한다(부른 쪽이 먼저 끊었어도 결제사는 처리했을 수 있다: 10-4 멱등 키의 이유).
"""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import re
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SECRET = os.environ.get("PAYMENT_WEBHOOK_SECRET", "lab-fake-webhook-secret").encode()
PAYMENTS: dict[str, dict] = {}
WEBHOOKS: list[dict] = []
FAULT = {"mode": "ok", "ms": 0}
SEEN: list[dict] = []           # 받은 결제 요청(측정용). 값은 가짜 주문 번호·추적 번호뿐
REFUNDS: list[dict] = []        # 13장: 받은 환불(가짜 주문 번호·금액·반품 신청 번호·승인 1회용 번호·추적 번호)
TOKEN_RE = re.compile(r"^tok_test_[0-9]{4,16}$")      # 랩의 가짜 테스트 카드 토큰만(카드 번호 모양은 받지 않는다)
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
        if self.path == "/_lab/fault":
            return self.reply(200, FAULT)
        if self.path == "/_lab/stats":
            with LOCK:
                rows = list(SEEN)
            return self.reply(200, {"count": len(rows), "items": rows[-2000:], "fault": FAULT})
        if self.path == "/v1/payments":
            with LOCK:
                return self.reply(200, list(PAYMENTS.values()))
        if self.path.startswith("/v1/payments/by-order/"):
            # 14장: 주문 번호로 결제 상태 묻기(고객 웹의 결제 확인). 느림 모드(slow)면 이 질의도 늦게 답한다(14-3 재연)
            if FAULT["mode"] == "slow":
                time.sleep(min(FAULT["ms"], 5000) / 1000)
            sn = self.path.rsplit("/", 1)[1][:40]
            with LOCK:
                rows = [p for p in PAYMENTS.values() if p["order_sn"] == sn]
            if not rows:
                return self.reply(404, {"order_sn": sn, "status": "NONE"})
            last = rows[-1]
            return self.reply(200, {"order_sn": sn, "payment_id": last["payment_id"], "status": last["status"].upper()})
        if self.path == "/v1/webhooks":
            with LOCK:
                return self.reply(200, WEBHOOKS[-200:])
        if self.path.split("?", 1)[0] in ("/v1/refunds", "/_stub/refunds"):
            rid = ""
            if "?" in self.path:
                for kv in self.path.split("?", 1)[1].split("&"):
                    if kv.startswith("return_id="):
                        rid = kv.split("=", 1)[1]
            with LOCK:
                rows = [r for r in REFUNDS if not rid or str(r.get("return_id")) == rid]
            if self.path.startswith("/v1/"):
                return self.reply(200, rows[-500:])
            return self.html(rows[-200:])
        self.reply(404, {"error": "없는 주소"})

    def html(self, rows: list[dict]) -> None:
        """13-3 화면 캡처용 환불 목록 표. 값은 가짜 주문 번호·금액뿐이라 그대로 보인다(글자는 이스케이프)"""
        import html as h
        trs = "".join(f"<tr><td>{h.escape(str(r['refund_id']))}</td><td>{h.escape(str(r['order_sn']))}</td><td>{h.escape(str(r.get('return_id')))}</td>"
                      f"<td style='text-align:right'>{int(r['amount']):,}원</td><td>{h.escape(str(r.get('approval') or '(없음)'))[:12]}</td>"
                      f"<td>{time.strftime('%H:%M:%S', time.localtime(r['ts']))}</td></tr>" for r in reversed(rows))
        body = ("<!doctype html><meta charset='utf-8'><title>가짜 결제사 환불 목록</title>"
                "<p style='background:#fde68a;padding:4px'>교육용 랩 · 127.0.0.1 · 값은 가짜</p><h1>가짜 결제사 · 환불 목록</h1>"
                f"<p>모두 {len(rows)}건</p><table border=1 cellpadding=4><tr><th>환불</th><th>주문 번호</th><th>반품 신청</th><th>금액</th><th>승인 번호</th><th>시각</th></tr>"
                f"{trs}</table>").encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
        self.end_headers()
        self.wfile.write(body)

    def seen(self, status: int) -> None:
        with LOCK:
            SEEN.append({"ts": round(getattr(self, "t0", time.time()), 3), "trace_id": (self.headers.get("X-Trace-Id") or "")[:64],
                         "key": (self.headers.get("Idempotency-Key") or "")[:80], "status": status})
            del SEEN[:-5000]

    def do_POST(self):  # noqa: N802
        self.t0 = time.time()                      # 받은 시각(늦게 답하는 모드에서도 도착 시각으로 센다)
        try:
            if self.path == "/_lab/fault":
                d = self.read_json()
                mode = str(d.get("mode", "ok")) if isinstance(d, dict) else ""
                ms = str(d.get("ms") or 0) if isinstance(d, dict) else "x"
                if mode not in ("ok", "500", "slow") or not ms.isdigit():
                    return self.reply(400, {"error": "mode는 ok·500·slow, ms는 0~5000"})
                FAULT.update(mode=mode, ms=max(0, min(int(ms), 5000)))
                return self.reply(200, FAULT)
            if self.path == "/_lab/reset":
                with LOCK:
                    SEEN.clear()
                return self.reply(200, {"count": 0})
            if self.path == "/v1/payments" and FAULT["mode"] == "500":
                self.seen(500)
                return self.reply(500, {"error": "랩 장애 모드(500)"})
            if self.path == "/v1/payments" and FAULT["mode"] == "slow":
                time.sleep(min(FAULT["ms"], 5000) / 1000)
            if self.path == "/v1/payments":
                d = self.read_json()
                if not d.get("order_sn") or not isinstance(d.get("amount"), (int, float)) or d["amount"] <= 0:
                    self.seen(400)
                    return self.reply(400, {"error": "order_sn과 amount(양수)가 필요합니다"})
                token = d.get("card_token")
                fp = None
                if token is not None:
                    if not isinstance(token, str) or not TOKEN_RE.match(token):
                        self.seen(400)
                        return self.reply(400, {"error": "card_token은 tok_test_ + 숫자 4~16자리(랩의 가짜 테스트 토큰)만 받습니다"})
                    fp = "fp_" + hashlib.sha256(token.encode()).hexdigest()[:16]
                approved = token is None or token[-1] in "24"
                with LOCK:
                    pid = f"PAY{len(PAYMENTS) + 1:06d}"
                    p = {"payment_id": pid, "order_sn": str(d["order_sn"]), "amount": d["amount"],
                         "status": "approved" if approved else "declined", "card_fingerprint": fp,
                         "callback_url": d.get("callback_url"), "ts": int(time.time())}
                    PAYMENTS[pid] = p
                if not approved:
                    self.seen(402)
                    return self.reply(402, {k: v for k, v in p.items() if k != "callback_url"})
                self.seen(201)
                threading.Thread(target=send_webhook, args=(p,), daemon=True).start()
                return self.reply(201, {k: v for k, v in p.items() if k != "callback_url"})
            if self.path == "/v1/refunds":
                d = self.read_json()
                amount = d.get("amount") if isinstance(d, dict) else None
                if not d.get("order_sn") or isinstance(amount, bool) or not isinstance(amount, (int, float)) or amount <= 0:
                    return self.reply(400, {"error": "order_sn과 amount(양수)가 필요합니다"})
                with LOCK:
                    r = {"refund_id": f"RF{len(REFUNDS) + 1:06d}", "order_sn": str(d["order_sn"])[:40], "amount": amount,
                         "return_id": d.get("return_id"), "approval": str(d.get("approval") or "")[:64],
                         "trace_id": (self.headers.get("X-Trace-Id") or "")[:64], "ts": int(time.time())}
                    REFUNDS.append(r)
                    del REFUNDS[:-5000]
                return self.reply(201, r)
            parts = self.path.strip("/").split("/")
            if len(parts) == 4 and parts[:2] == ["v1", "payments"] and parts[3] == "resend":
                with LOCK:
                    p = PAYMENTS.get(parts[2])
                if not p or p.get("status") != "approved":
                    return self.reply(404, {"error": "없는 결제(또는 승인되지 않은 결제)"})
                threading.Thread(target=send_webhook, args=(p,), daemon=True).start()
                return self.reply(202, {"resent": p["payment_id"]})
            self.reply(404, {"error": "없는 주소"})
        except (ValueError, json.JSONDecodeError) as e:
            self.reply(400, {"error": str(e)})


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
