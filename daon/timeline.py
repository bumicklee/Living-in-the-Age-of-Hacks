"""14-4 사고 타임라인: 경보 한 줄에서 출발해 강의 내내 심은 SECURITY 줄을 actor → ip → trace_id 순서로 모은다.

    python -m daon timeline --actor member:117 --since 30m             1단계 actor + 2단계 같은 ip로 넓히기
    python -m daon timeline --trace drill-14-4-0008                     3단계 한 요청이 지나간 모든 칸(일반 로그 줄 포함)
    python -m daon timeline --actor member:117 --since 30m --incident INC-14-4
                                                                        결과물 .daon/incident/INC-14-4/timeline.md
    python -m daon timeline --actor admin:staff_night --since 2h --audit --logins     15-1 캡스톤: 감사 로그 줄·로그인 기록(평소 7일)도 함께
    옵션: --trace-prefix drill-14-4-(모의 침해 요청만) · --source docker|loki(기본 docker, A5가 켜져 있으면 loki도 된다)

출처(새 로그를 만들지 않는다)
  docker  docker compose logs --timestamps(앱·승인 서비스·백업 감사·게이트웨이) + 도구 이벤트 reports/security-events.jsonl
  loki    {stream="security"} (8-4 A5). 같은 표 모양으로 바꾼다
감사 로그(8-3, 다른 저장소)는 같은 시간대 정상 권한 조회 건수만 센다(4단계). 개인정보 원문은 어디에도 찍지 않는다(target은 ID만, 8-5 원칙).
결과의 「판단」 칸은 사람이 채운다: 사실 / 추정 / 모름(IP가 같다고 같은 사람이라고 확정하지 않는다).
"""
from __future__ import annotations

import json
import re
import sys
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

from daon import stack as st
from daon.config import DATA, ROOT, current_stack

SERVICES = ["mall-portal", "mall-admin", "approval", "backup-audit", "gateway"]
INCIDENTS = DATA / "incident"
_STAMP = re.compile(r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z)\s+(.*)$")


def since_to_utc(since: str) -> str:
    """'30m'·'2h'·'90s' 또는 ISO 시각 → docker logs --since 값(UTC ISO)"""
    m = re.fullmatch(r"(\d+)([smh])", since.strip())
    if m:
        sec = int(m.group(1)) * {"s": 1, "m": 60, "h": 3600}[m.group(2)]
        return (datetime.now(timezone.utc) - timedelta(seconds=sec)).strftime("%Y-%m-%dT%H:%M:%SZ")
    return since


def _services(stack: str) -> list[str]:
    have = set((st.compose(stack, "config", "--services", check=False, capture=True).stdout or "").split())
    return [s for s in SERVICES if s in have] or ["mall-portal", "mall-admin"]


def docker_lines(since: str, stack: str = "spring") -> list[tuple[str, str, str]]:
    """(서비스, 시각 UTC, 줄) — 모든 줄(SECURITY가 아닌 일반 로그도). trace_id 단계에서 쓴다"""
    out = []
    for svc in _services(stack):
        r = st.compose(stack, "logs", "--no-log-prefix", "--no-color", "--timestamps", "--since", since_to_utc(since), svc, check=False, capture=True)
        for line in (r.stdout or "").splitlines():
            m = _STAMP.match(line)
            if m:
                out.append((svc, m.group(1), m.group(2)))
    return out


def parse_security(svc: str, ts: str, line: str) -> dict | None:
    if " SECURITY " not in f" {line} " or "{" not in line:
        return None
    try:
        e = json.loads(line[line.index("{"):])
    except ValueError:
        return None
    if not isinstance(e, dict) or not e.get("event"):
        return None
    e.setdefault("ts", ts)
    e["app"] = svc
    e["_level"] = "WARN" if re.search(r"\bWARN\b", line[:line.index("{")]) else "INFO"
    return e


def security_rows(since: str = "30m", source: str = "docker", stack: str = "spring") -> list[dict]:
    rows: list[dict] = []
    if source == "loki":
        from replay import _obs
        sec = int(re.fullmatch(r"(\d+)([smh])", since).group(1)) * {"s": 1, "m": 60, "h": 3600}[since[-1]] if re.fullmatch(r"\d+[smh]", since) else 3600
        for labels, line in _obs.loki_query('{stream="security"}', since_s=sec, limit=5000):
            try:
                e = json.loads(line[line.index("{"):])
            except ValueError:
                continue
            e["app"] = labels.get("app", "?")
            e["_level"] = labels.get("level", "")
            rows.append(e)
    else:
        for svc, ts, line in docker_lines(since, stack):
            e = parse_security(svc, ts, line)
            if e:
                rows.append(e)
        sys.path.insert(0, str(ROOT / "tools"))
        import secevent
        cut = since_to_utc(since)
        for e in secevent.read():
            if _utc(e.get("ts", "")) >= _utc(cut):
                e["app"] = "도구"
                rows.append(e)
    rows.sort(key=lambda r: _utc(r.get("ts", "")))
    return rows


def _utc(ts: str) -> str:
    """여러 모양의 시각(+09:00·Z·+0900) → 비교용 UTC 글자"""
    if not ts:
        return ""
    t = ts.replace("Z", "+00:00")
    t = re.sub(r"([+-]\d\d)(\d\d)$", r"\1:\2", t)
    try:
        return datetime.fromisoformat(t).astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")
    except ValueError:
        return ts


def build(rows: list[dict], actor: str | None = None, ip: str | None = None, trace: str | None = None,
          trace_prefix: str | None = None) -> tuple[list[dict], dict]:
    """1단계 actor → 2단계 같은 ip로 넓히기 → (3단계 trace_id). 결과 줄과 단계별 요약"""
    note: dict = {}
    if trace_prefix:
        rows = [r for r in rows if str(r.get("trace_id") or "").startswith(trace_prefix)]
    if actor:
        first = [r for r in rows if r.get("actor") == actor]
        ips = {r.get("ip") for r in first if r.get("ip")}
        note["1_actor"] = len(first)
        note["2_ips"] = sorted(ips)
        rows = [r for r in rows if r.get("actor") == actor or (r.get("ip") in ips)]
        note["2_actors"] = sorted({str(r.get("actor")) for r in rows})
    if ip:
        rows = [r for r in rows if r.get("ip") == ip]
    if trace:
        rows = [r for r in rows if r.get("trace_id") == trace]
    return rows, note


def impact(rows: list[dict], opened: int | None = None) -> dict:
    """5단계 영향 범위: target의 ID만 센다(개인정보 원문 없이)"""
    tried = {r["target"] for r in rows if str(r.get("target", "")).startswith("order:") and r.get("event") == "access.denied.not_owner"}
    members = {r["actor"] for r in rows if str(r.get("actor", "")).startswith("member:")}
    return {"조회를 시도당한 주문": len(tried), "실제로 열린 주문": opened if opened is not None else "감사 로그·응답 기록과 맞춰 셈",
            "관찰된 고객 계정": len(members), "이벤트 종류": dict(Counter(r["event"] for r in rows))}


def to_markdown(rows: list[dict], note: dict, imp: dict, title: str) -> str:
    out = [f"# 사고 타임라인 · {title}", "",
           f"만든 시각: {datetime.now().astimezone().isoformat(timespec='seconds')} · 출처: 앱·승인 서비스·백업 감사·게이트웨이 SECURITY 줄, 도구 이벤트",
           "판단 칸은 사람이 채운다: 사실 / 추정 / 모름. IP가 같다는 것만으로 같은 사람이라고 확정하지 않는다(NAT·공유 프록시).", ""]
    if note:
        out += [f"- 1단계 actor 줄 수: {note.get('1_actor', '-')}", f"- 2단계 같은 ip: {', '.join(note.get('2_ips', [])) or '-'} → actor {', '.join(note.get('2_actors', []))}", ""]
    out += ["| 시각 | 출처 | event | actor | target | ip | trace_id | 판단 |", "|---|---|---|---|---|---|---|---|"]
    for r in rows:
        ts = r.get("ts", "")
        out.append(f"| {ts} | {r.get('app', '')} | {r.get('event', '')} | {r.get('actor', '')} | {r.get('target', '')} | {r.get('ip') or ''} | "
                   f"{r.get('trace_id') or ''} | 사실/추정/모름 |")
    out += ["", "## 영향 범위(ID로만 센다)", ""] + [f"- {k}: {v}" for k, v in imp.items()]
    out += ["", "## 로그를 믿을 수 있나", "", "- make audit-verify(8-6 해시 체인) 결과와 검사 범위를 여기 적는다. 통과만으로 수집 누락까지 입증되지는 않는다."]
    return "\n".join(out) + "\n"


def audit_rows(actor: str, since: str = "2h") -> list[dict]:
    """15-1: 감사 로그(8-3, 다른 저장소) 가운데 이 actor의 줄을 타임라인 모양으로. 조회·수정 건수와 대상 ID만"""
    from replay import _obs
    cut = _utc(since_to_utc(since))
    out = []
    for rec in _obs.audit_lines():
        if rec.get("actor") != actor or _utc(str(rec.get("ts", ""))) < cut:
            continue
        out.append({"ts": rec.get("ts"), "app": "감사 로그", "event": f"audit:{rec.get('action')}", "actor": actor,
                    "target": rec.get("target"), "ip": None, "trace_id": rec.get("trace_id"), "count": rec.get("count"),
                    "result": rec.get("result")})
    return out


def login_rows(actor: str, since: str = "7d") -> list[dict]:
    """15-1: 관리자 로그인 기록(원본 표 ums_admin_login_log: 시각·IP). SECURITY 줄이 아니다 — 정상 로그인은 막지 않으므로 기록만 있다"""
    if not actor.startswith("admin:"):
        return []
    from replay._money import mysql
    name = actor.split(":", 1)[1]
    if not re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", name):
        return []
    m = re.fullmatch(r"(\d+)([smhd])", since.strip())
    minutes = int(m.group(1)) * {"s": 1 / 60, "m": 1, "h": 60, "d": 1440}[m.group(2)] if m else 7 * 1440
    rows = mysql(f"SELECT DATE_FORMAT(l.create_time, '%Y-%m-%dT%H:%i:%s+09:00'), IFNULL(l.ip, '') FROM ums_admin_login_log l "
                 f"JOIN ums_admin a ON a.id = l.admin_id WHERE a.username = '{name}' "
                 f"AND l.create_time >= NOW() - INTERVAL {int(minutes)} MINUTE ORDER BY l.id")
    out = []
    for line in rows.splitlines():
        ts, _, ip = line.partition("\t")
        out.append({"ts": ts, "app": "로그인 기록", "event": "admin.login(기록)", "actor": actor, "target": actor, "ip": ip, "trace_id": None})
    return out


def trace_path(trace: str, since: str = "2h", stack: str = "spring") -> list[tuple[str, str, str]]:
    """3단계: 한 추적 번호가 지나간 모든 줄(게이트웨이 접근 로그·앱 일반 로그 [번호]·SECURITY·승인 서비스)"""
    return [(svc, ts, line[:240]) for svc, ts, line in docker_lines(since, stack) if trace in line]


def cmd_timeline(a) -> None:
    stack = a.stack or current_stack()
    if a.trace and not a.actor:
        hits = trace_path(a.trace, a.since or "2h", stack)
        print(f"추적 번호 {a.trace}가 지나간 줄 {len(hits)}개(시각 순)")
        for svc, ts, line in sorted(hits, key=lambda h: h[1]):
            print(f"  {ts}  {svc:<12} {line}")
        return
    rows = security_rows(a.since or "30m", a.source or "docker", stack)
    picked, note = build(rows, actor=a.actor, ip=a.ip, trace=a.trace, trace_prefix=a.trace_prefix)
    opened = None
    if a.trace_prefix and a.trace_prefix.startswith("drill-14-4"):
        from replay.drills import d14_04
        opened = (d14_04.last() or {}).get("opened")
    if getattr(a, "audit", False) and a.actor:
        picked = sorted(picked + audit_rows(a.actor, a.since or "30m"), key=lambda r: _utc(str(r.get("ts", ""))))
    if getattr(a, "logins", False) and a.actor:
        picked = sorted(picked + login_rows(a.actor, a.logins_since or "7d"), key=lambda r: _utc(str(r.get("ts", ""))))
    imp = impact(picked, opened)
    if note:
        print(f"1단계 actor {a.actor}: {note['1_actor']}줄 · 2단계 같은 ip {note['2_ips']} → actor {note['2_actors']}")
    print(f"{'시각':<26} {'출처':<12} {'event':<32} {'actor':<16} {'target':<18} trace_id")
    for r in picked:
        print(f"{str(r.get('ts', ''))[:26]:<26} {r.get('app', ''):<12} {r.get('event', ''):<32} {str(r.get('actor', '')):<16} "
              f"{str(r.get('target', ''))[:18]:<18} {r.get('trace_id') or ''}")
    print("영향 범위: " + " · ".join(f"{k} {v}" for k, v in imp.items() if k != "이벤트 종류"))
    if a.incident:
        if not re.fullmatch(r"[A-Za-z0-9-]{3,40}", a.incident):
            raise SystemExit("사고 번호는 영문·숫자·하이픈(예: INC-14-4)")
        d = INCIDENTS / a.incident
        d.mkdir(parents=True, exist_ok=True)
        (d / "timeline.md").write_text(to_markdown(picked, note, imp, a.incident), "utf-8")
        print(f"결과물: {(d / 'timeline.md').relative_to(ROOT)}")


def register(add, stack_opt) -> None:
    add("timeline", cmd_timeline, stack_opt, (["--actor"], {}), (["--ip"], {}), (["--trace"], {}), (["--trace-prefix"], {}),
        (["--since"], {}), (["--source"], {"choices": ["docker", "loki"]}), (["--incident"], {}),
        (["--audit"], {"action": "store_true", "help": "15-1: 같은 actor의 감사 로그 줄도"}),
        (["--logins"], {"action": "store_true", "help": "15-1: 관리자 로그인 기록(평소 IP·시각과 비교)"}),
        (["--logins-since"], {"default": "7d"}))
