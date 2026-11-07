"""방어 스위치(A4)와 랩 상황판(A8). 랩-설계.md 12·13절.

방어 스위치
  make switch                       목록과 지금 값
  make switch K=ownership V=off     끄기 → .daon/switches.env → 그 앱만 다시 띄움(30초쯤)
  make switch K=all V=on            전부 켜기(기본값으로 돌아가기)
  - 스위치를 읽는 코드는 수업 정답(완료 지점)에만 있다. 시작 지점에서는 바꿔도 아무 일도 없다(손으로 고치는 실습은 그대로).
  - Spring은 secure-kit.<이름>.enabled 를 환경 변수 SECUREKIT_<이름>_ENABLED 로 받는다(SecureKitSwitch).

랩 상황판
  make lab                          http://127.0.0.1:8099/lab (읽기 전용, 이 PC에서만)
  python -m daon lab --json         같은 내용을 JSON 한 번
"""
from __future__ import annotations

import json
import time
from datetime import datetime
from pathlib import Path

from daon import course, stack as st
from daon.config import DATA, ENV, ROOT, current_stack

# 이름 → 수업·앱·재연. SecureKitSwitch.KNOWN(Java)과 같은 이름을 쓴다
SWITCHES = {
    "ownership": {"lesson": "s2-02", "service": "mall-portal", "replay": "T01",
                  "title": "주문 소유권 검사(2-2·2-미션)"},
    "admin-default-deny": {"lesson": "s2-03", "service": "mall-admin", "replay": "T02",
                           "title": "관리자 기본 거부(2-3)"},
    "request-dto": {"lesson": "s2-04", "service": "mall-portal", "replay": "T03",
                    "title": "회원정보 요청 전용 DTO(2-4)"},
    "egress": {"lesson": "s2-05", "service": "mall-admin", "replay": "T04",
               "title": "나가는 요청 검사(2-5)"},
    "host-allowlist": {"lesson": "s2-06", "service": "mall-admin", "replay": "T05",
                       "title": "운영 도구 Host 허용 목록(2-6)"},
}
STATE = DATA / "switches.json"
ENV_FILE = DATA / "switches.env"       # infra/compose.yml의 x-switches가 읽는다(없으면 모두 켜짐)
ARCH_NAMES = {"a02": "A2 나가는 길 통제(이그레스 프록시)"}
BANNER = "교육용 랩 · 127.0.0.1 · 값은 가짜"


# ── 방어 스위치 ──
def switches() -> dict[str, bool]:
    try:
        saved = json.loads(STATE.read_text("utf-8"))
    except (FileNotFoundError, ValueError):
        saved = {}
    return {k: bool(saved.get(k, True)) for k in SWITCHES}


def env_name(key: str) -> str:
    return "SECUREKIT_" + key.upper().replace("-", "_") + "_ENABLED"


def _write(values: dict[str, bool]) -> None:
    DATA.mkdir(exist_ok=True)
    STATE.write_text(json.dumps(values, ensure_ascii=False, indent=2), "utf-8")
    lines = ["# make switch가 만든다. 손으로 고치지 않는다(랩-설계.md 12절). 여기 없는 스위치는 켜짐."]
    lines += [f"{env_name(k)}=false" for k, on in values.items() if not on]
    ENV_FILE.write_text("\n".join(lines) + "\n", "utf-8")


def reached(key: str, pos: dict | None = None) -> bool:
    """지금 지점에서 그 스위치를 읽는 코드(정답)가 들어와 있나."""
    pos = pos if pos is not None else course.position()
    if not pos:
        return False
    ids = course.order()
    want, here = SWITCHES[key]["lesson"], pos["lesson"]
    if want not in ids or here not in ids:
        return False
    return ids.index(here) > ids.index(want) or (here == want and pos.get("done"))


def off_switches() -> list[str]:
    return [k for k, on in switches().items() if not on]


def warn_if_off() -> None:
    off = off_switches()
    if off:
        print(f"[알림] 꺼진 방어 스위치: {', '.join(off)} → 완료 지점 시험이 레드일 수 있습니다. 다시 켜기: make switch K=all V=on")


def cmd_switch(a) -> None:
    stack = a.stack or current_stack()
    values = switches()
    if not a.key:
        pos = course.position()
        print("방어 스위치(secure-kit.<이름>.enabled, 완료 지점 코드만 읽음)")
        for k, meta in SWITCHES.items():
            state = "켜짐" if values[k] else "꺼짐"
            where = "" if reached(k, pos) else "  (지금 지점에는 아직 이 코드가 없음)"
            print(f"  {k:<20} {state}  {meta['title']} · 재연 {meta['replay']}{where}")
        print("\n바꾸기: make switch K=ownership V=off  ·  전부 켜기: make switch K=all V=on")
        return
    keys = list(SWITCHES) if a.key == "all" else [a.key]
    for k in keys:
        if k not in SWITCHES:
            raise SystemExit(f"없는 스위치입니다: {k}. 있는 것: {', '.join(SWITCHES)}, all")
    v = (a.value or "").lower()
    if v not in ("on", "off"):
        raise SystemExit("V는 on 또는 off입니다: make switch K=ownership V=off")
    on = v == "on"
    changed = [k for k in keys if values[k] != on]
    for k in keys:
        values[k] = on
    _write(values)
    if not changed:
        print(f"이미 {'켜짐' if on else '꺼짐'}입니다: {', '.join(keys)}")
        return
    for k in changed:
        note = "" if reached(k) else "  ※ 지금 지점에는 이 스위치를 읽는 코드가 없습니다(그 수업 완료 지점에서 쓰세요: make goto L=… AT=done)"
        print(f"{k} → {'켜짐' if on else '꺼짐'} ({env_name(k)}{'' if on else '=false'}){note}")
    if stack != "spring":
        print("Express 스택은 아직 스위치를 읽는 코드가 없습니다(diffs/만 제공). 값만 저장했습니다.")
        return
    if a.no_restart:
        print("값만 저장했습니다. 다음 make up·check 때 반영됩니다.")
        return
    services = sorted({SWITCHES[k]["service"] for k in changed})
    if not _running(stack, services):
        print("앱이 떠 있지 않아 값만 저장했습니다. make up 때 반영됩니다.")
        return
    st.ensure_fresh(stack)
    print(f"[{stack}] 스위치를 반영하려고 다시 띄웁니다: {', '.join(services)}")
    st.compose(stack, "up", "-d", "--no-deps", *services)
    st.wait_healthy(stack)
    replays = " · ".join(f"make replay T={SWITCHES[k]['replay'][1:]}" for k in changed)
    print(f"확인: {replays}  ({'그린이어야 합니다' if on else '레드여야 합니다'})")


def _running(stack: str, services: list[str]) -> bool:
    r = st.compose(stack, "ps", "--format", "json", *services, check=False, capture=True)
    return bool((r.stdout or "").strip())


# ── 랩 상황판 ──
def _events(stack: str, limit: int = 50) -> list[dict]:
    services = st.APPS[stack]
    out = []
    for svc in services:
        r = st.compose(stack, "logs", "--no-log-prefix", "--tail", "3000", svc, check=False, capture=True)
        for line in (r.stdout or "").splitlines():
            if " SECURITY " not in line or "{" not in line:
                continue
            try:
                e = json.loads(line[line.index("{"):])
            except ValueError:
                continue
            if isinstance(e, dict) and e.get("event"):
                e["service"] = svc
                out.append(e)

    def when(e):
        try:
            return datetime.fromisoformat(str(e.get("ts", "")).replace("Z", "+00:00")).timestamp()
        except ValueError:
            return 0.0
    out.sort(key=when, reverse=True)
    return out[:limit]


def state(stack: str | None = None) -> dict:
    stack = stack or current_stack()
    pos = course.position()
    lesson = None
    if pos:
        try:
            l = course.find(pos["lesson"])
            lesson = {"id": l["id"], "no": l["no"], "title": l["title"], "section": l["section"], "done": bool(pos.get("done"))}
        except SystemExit:
            lesson = {"id": pos["lesson"], "no": pos["lesson"], "title": "", "section": None, "done": bool(pos.get("done"))}
    layers = st.arch_layers()
    level = "A0" + "".join(f" + {n[0].upper()}{int(n[1:])}" for n in layers)
    values = switches()
    sw = [{"key": k, "title": m["title"], "lesson": course.short(m["lesson"]), "replay": m["replay"],
           "on": values[k], "active": reached(k, pos)} for k, m in SWITCHES.items()]
    score = None
    p = ROOT / "scoreboard" / f"{stack}.json"
    if p.exists():
        try:
            score = json.loads(p.read_text("utf-8"))
        except ValueError:
            score = None
    try:
        events, events_error = _events(stack), None
    except SystemExit as e:            # docker가 없을 때
        events, events_error = [], str(e)
    return {
        "banner": BANNER,
        "at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "stack": stack,
        "lesson": lesson,
        "arch": {"level": level, "layers": [{"name": n, "label": ARCH_NAMES.get(n, n)} for n in layers]},
        "switches": sw,
        "score": score,
        "events": events,
        "events_error": events_error,
    }


PAGE = Path(__file__).with_name("lab_page.html")
ALLOWED_HOSTS = {"127.0.0.1", "localhost"}


def serve(port: int, stack: str) -> None:
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

    cache: dict = {"t": 0.0, "body": b""}

    class Handler(BaseHTTPRequestHandler):
        def _send(self, code: int, body: bytes, ctype: str) -> None:
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Security-Policy",
                             "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):  # noqa: N802
            # 2-6에서 배운 대로: 이 PC 이름으로 부른 요청만 받는다(DNS 리바인딩 막기)
            host = (self.headers.get("Host") or "").rsplit(":", 1)[0].strip("[]").lower()
            if host not in ALLOWED_HOSTS:
                return self._send(403, "허용하지 않은 Host입니다".encode(), "text/plain; charset=utf-8")
            path = self.path.split("?", 1)[0]
            if path in ("/", "/lab", "/lab/"):
                return self._send(200, PAGE.read_bytes(), "text/html; charset=utf-8")
            if path == "/lab/state.json":
                if time.time() - cache["t"] > 2:
                    cache["body"] = json.dumps(state(stack), ensure_ascii=False).encode()
                    cache["t"] = time.time()
                return self._send(200, cache["body"], "application/json; charset=utf-8")
            return self._send(404, b"not found", "text/plain")

        def log_message(self, *args):   # 요청마다 찍지 않는다
            pass

    srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"랩 상황판: http://127.0.0.1:{port}/lab  (읽기 전용, 이 PC에서만 · 끝내려면 Ctrl+C)")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()


def cmd_lab(a) -> None:
    stack = a.stack or current_stack()
    if a.json:
        print(json.dumps(state(stack), ensure_ascii=False, indent=2))
        return
    serve(int(a.port or ENV.get("DAON_LAB_PORT", 8099)), stack)


def register(add, stack_opt) -> None:
    """cli.main()이 부른다."""
    add("switch", cmd_switch, (["key"], {"nargs": "?"}), (["value"], {"nargs": "?"}), stack_opt,
        (["--no-restart"], {"action": "store_true"}))
    add("lab", cmd_lab, stack_opt, (["--json"], {"action": "store_true"}), (["--port"], {}))
