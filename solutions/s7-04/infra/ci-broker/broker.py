"""다온마켓 A8 · 레지스트리 단기 자격증명 발급기(교육용 랩 전용, 7-5).

  작업(job)이 자기 Gitea 작업 토큰으로 요청한다 → 발급기가 Gitea에 「이 토큰이 지금 도는 작업의 것인가」를 묻는다
  → 맞으면 레지스트리 계정(job-<무작위>)과 무작위 비밀번호를 만들어 준다. 유효 시간 상한은 10분.
  작업이 끝나면(작업 토큰이 더는 통하지 않으면) 몇 초 안에 그 계정을 지운다.

  - 비밀번호·작업 토큰은 로그에 남기지 않는다. 남는 것은 계정 이름·남은 시간·개수뿐이다.
  - 레지스트리는 htpasswd 파일(bcrypt)을 읽는다. 이 파일을 쓰는 곳은 이 프로세스 하나다.
  - 장기 계정: lab-admin(운영자, 늘 있음). ci-push(저장소 비밀값 REGISTRY_PASSWORD용 장기 키)는 secrets.list에 그 이름이 있을 때만 만든다.
"""
import http.server
import json
import os
import secrets
import subprocess
import threading
import time
import urllib.request

AUTH = os.environ.get("HTPASSWD", "/auth/htpasswd")
GITEA = os.environ.get("GITEA_API", "http://gitea:3000/api/v1")
TTL_MAX = 600
LOCK = threading.Lock()
ISSUED: dict[str, dict] = {}          # 계정 -> {"pw": 해시 전 값은 두지 않는다, "token": 작업 토큰, "exp": 만료 시각}
STATIC: dict[str, str] = {}           # 계정 -> 비밀번호(장기)


def bcrypt(user: str, password: str) -> str:
    out = subprocess.run(["htpasswd", "-Bbn", user, password], capture_output=True, text=True, check=True).stdout.strip()
    return out


def write_file() -> None:
    lines = [bcrypt(u, p) for u, p in STATIC.items()] + [v["line"] for v in ISSUED.values()]
    tmp = AUTH + ".tmp"
    with open(tmp, "w") as f:
        f.write("\n".join(lines) + "\n")
    os.replace(tmp, AUTH)       # 이름 바꾸기(원자적). 레지스트리는 파일이 바뀐 것을 보고 다시 읽는다


def job_alive(token: str) -> bool:
    req = urllib.request.Request(GITEA + "/user", headers={"Authorization": "token " + token})
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            return json.load(r).get("login") == "gitea-actions"
    except Exception:
        return False


def prune() -> None:  # LOCK 안에서 job_alive(Gitea 호출)를 부르므로 느려도 짧게(5초 제한)
    now = time.time()
    with LOCK:
        dead = [u for u, v in ISSUED.items() if v["exp"] < now or not job_alive(v["token"])]
        for u in dead:
            ISSUED.pop(u)
            print(f"[broker] 단기 계정 {u} 을(를) 지웠습니다", flush=True)
        if dead:
            write_file()


def sweeper() -> None:
    while True:
        time.sleep(2)
        for step in (sync_static, prune):
            try:
                step()
            except Exception as e:     # 한 단계가 실패해도 다음 단계와 발급기는 계속 돈다. 이유만 남긴다
                print(f"[broker] {step.__name__} 실패:", type(e).__name__, flush=True)


class H(http.server.BaseHTTPRequestHandler):
    def _send(self, code: int, body: dict) -> None:
        raw = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path == "/healthz":
            return self._send(200, {"ok": True})
        if self.path == "/status":
            with LOCK:
                return self._send(200, {"active": len(ISSUED), "users": sorted(ISSUED), "ttl_max": TTL_MAX})
        self._send(404, {"error": "not found"})

    def do_POST(self):
        if not self.path.startswith("/token"):
            return self._send(404, {"error": "not found"})
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("token ") or not job_alive(auth[6:].strip()):
            print("[broker] 거부: 지금 도는 작업의 토큰이 아닙니다", flush=True)
            return self._send(401, {"error": "not a running job token"})
        user, pw = "job-" + secrets.token_hex(4), secrets.token_hex(16)
        with LOCK:
            ISSUED[user] = {"token": auth[6:].strip(), "exp": time.time() + TTL_MAX, "line": bcrypt(user, pw)}
            write_file()
        print(f"[broker] 단기 계정 {user} 발급(유효 {TTL_MAX}초 또는 작업이 끝날 때까지)", flush=True)
        self._send(200, {"username": user, "password": pw, "expires_in": TTL_MAX})

    def log_message(self, *a):
        pass


def sync_static() -> bool:
    """장기 계정을 secrets.list에 맞춘다(7-5에서 이 목록에서 REGISTRY_PASSWORD를 지우면 ci-push도 사라진다). 바뀌었으면 True"""
    want = {"lab-admin": os.environ["DAON_CI_ADMIN_PASSWORD"]}
    names = [l.strip() for l in open("/seed/secrets.list") if l.strip() and not l.startswith("#")]
    if "REGISTRY_PASSWORD" in names:
        want["ci-push"] = os.environ["DAON_CI_PUSH_PASSWORD"]
    with LOCK:
        if want == STATIC:
            return False
        STATIC.clear()
        STATIC.update(want)
        write_file()
    print("[broker] 장기 계정:", ", ".join(STATIC), flush=True)
    return True


def ensure_keys() -> None:
    """이미지 서명 키 한 쌍(랩 전용, 도커 볼륨 ci-keys, git에 들어가지 않는다). 없을 때만 만든다. 개인 키는 러너(7-6)만 읽는다"""
    if os.path.exists("/keys/cosign.key"):
        return
    env = os.environ | {"COSIGN_PASSWORD": os.environ.get("DAON_CI_SIGN_PASSWORD", "lab-fake-sign-0001")}
    subprocess.run(["cosign", "generate-key-pair"], cwd="/keys", env=env, check=True, capture_output=True)
    os.chown("/keys/cosign.key", 10002, 10002)
    os.chmod("/keys/cosign.key", 0o400)
    os.chmod("/keys/cosign.pub", 0o644)
    print("[broker] 서명 키 한 쌍을 만들었습니다(공개 키만 밖으로 나갑니다)", flush=True)


def main() -> None:
    ensure_keys()
    sync_static()
    threading.Thread(target=sweeper, daemon=True).start()
    http.server.ThreadingHTTPServer(("0.0.0.0", 8000), H).serve_forever()


if __name__ == "__main__":
    main()
