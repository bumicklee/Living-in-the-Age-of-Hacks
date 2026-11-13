"""섹션 8 랩 도구: 기록과 경보(A5). 랩-설계.md 10절, course/SECURITY_EVENTS.md 「8장」.

  make events-count                지금까지 쌓인 SECURITY·도구 이벤트를 이름별로 센다(8-1)
  make alert-rules                 SECURITY_EVENTS.md 표 → infra/observability/rules/security.yaml, Grafana 다시 띄움(8-4)
  make collector-test              앱 로그에 샘플 줄 두 개(SECURITY 줄, 새는 줄)를 넣고 Loki에 어떻게 들어갔는지 본다(8-4·8-5)
  make log-scan [SINCE=10m]        앱 로그(와 Loki)에서 평문 비밀번호·JWT·휴대전화 모양을 센다. 값은 찍지 않는다(8-5)
  make audit-verify                감사 로그 해시 체인과 머리 해시(Loki 사본)를 확인한다. 끊기면 종료 코드 1 + audit.chain.broken(8-6)
  make traffic-normal [MIN=10]     관리자·회원의 평범한 사용을 흉내 낸다. 끝나면 그동안 온 경보 메일 수(오탐 확인, 8-4)
  make drill [WAIT=150]            모의 경보 훈련: 번호 훑기·대량 조회·감사 로그 줄 지우기 + 정상 사용(8-7)
  make drill D=14-4                14장 모의 침해 재생(replay/drills/d14_04.py, daon/incident.py)
  make ack ID=<경보 이름>          경보를 확인했다고 적는다(8-7)
  make drill-report                경보 → 확인 시간 표. 기준 시간을 넘긴 P1은 alert.escalated.unacknowledged(8-7)
  make wipe-logs                   Loki에 쌓인 로그를 지운다(랩 전용. 운영은 보관 기간과 삭제 절차가 따로 있다)

모든 명령은 이 PC의 랩(127.0.0.1)만 부르고, 값(토큰·비밀번호·전화번호)은 화면에 찍지 않는다.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import time
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

from daon import stack as st
from daon.config import DATA, ROOT, current_stack

ACKS = DATA / "acks.json"
DRILL = DATA / "drill.json"
GENESIS = "0" * 64


# ── 8-1 이벤트 세기 ──
def cmd_events_count(a) -> None:
    stack = a.stack or current_stack()
    rows: Counter = Counter()
    lessons: dict[str, str] = {}
    from daon.lab import _has_service
    extra = ["backup-audit"] if stack == "spring" and _has_service(stack, "backup-audit") else []
    for svc in st.app_services(stack) + extra:  # 13장부터 승인 서비스(approval), 14-2부터 백업 감사(backup-audit)도 센다
        r = st.compose(stack, "logs", "--no-log-prefix", svc, check=False, capture=True)
        for line in (r.stdout or "").splitlines():
            if " SECURITY " not in line or "{" not in line:
                continue
            try:
                e = json.loads(line[line.index("{"):])
            except ValueError:
                continue
            if isinstance(e, dict) and e.get("event"):
                rows[(svc, e["event"])] += 1
                lessons[e["event"]] = str(e.get("lesson", ""))
    sys.path.insert(0, str(ROOT / "tools"))
    import secevent
    for e in secevent.read():
        rows[("도구", e.get("event", "?"))] += 1
        lessons[e.get("event", "?")] = str(e.get("lesson", ""))
    if not rows:
        print("아직 이벤트가 없습니다. make replay(재연)를 돌린 뒤 다시 세어 보세요.")
        return
    print(f"보안 이벤트 줄 수 · {stack} · {time.strftime('%Y-%m-%d %H:%M')} (컨테이너가 다시 만들어지면 앱 로그는 처음부터 다시 쌓인다)")
    print(f"{'출처':<12} {'수업':<10} {'event':<40} 줄")
    for (src, ev), n in sorted(rows.items(), key=lambda kv: (lessons.get(kv[0][1], ""), kv[0][1])):
        print(f"{src:<12} {lessons.get(ev, ''):<10} {ev:<40} {n}")
    print(f"\n합계 {sum(rows.values())}줄 · {len({ev for _, ev in rows})}종류. 이 줄들이 쌓이는 동안 누구에게도 알림이 가지 않았습니다(8-4 전).")


# ── 8-4 경보 규칙 만들기 ──
def cmd_alert_rules(a) -> None:
    sys.path.insert(0, str(ROOT / "tools"))
    import alert_rules
    alert_rules.main()
    stack = a.stack or current_stack()
    if "a05" not in st.arch_layers():
        print("\nA5(Loki·Grafana)가 아직 꺼져 있습니다. 8-4 완료 지점에서 make up 하면 이 규칙을 읽습니다.")
        return
    r = st.compose(stack, "ps", "--format", "json", "grafana", check=False, capture=True)
    if (r.stdout or "").strip():
        print("\nGrafana를 다시 띄워 규칙을 읽게 합니다(30초쯤).")
        st.compose(stack, "up", "-d", "--no-deps", "--force-recreate", "grafana")


# ── 8-4·8-5 수집기 시험 ──
def inject(service: str, text: str, stack: str = "spring") -> None:
    """랩 도구: 앱 컨테이너의 표준 출력(PID 1)에 한 줄을 쓴다. 앱이 찍은 줄처럼 수집기가 읽는다(랩 안에서만)."""
    safe = text.replace("'", "")
    r = st.compose(stack, "exec", "-T", service, "sh", "-c", f"printf '%s\\n' '{safe}' > /proc/1/fd/1",
                   check=False, capture=True)
    if r.returncode != 0:
        raise SystemExit(f"{service}에 샘플 줄을 넣지 못했습니다(make up): {(r.stderr or '').strip()[:120]}")


FAKE_JWT = "eyJsYWJGYWtlSGVhZGVy.eyJsYWJGYWtlQm9keQ.ZmFrZVNpZ25hdHVyZQ"   # 랩 전용 가짜(서명도 내용도 없음)


def collector_samples(marker: str) -> dict[str, str]:
    ts = time.strftime("%Y-%m-%dT%H:%M:%S.000+09:00")
    sec = json.dumps({"event": "lab.collector.test", "lesson": "8-4", "actor": "tool:collector-test",
                      "target": "order:202611100100000001", "ip": None, "ua": None, "reason": "수집기 시험 줄",
                      "count": 1, "ts": ts, "trace_id": marker}, ensure_ascii=False)
    return {
        "security": f"{ts}  WARN 1 --- [mall-portal] [lab-test] [{marker}] SECURITY                                 : {sec}",
        "leak": f"{ts}  INFO 1 --- [mall-portal] [lab-test] [{marker}] c.m.m.lab.CollectorTest : debug token={FAKE_JWT} order=202611100100000001",
    }


def cmd_collector_test(a) -> None:
    from replay import _obs
    stack = a.stack or current_stack()
    marker = "collector-test-" + hashlib.sha256(str(time.time()).encode()).hexdigest()[:10]
    for kind, line in collector_samples(marker).items():
        inject("mall-portal", line, stack)
        print(f"넣은 줄({kind}): {_obs.mask_preview(line, 140)}")
    if not _obs.a05_on():
        print("A5가 꺼져 있어 Loki가 없습니다(8-4에서 켭니다). make logs S=mall-portal 로 줄만 확인하세요.")
        return
    rows = _obs.wait_loki(f'{{app="mall-portal"}} |= "{marker}"', timeout=40, want=2)
    print(f"\nLoki에서 찾은 줄 {len(rows)}개:")
    for labels, line in rows:
        keep = {k: v for k, v in labels.items() if k in ("app", "stream", "event", "level", "lesson", "masked_at_collector", "leak")}
        raw = "그대로 들어감(가려지지 않음)" if FAKE_JWT in line else "가려짐"
        print(f"  레이블 {keep}\n  줄 {line[:150]}{'' if 'token=' not in line else '  ← 토큰 ' + raw}")
    if not rows:
        print("  아직 없음: 수집기(alloy)가 떠 있는지 make ps, 수집기 설정을 바꿨다면 make up")


# ── 8-5 로그 점검 ──
def _since_arg(v: str | None) -> str:
    return v or "10m"


def cmd_log_scan(a) -> None:
    from replay import _obs
    from replay.t21_log_leak import SERVICES, count
    stack = a.stack or current_stack()
    since = _since_arg(a.since)
    lines_by: dict[str, list[str]] = {}
    for svc in SERVICES:
        r = st.compose(stack, "logs", "--no-log-prefix", "--since", since, svc, check=False, capture=True)
        lines_by[svc] = (r.stdout or "").splitlines()
    print(f"로그 점검 · 최근 {since} · 값은 찍지 않고 종류별 줄 수만")
    total = 0
    for svc, lines in lines_by.items():
        c = count(lines)
        total += sum(c.values())
        print(f"  앱 로그 {svc:<12} 비밀번호 값 {c['password']:>3} · JWT 모양 {c['jwt']:>3} · 휴대전화 모양 {c['phone']:>3}  (전체 {len(lines)}줄)")
    if _obs.a05_on() and _obs.loki_ready():
        m = re.fullmatch(r"(\d+)([smhd])", since)
        secs = int(m[1]) * {"s": 1, "m": 60, "h": 3600, "d": 86400}[m[2]] if m else 600
        lk = [ln for _, ln in _obs.loki_query('{app=~"mall-admin|mall-portal", stream!="audit"}', since_s=secs, limit=5000)]
        c = count(lk)
        total += sum(c.values())
        print(f"  Loki      {'(A5)':<12} 비밀번호 값 {c['password']:>3} · JWT 모양 {c['jwt']:>3} · 휴대전화 모양 {c['phone']:>3}  (전체 {len(lk)}줄)")
    print(f"\n합계 {total}줄" + (" → 로그가 민감 값의 사본입니다(8-5에서 고칩니다)" if total else " → 깨끗합니다"))
    if a.fail and total:
        sys.exit(1)


# ── 8-6 감사 로그 확인 ──
@dataclass
class VerifyReport:
    status: str                 # ok · broken · no_chain · no_file
    summary: str
    seq: int | None = None
    details: list[str] = field(default_factory=list)
    lines: int = 0
    head_checked: bool = False


def canonical(d: dict) -> str:
    return json.dumps(d, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def line_hash(d: dict) -> str:
    body = {k: v for k, v in d.items() if k != "hash"}
    return hashlib.sha256(canonical(body).encode("utf-8")).hexdigest()


def check_chain(lines: list[dict]) -> tuple[str, int | None, str, list[str]]:
    """파일 순서대로 체인을 다시 계산한다. (상태, 끊긴 seq, 이유, 자세히)"""
    chained = [d for d in lines if "seq" in d and "hash" in d]
    if not chained:
        return "no_chain", None, "줄에 체인 정보(seq·prev·hash)가 없습니다(8-6 전)", []
    if "hash" not in lines[-1]:
        # 체인 줄 뒤에 체인 없는 줄이 이어진다: 지금 쓰는 줄은 체인으로 지켜지지 않는다(체인이 꺼졌거나 8-6 전 코드)
        return "no_chain", None, "마지막 줄에 체인 정보가 없습니다(체인이 꺼졌거나 8-6 전 코드가 쓰는 중)", [f"체인 줄 {len(chained)}개 뒤에 체인 없는 줄"]
    details = []
    prev_d = None
    for d in chained:
        seq = d.get("seq")
        if line_hash(d) != d.get("hash"):
            return "broken", seq, "줄 내용이 바뀜(이 줄의 해시가 맞지 않음)", [f"seq={seq} action={d.get('action')}"]
        if d.get("seq") == 1 and d.get("prev") == GENESIS:
            if prev_d is not None:
                details.append(f"seq=1에서 새 체인 시작(앱이 체인 없이 돌았던 뒤 · 이전 마지막 seq={prev_d.get('seq')})")
            prev_d = d
            continue
        if prev_d is None:
            return "broken", seq, "체인의 첫 줄(seq=1)이 없음(앞쪽 줄이 지워짐)", [f"파일의 첫 체인 줄 seq={seq}"]
        if d.get("prev") != prev_d.get("hash") or seq != prev_d.get("seq", 0) + 1:
            gap = f"seq {prev_d.get('seq')} 다음이 seq {seq}"
            return "broken", seq, "앞 줄 해시 불일치(바로 앞 줄이 지워졌거나 바뀜)", [gap]
        prev_d = d
    return "ok", None, f"체인 {len(chained)}줄 이상 없음", details


def verify(stack: str = "spring", emit: bool = True) -> VerifyReport:
    from replay import _obs
    loki_rows = None
    if _obs.a05_on() and _obs.loki_ready():                # Loki 사본을 먼저 읽는다(그 뒤에 쓰인 줄과 헷갈리지 않게)
        try:
            loki_rows = [ln for _, ln in _obs.loki_query('{stream="audit", app="mall-admin"}', since_s=86400, limit=5000)]
        except Exception:                                   # noqa: BLE001  Loki가 없어도 파일 체인은 본다
            loki_rows = None
    raw = _obs.audit_raw(stack)
    lines = []
    for ln in raw.splitlines():
        try:
            d = json.loads(ln)
        except ValueError:
            continue
        if isinstance(d, dict):
            lines.append(d)
    if not lines:
        return VerifyReport("no_file", "감사 로그 파일이 비었거나 없습니다(8-3 전이거나 make wipe 직후)")
    status, seq, why, details = check_chain(lines)
    rep = VerifyReport(status, why, seq, details, len(lines))
    if status == "ok":
        chained = [d for d in lines if "hash" in d]
        last = chained[-1]
        if loki_rows is not None:
            rep.head_checked = True
            succ = None
            for ln in loki_rows:
                try:
                    d = json.loads(ln)
                except ValueError:
                    continue
                if d.get("prev") == last.get("hash"):
                    succ = d
                    break
            if succ:
                rep = VerifyReport("broken", "머리 해시 불일치(파일의 마지막 줄 뒤에 있던 줄이 지워짐: Loki 사본에는 있다)",
                                   succ.get("seq"), [f"파일 마지막 seq={last.get('seq')} · Loki 사본에는 seq={succ.get('seq')}가 이어짐"],
                                   len(lines), True)
            else:
                rep.summary += f" · 머리 해시 확인(Loki 사본 {len(loki_rows)}줄, 파일 마지막 seq={last.get('seq')})"
        else:
            rep.summary += " · 머리 해시는 확인하지 못함(Loki가 없음: 8-4 A5)"
    if rep.status == "broken" and emit:
        sys.path.insert(0, str(ROOT / "tools"))
        import secevent
        reason = "머리 해시 불일치" if rep.summary.startswith("머리") else "앞 줄 해시 불일치"
        secevent.emit("audit.chain.broken", "8-6", "tool:audit-verify", f"audit:mall-admin#seq={rep.seq}", reason)
    return rep


def cmd_audit_verify(a) -> None:
    stack = a.stack or current_stack()
    rep = verify(stack, emit=True)
    label = {"ok": "통과", "broken": "체인이 끊김", "no_chain": "확인할 수 없음", "no_file": "파일 없음"}[rep.status]
    print(f"감사 로그 확인 · mall-admin · {rep.lines}줄 · {label}")
    print(f"  {rep.summary}" + (f" (seq={rep.seq})" if rep.seq else ""))
    for d in rep.details:
        print(f"  {d}")
    if rep.status != "ok":
        sys.exit(1)


# ── 8-4 평범한 사용(오탐 확인) ──
def normal_round() -> None:
    from replay._http import admin, admin_login, portal, portal_login
    from replay.t09_login_guard import clear_counters
    clear_counters()
    with admin() as c:
        tok = admin_login(c, "staff_order")
        h = {"Authorization": tok}
        page = c.get("/order/list", params={"pageNum": 1, "pageSize": 10}, headers=h).json().get("data") or {}
        for o in (page.get("list") or [])[:1]:
            c.get(f"/order/{o['id']}", headers=h)
    with portal() as c:
        tok = portal_login(c, "alice")
        c.get("/order/list", params={"status": -1, "pageNum": 1, "pageSize": 5}, headers={"Authorization": tok})


def cmd_traffic_normal(a) -> None:
    from replay import _obs
    minutes = float(a.minutes or 10)
    start = _obs.now_utc()
    end = time.time() + minutes * 60
    n = 0
    print(f"평범한 사용 {minutes:g}분: 주문 관리자 staff_order가 주문 목록(한 쪽 10건)·상세 하나, 회원 alice가 내 주문 목록. 20초마다")
    while time.time() < end:
        normal_round()
        n += 1
        time.sleep(min(20, max(0, end - time.time())))
    got = [m for m in _obs.mails(start) if m["subject"].startswith(_obs.SUBJECT_PREFIX)]
    print(f"{n}번 돌았습니다. 그동안 온 경보 메일 {len(got)}통" + (" → 오탐인지 제목을 보세요" if got else " (오탐 없음)"))
    for m in got[:10]:
        print(f"  {m['subject']}")


# ── 8-7 모의 경보 훈련 ──
def _load(p: Path) -> dict:
    try:
        return json.loads(p.read_text("utf-8"))
    except (FileNotFoundError, ValueError):
        return {}


def cmd_drill(a) -> None:
    if getattr(a, "drill", None):                     # 14-4: make drill D=14-4 → 모의 침해 재생(경보 훈련 8-7과 다른 재생)
        if a.drill not in ("14-4", "d14-4", "d14_04"):
            raise SystemExit("지금 있는 모의 침해 재생: D=14-4")
        from daon import incident
        incident.drill(a)
        return
    import replay
    from replay import _obs
    stack = a.stack or current_stack()
    if not _obs.a05_on():
        raise SystemExit("A5(경보)가 꺼져 있습니다. make goto L=8-7 뒤 make up")
    start = _obs.now_utc()
    DATA.mkdir(exist_ok=True)
    DRILL.write_text(json.dumps({"start": start}), "utf-8")
    print("모의 경보 훈련 시작(127.0.0.1, 가짜 계정만). 정상 사용도 함께 돈다.")
    steps = [("번호 훑기(T01 × 6)", lambda: [replay.get("01").run(stack) for _ in range(6)]),
             ("관리자 대량 조회(T20)", lambda: replay.get("20").run(stack, dynamic_run=False)),
             ("감사 로그 줄 지우기 → make audit-verify(T20x)", lambda: replay.get("20x").run(stack)),
             ("정상 사용 한 바퀴", normal_round)]
    for name, fn in steps:
        print(f"  · {name}")
        try:
            fn()
        except replay.NotReady as e:
            print(f"    [준비 안 됨] {e}")
        time.sleep(5)
    wait = float(a.wait or 150)
    print(f"경보 평가·묶음 대기 때문에 {wait:g}초 기다린 뒤 받은 편지함을 셉니다(http://127.0.0.1:8025)")
    time.sleep(wait)
    got = [m for m in _obs.mails(start) if "[다온" in m["subject"]]
    by = Counter(re.sub(r"\s+\S+$", "", m["subject"]) for m in got)
    print(f"\n받은 경보 메일 {len(got)}통")
    for subj, n in by.most_common():
        print(f"  {n:>3}  {subj}")
    print("\n확인했으면: make ack ID=<경보 이름>  ·  대응 시간 표: make drill-report")


def cmd_ack(a) -> None:
    key = (a.id or "").strip()
    if not key:
        raise SystemExit("make ack ID=<경보 이름(예: access.denied.not_owner)>")
    acks = _load(ACKS)
    acks[key] = time.time()
    DATA.mkdir(exist_ok=True)
    ACKS.write_text(json.dumps(acks, ensure_ascii=False, indent=2), "utf-8")
    print(f"확인 기록: {key} · {time.strftime('%H:%M:%S')}")


def severity_of(subject: str) -> str:
    m = re.search(r"\[(P[123])\]", subject)
    return m[1] if m else "-"


def alert_name_of(subject: str) -> str:
    m = re.search(r"\]\s*([a-z0-9_]+(?:\.[a-z0-9_]+)+)", subject)
    return m[1] if m else subject


def escalate(mails: list[dict], acks: dict, limit_min: float, now: float, emit=None) -> list[dict]:
    """확인되지 않은 채 기준 시간을 넘긴 P1 경보 → alert.escalated.unacknowledged(이벤트 한 줄씩)"""
    emit = emit or __import__("secevent").emit
    late = []
    seen = set()
    for m in sorted(mails, key=lambda m: m["at"]):
        name = alert_name_of(m["subject"])
        if severity_of(m["subject"]) != "P1" or name in seen or name == "alert.escalated.unacknowledged":
            continue
        seen.add(name)
        acked = acks.get(name)
        if (acked is None or acked < m["at"]) and now - m["at"] > limit_min * 60:
            emit("alert.escalated.unacknowledged", "8-7", "tool:drill-report", f"alert:{name}",
                 f"P1 경보가 {limit_min:g}분 안에 확인되지 않음")
            late.append(m)
    return late


def cmd_drill_report(a) -> None:
    from replay import _obs
    sys.path.insert(0, str(ROOT / "tools"))
    start = _load(DRILL).get("start", time.time() - 3600)
    acks = _load(ACKS)
    got = [m for m in _obs.mails(start) if m["subject"].startswith(_obs.SUBJECT_PREFIX)]
    if not got:
        print("훈련 뒤 받은 경보 메일이 없습니다. make drill 먼저.")
        return
    first: dict[str, dict] = {}
    for m in sorted(got, key=lambda m: m["at"]):
        first.setdefault(alert_name_of(m["subject"]), m)
    print(f"경보 → 확인 시간 · 훈련 시작 {time.strftime('%H:%M:%S', time.localtime(start))}")
    print(f"{'등급':<4} {'경보':<36} {'메일 도착':<10} {'확인':<10} 걸린 시간")
    rows = sorted(first.items(), key=lambda kv: (severity_of(kv[1]['subject']), kv[1]['at']))
    for name, m in rows:
        ack = acks.get(name)
        took = f"{(ack - m['at']) / 60:.1f}분" if ack and ack >= m["at"] else "확인 안 됨"
        print(f"{severity_of(m['subject']):<4} {name:<36} {time.strftime('%H:%M:%S', time.localtime(m['at'])):<10} "
              f"{time.strftime('%H:%M:%S', time.localtime(ack)) if ack else '-':<10} {took}")
    limit = float(os.environ.get("DAON_ACK_LIMIT_MIN", "15"))
    late = escalate(got, acks, limit, time.time())
    if late:
        print(f"\n기준 시간({limit:g}분)을 넘긴 P1 {len(late)}건 → alert.escalated.unacknowledged(한 단계 위 담당 lead@에게 다시 보냄)")
    print("\n무엇이 늦었는지 나눠 적으세요: 알림이 늦었나(평가·묶음 대기), 사람이 늦었나, 런북이 없었나.")


# ── 랩 전용: Loki 비우기 ──
def cmd_wipe_logs(a) -> None:
    stack = a.stack or current_stack()
    if "a05" not in st.arch_layers():
        raise SystemExit("A5가 꺼져 있어 지울 Loki가 없습니다.")
    st.compose(stack, "rm", "-sf", "loki", "alloy", check=False)
    for v in ("daon_loki-data", "daon_alloy-data"):
        subprocess.run(["docker", "volume", "rm", "-f", v], capture_output=True)
    st.compose(stack, "up", "-d", "--no-deps", "loki", "alloy")
    print("Loki의 로그와 수집 위치를 지웠습니다. 수집기는 지금 떠 있는 컨테이너의 로그를 처음부터 다시 읽습니다.")


def register(add, stack_opt) -> None:
    add("events-count", cmd_events_count, stack_opt)
    add("alert-rules", cmd_alert_rules, stack_opt)
    add("collector-test", cmd_collector_test, stack_opt)
    add("log-scan", cmd_log_scan, stack_opt, (["--since"], {}), (["--fail"], {"action": "store_true"}))
    add("audit-verify", cmd_audit_verify, stack_opt)
    add("traffic-normal", cmd_traffic_normal, stack_opt, (["--minutes"], {}))
    add("drill", cmd_drill, stack_opt, (["--wait"], {}), (["--drill"], {}))
    add("ack", cmd_ack, (["id"], {"nargs": "?"}))
    add("drill-report", cmd_drill_report, stack_opt)
    add("wipe-logs", cmd_wipe_logs, stack_opt)
