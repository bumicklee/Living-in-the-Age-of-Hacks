"""다온마켓 14-2 · backup-audit: 백업 저장소(backup-vault)의 감사 로그 → SECURITY 한 줄. 표준 라이브러리만 쓴다.

MinIO는 앱이 아니라 SecurityEventLogger를 쓰지 못한다. 대신 모든 API 호출을 감사 로그 webhook으로 보낸다
(MINIO_AUDIT_WEBHOOK_ENDPOINT_daon = http://backup-audit:8000/minio-audit). 이 작은 서버가 받아서 앱과 같은 키의 줄을 찍고,
수집기(A5 Alloy)가 「WARN SECURITY {…}」 줄을 stream=security로 모은다(Express 줄과 같은 모양).

  backup.denied.delete (WARN)  daon-backup에 지우기(DeleteObject·DeleteObjects 등)를 시도했는데 거부됨(권한 없음·잠금)
                               actor minio:<접근 키>, target object:daon-backup/<키>. 한 번이라도 경보(정상 운영에서 백업을 지우는 사람은 없다)
  backup.missed (WARN)         backup-writer가 mysql/에 올린 마지막 백업이 기준 시간(DAON_BACKUP_MAX_AGE_SECONDS)보다 오래됨
                               (공격자가 백업을 못 지우면 새 백업이 안 생기게 막을 수 있다. 조용히 멈춘 백업도 사고다)

넣지 않는 것: 비밀 키·요청 본문·객체 내용. 시각은 +09:00(TZ=Asia/Seoul), trace_id는 MinIO 요청 번호(minio-<requestID>).
GET /health · GET /state(마지막 백업 시각, 시험·make backup-status용)
"""
from __future__ import annotations

import json
import os
import threading
import time
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BUCKET = "daon-backup"
MAX_AGE = int(os.environ.get("DAON_BACKUP_MAX_AGE_SECONDS", "900"))
STATE = {"last_put": time.time(), "last_put_key": None, "missed_at": 0.0, "denied": 0}   # 뜬 시각을 첫 기준으로(뜨자마자 울리지 않게)
COUNTS: dict[str, list[float]] = {}
LOCK = threading.Lock()


def emit(event: str, actor: str, target: str, reason: str, trace: str | None, level: str = "WARN") -> None:
    key = f"{event}|{actor}"
    now = time.time()
    with LOCK:
        q = [t for t in COUNTS.get(key, []) if now - t < 600] + [now]
        COUNTS[key] = q
    line = {"event": event, "lesson": "14-2", "actor": actor, "target": target, "ip": None, "ua": None,
            "reason": reason, "count": len(q), "ts": datetime.now().astimezone().isoformat(timespec="milliseconds"),
            "trace_id": trace}
    print(f"{level} SECURITY {json.dumps(line, ensure_ascii=False)}", flush=True)


def handle(entry: dict) -> None:
    api = entry.get("api") or {}
    name = str(api.get("name") or "")
    bucket = str(api.get("bucket") or "")
    obj = str(api.get("object") or "")
    code = api.get("statusCode")
    try:
        code = int(code)
    except (TypeError, ValueError):
        code = 0 if str(api.get("status", "")).upper() in ("OK", "") else 400
    who = str(entry.get("accessKey") or (entry.get("requestClaims") or {}).get("accessKey") or entry.get("parentUser") or "anonymous")
    trace = f"minio-{entry['requestID']}" if entry.get("requestID") else None
    if bucket != BUCKET:
        return
    if "delete" in name.lower() and code >= 400:
        with LOCK:
            STATE["denied"] += 1
        emit("backup.denied.delete", f"minio:{who}", f"object:{BUCKET}/{obj}" if obj else f"bucket:{BUCKET}",
             "백업 삭제 시도 거부(권한 없음 또는 잠금 기간 안)", trace)
    elif name == "PutObject" and 200 <= code < 300 and obj.startswith("mysql/") and who == "backup-writer":
        with LOCK:
            STATE["last_put"] = time.time()
            STATE["last_put_key"] = obj
            STATE["missed_at"] = 0.0


def watch() -> None:
    while True:
        time.sleep(30)
        now = time.time()
        with LOCK:
            age = now - STATE["last_put"]
            due = age > MAX_AGE and now - STATE["missed_at"] > MAX_AGE
            if due:
                STATE["missed_at"] = now
        if due:
            emit("backup.missed", "system:backup-audit", f"bucket:{BUCKET}",
                 f"마지막 백업이 {int(age // 60)}분 전(기준 {MAX_AGE // 60}분)", None)


class Handler(BaseHTTPRequestHandler):
    server_version = "daon-backup-audit/1"

    def log_message(self, fmt, *args):  # noqa: D401 - 접근 로그는 찍지 않는다(감사 로그 줄이 기록이다)
        pass

    def reply(self, code: int, data: dict) -> None:
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802
        if self.path == "/health":
            return self.reply(200, {"ok": True})
        if self.path == "/state":
            with LOCK:
                return self.reply(200, {"last_put_age_seconds": int(time.time() - STATE["last_put"]), "last_put_key": STATE["last_put_key"],
                                        "max_age_seconds": MAX_AGE, "denied_deletes": STATE["denied"]})
        self.reply(404, {"error": "없는 주소"})

    def do_POST(self):  # noqa: N802
        n = int(self.headers.get("Content-Length") or 0)
        if self.path != "/minio-audit" or n > 1024 * 1024:
            return self.reply(404, {"error": "없는 주소"})
        raw = self.rfile.read(n)
        try:
            data = json.loads(raw or b"{}")
        except ValueError:
            return self.reply(400, {"error": "JSON이 아님"})
        for entry in data if isinstance(data, list) else [data]:
            if isinstance(entry, dict):
                handle(entry)
        self.reply(200, {"ok": True})


if __name__ == "__main__":
    threading.Thread(target=watch, daemon=True).start()
    print(f"[A11] backup-audit 준비: 버킷 {BUCKET}, backup.missed 기준 {MAX_AGE}초", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
