"""SECURITY_EVENTS.md 표의 「경보 후보(8장)」 칸 → Grafana 경보 규칙 파일(수업 8-4, make alert-rules).

경보 규칙은 손으로 쓰지 않고 이벤트 표에서 만든다. 9장 뒤로 새 이벤트가 생기면 표에 한 줄만 더하면 된다.

읽는 법(칸의 글자 → 규칙)
  「한 번이라도」                     → 10분 안에 1건 이상
  「count ≥ 5 / 10분」               → 같은 묶음(actor)의 10분 합계 5건 이상
  「20회 / 10분」·「3회 / 1일」       → 같은 묶음의 그 기간 합계
  「반복」(반복 시·반복되면)          → 10분 안에 3건 이상
  「같은 ip」                         → 묶음을 actor 대신 ip로
  (P1)·(P2)·(P3)                      → 등급 레이블 severity(없으면 P2). 8-7에서 알림 정책이 등급을 읽는다
  `rules/` 가 적힌 칸                 → 손으로 쓴 규칙 파일(bulk.yaml 등)에 있으므로 건너뜀
  그 밖(「N개」, 「가까워지면」 …)   → 규칙으로 바꾸지 못함: 목록으로 알려 주고 건너뜀(사람이 판단)

같은 event가 표에 두 번 나오면 처음 줄만 쓴다. 출력은 늘 같은 순서·같은 글자(시험이 다시 만들어 비교한다).
"""
from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
EVENTS_MD = ROOT / "course" / "SECURITY_EVENTS.md"
OUT = ROOT / "infra" / "observability" / "rules" / "security.yaml"
DATASOURCE = "loki"
HEADER = """\
# 다온마켓 A5 · 보안 이벤트 경보 규칙(Grafana 경보 규칙 불러오기 형식)
# make alert-rules 가 course/SECURITY_EVENTS.md 표의 「경보 후보(8장)」 칸에서 만든다. 손으로 고치지 않는다.
# 새 이벤트는 표에 한 줄 더하고 make alert-rules 를 다시 돌린다(Grafana가 다시 뜬다).
# 손으로 쓴 규칙은 같은 폴더의 bulk.yaml(8-4)·collector.yaml(8-5), 알림 정책은 notify.yaml.
"""


@dataclass
class Rule:
    event: str
    lesson: str
    level: str
    threshold: int
    window_min: int
    by: str
    severity: str
    text: str


@dataclass
class Skipped:
    event: str
    lesson: str
    text: str
    why: str


def _cells(line: str) -> list[str]:
    return [c.strip() for c in line.strip().strip("|").split("|")]


def parse(md: str) -> tuple[list[Rule], list[Skipped]]:
    rules: list[Rule] = []
    skipped: list[Skipped] = []
    seen: set[str] = set()
    in_table = False
    for line in md.splitlines():
        if line.startswith("| event |"):
            in_table = True
            continue
        if not in_table:
            continue
        if not line.startswith("|"):
            if line.strip():
                in_table = False
            continue
        cells = _cells(line)
        if len(cells) < 6 or set(cells[0]) <= {"-", " "}:
            continue
        names = re.findall(r"`([a-z0-9_]+(?:\.[a-z0-9_]+)+)`", cells[0])
        level, lesson, text = cells[1], cells[2], cells[-1]
        for ev in names:
            if ev in seen:
                continue
            seen.add(ev)
            r = _rule(ev, lesson, level, text)
            if isinstance(r, Rule):
                rules.append(r)
            else:
                skipped.append(r)
    return rules, skipped


def _rule(ev: str, lesson: str, level: str, text: str) -> Rule | Skipped:
    plain = text.replace("`", "")
    if "rules/" in plain:
        return Skipped(ev, lesson, text, "손으로 쓴 규칙 파일에 있음")
    sev = (re.search(r"\bP([123])\b", plain) or [None, "2"])[1]
    by = "ip" if re.search(r"같은\s*ip\b", plain, re.I) else "actor"
    m = re.search(r"≥\s*(\d+)\s*/\s*(\d+)\s*분", plain)
    if m:
        return Rule(ev, lesson, level, int(m[1]), int(m[2]), by, f"P{sev}", text)
    m = re.search(r"(\d+)\s*회\s*/\s*(\d+)\s*(분|일)", plain)
    if m:
        win = int(m[2]) * (1440 if m[3] == "일" else 1)
        return Rule(ev, lesson, level, int(m[1]), win, by, f"P{sev}", text)
    if "한 번이라도" in plain:
        return Rule(ev, lesson, level, 1, 10, by, f"P{sev}", text)
    if re.search(r"\bN\s*개", plain):
        return Skipped(ev, lesson, text, "기준 숫자(N)가 정해지지 않음")
    if "반복" in plain:
        return Rule(ev, lesson, level, 3, 10, by, f"P{sev}", text)
    return Skipped(ev, lesson, text, "글자로는 규칙을 만들 수 없음(사람이 판단)")


def uid(prefix: str, ev: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", ev.lower()).strip("-")
    u = f"{prefix}-{slug}"
    if len(u) > 40:
        u = u[:31] + "-" + hashlib.sha256(ev.encode()).hexdigest()[:8]
    return u


def _window(minutes: int) -> str:
    return f"{minutes // 1440}d" if minutes % 1440 == 0 else f"{minutes}m"


def expr(r: Rule) -> str:
    return (f'sum by ({r.by}, app) (count_over_time({{event="{r.event}"}} '
            f'| json {r.by}="{r.by}" [{_window(r.window_min)}]))')


def grafana_rule(r: Rule) -> dict:
    return loki_rule(
        uid=uid("sec", r.event), title=r.event, expr=expr(r), window_sec=r.window_min * 60,
        above=r.threshold - 1,
        labels={"event": r.event, "severity": r.severity, "lesson": r.lesson, "source": "SECURITY_EVENTS.md"},
        summary=f"{r.event}: 같은 {r.by} {_window(r.window_min)} 안 {r.threshold}건 이상",
        description=f"경보 후보(8장): {r.text.replace('`', '')}",
    )


def loki_rule(*, uid: str, title: str, expr: str, window_sec: int, above: float, labels: dict,
              summary: str, description: str) -> dict:
    """Loki 즉시 질의(A) → 문턱값(C). 결과 묶음(actor 등)마다 경보 한 건."""
    return {
        "uid": uid,
        "title": title,
        "condition": "C",
        "data": [
            {"refId": "A", "relativeTimeRange": {"from": window_sec, "to": 0}, "datasourceUid": DATASOURCE,
             "model": {"refId": "A", "expr": expr, "queryType": "instant", "editorMode": "code"}},
            {"refId": "C", "relativeTimeRange": {"from": 0, "to": 0}, "datasourceUid": "__expr__",
             "model": {"refId": "C", "type": "threshold", "expression": "A",
                       "conditions": [{"evaluator": {"type": "gt", "params": [above]}}]}},
        ],
        "noDataState": "OK",
        "execErrState": "OK",
        "for": "0s",
        "labels": labels,
        "annotations": {"summary": summary, "description": description,
                        "runbook_url": f"docs/runbooks/{labels.get('event', title)}.md"},
        "isPaused": False,
    }


def render(rules: list[Rule]) -> str:
    doc = {"apiVersion": 1, "groups": [{
        "orgId": 1, "name": "security-events", "folder": "다온마켓 경보", "interval": "30s",
        "rules": [grafana_rule(r) for r in rules],
    }]}
    return HEADER + yaml.safe_dump(doc, allow_unicode=True, sort_keys=False, width=200)


def build(md_path: Path = EVENTS_MD) -> tuple[str, list[Rule], list[Skipped]]:
    rules, skipped = parse(md_path.read_text("utf-8"))
    return render(rules), rules, skipped


def main() -> int:
    text, rules, skipped = build()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, "utf-8")
    print(f"경보 규칙 {len(rules)}개를 만들었습니다: {OUT.relative_to(ROOT)}")
    for r in rules:
        print(f"  {r.severity}  {r.event:<38} 같은 {r.by} {_window(r.window_min):>4} 안 {r.threshold}건 이상")
    if skipped:
        print(f"\n규칙으로 바꾸지 않은 줄 {len(skipped)}개(사람이 판단하거나 손으로 쓴 규칙 파일에 있음):")
        for s in skipped:
            print(f"  {s.event:<38} {s.why} · 「{s.text.replace('`', '')[:50]}」")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
