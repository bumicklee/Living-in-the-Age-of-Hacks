"""15-3 최종 점검: 최초 점검표 74행을 같은 방법으로 다시 돌리고(make audit), 시작 지점과 최종 지점의 점수판을 같은 재연으로 비교한다(make final-score).

    make audit                 course/audit.yaml 74행 · 행마다 check(재연·시험·설정·의존성·diff·문서) → 결과 표
    make audit ONLINE=1        의존성 행(F33 OSV·F34 npm audit)을 오늘 날짜로 다시 조회(인터넷 필요. 랩의 오프라인 원칙의 예외)
    make final-score           1-12 시작 지점(course/base) → 점수판, 15-3 완료 지점 → 점수판. 같은 재연 37개. 지금 지점으로 돌아온다

결과는 reports/final-check/<날짜>-<커밋 앞 8자>.{json,md}(git에 넣지 않는 reports/). 값(토큰·개인정보)은 넣지 않는다.
처리 결과 네 가지: 고침(코드·설정을 바꾸고 시험이 그린) · 완화(구조로 피해를 줄임) · 수용(고치지 않기로, 이유와 다시 볼 날) · 확인 필요(근거가 아직 없음).
「점수 0」은 「강의가 약속한 재연 37개를 막았다」이고, 「위험 0」이 아니다. 확인 필요 행이 남을 수 있다.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import time
from pathlib import Path

import yaml

from daon import course, stack as st
from daon.config import ROOT, current_stack

SHEET = ROOT / "course" / "audit.yaml"
RESULTS = ("고침", "완화", "수용", "확인 필요")
OUT = ROOT / "reports" / "final-check"


def rows(path: Path = SHEET) -> list[dict]:
    return yaml.safe_load(path.read_text("utf-8")) or []


def problems(sheet: list[dict]) -> list[str]:
    """표 자체의 빈칸(시험이 같은 함수를 쓴다)"""
    out = []
    want = [f"F{i:02d}" for i in range(1, 49)] + [f"E{i:02d}" for i in range(1, 27)]
    have = [r.get("id") for r in sheet]
    if sorted(have) != sorted(want):
        out.append(f"행 목록이 F01~F48·E01~E26(74행)과 다름: 없는 행 {sorted(set(want) - set(have))[:5]} · 남는 행 {sorted(set(have) - set(want))[:5]}")
    for r in sheet:
        rid = r.get("id")
        if r.get("result") not in RESULTS:
            out.append(f"{rid}: 처리 결과가 비었거나 네 가지 밖({r.get('result')!r})")
        if not str(r.get("evidence") or "").strip():
            out.append(f"{rid}: 근거가 비었음")
        if r.get("result") == "수용" and not re.search(r"\d{4}-\d\d-\d\d", str(r.get("residual") or "")):
            out.append(f"{rid}: 수용은 남은 위험 칸에 이유와 다시 볼 날(YYYY-MM-DD)")
        if not (r.get("check") or {}).get("kind"):
            out.append(f"{rid}: 다시 확인하는 방법(check.kind)이 없음")
    return out


def _replay(ts: list[str], stack: str) -> tuple[bool, str]:
    import replay
    states = []
    for t in ts:
        try:
            mod = replay.get(t)
            code = mod.run.__code__
            kw = {"dynamic_run": False} if "dynamic_run" in code.co_varnames[:code.co_argcount] else {}
            r = mod.run(stack, **kw)
            states.append((t, not r.vulnerable))
        except replay.NotReady as e:
            states.append((t, None))
            return False, f"{t} 준비 안 됨({str(e)[:60]})"
    return all(ok for _, ok in states), " · ".join(f"{t} {'그린' if ok else '레드'}" for t, ok in states)


def _deps(source: str, online: bool) -> tuple[bool | None, str]:
    if not online:
        return None, "조회 안 함(오프라인). make audit ONLINE=1"
    if source == "osv":
        import httpx
        sboms = sorted((ROOT / "build" / "sbom").glob("*.cdx.json"))
        if not sboms:
            return None, "SBOM 없음(make sbom 먼저)"
        purls = sorted({c["purl"] for f in sboms for c in json.loads(f.read_text("utf-8")).get("components", []) if c.get("purl")})
        try:
            r = httpx.post("https://api.osv.dev/v1/querybatch", json={"queries": [{"package": {"purl": p}} for p in purls]}, timeout=60)
            hits = sum(1 for x in r.json().get("results", []) if x.get("vulns"))
        except (httpx.HTTPError, ValueError) as e:
            return None, f"조회 못 함({e.__class__.__name__})"
        return hits == 0, f"OSV {time.strftime('%Y-%m-%d')}: 부품 {len(purls)}개 중 권고 있는 것 {hits}개(쓰는 기능 해당 여부는 사람이 본다)"
    web = ROOT / "spring" / "mall-admin-web"
    try:
        r = subprocess.run(["npm", "audit", "--json", "--package-lock-only"], cwd=web, capture_output=True, text=True, timeout=120)
        meta = json.loads(r.stdout or "{}").get("metadata", {}).get("vulnerabilities", {})
    except (OSError, ValueError, subprocess.TimeoutExpired) as e:
        return None, f"조회 못 함({e.__class__.__name__})"
    return None, f"npm audit {time.strftime('%Y-%m-%d')}: " + ", ".join(f"{k} {v}" for k, v in meta.items() if v)


def check_row(r: dict, stack: str, online: bool = False) -> tuple[bool | None, str]:
    c = r.get("check") or {}
    kind = c.get("kind")
    have_tests = [t for t in c.get("tests", []) if (ROOT / "tests" / t).exists()]
    if kind == "replay":
        ok, note = _replay(c.get("t", []), stack)
        return ok, note + (f" · 시험 {len(have_tests)}개" if have_tests else "")
    if kind == "tests":
        return (len(have_tests) == len(c.get("tests", [])) and bool(have_tests)), f"시험 파일 {len(have_tests)}/{len(c.get('tests', []))}"
    if kind == "config":
        f = ROOT / c["file"]
        ok = f.exists() and re.search(c["must"], f.read_text("utf-8")) is not None
        return ok, f"{c['file']} {'맞음' if ok else '다름'}"
    if kind == "deps":
        return _deps(c.get("source", "osv"), online)
    if kind == "diff":
        files = c.get("diffs", [])
        ok = bool(files) and all((ROOT / f).exists() for f in files)
        return (ok if files else None), (" · ".join(files) if files else "diff 없음")
    return None, "문서로 확인(사람)"


def run_audit(stack: str, online: bool = False, echo=print) -> dict:
    sheet = rows()
    out = {"at": time.strftime("%Y-%m-%d %H:%M"), "commit": _commit(), "rows": [], "sheet_problems": problems(sheet)}
    for r in sheet:
        ok, note = check_row(r, stack, online)
        bad = r.get("result") == "고침" and ok is False
        out["rows"].append({"id": r["id"], "severity": r.get("severity"), "result": r.get("result"), "check": note,
                            "confirmed": ok, "evidence": r.get("evidence"), "residual": r.get("residual"), "problem": bad})
        mark = "그린" if ok else ("레드" if ok is False else "사람")
        echo(f"  {r['id']}  {str(r.get('result') or '(빈칸)'):<6} {mark:<3} {note[:90]}")
    counts: dict[str, int] = {}
    for r in out["rows"]:
        counts[r["result"] or "(빈칸)"] = counts.get(r["result"] or "(빈칸)", 0) + 1
    out["counts"] = counts
    out["fixed_not_confirmed"] = [r["id"] for r in out["rows"] if r["problem"]]
    _write("audit", out, _audit_md(out))
    return out


def _audit_md(out: dict) -> str:
    lines = [f"# 최종 점검 결과 · {out['at']} · 커밋 {out['commit']}", "",
             "처리 결과: " + " · ".join(f"{k} {v}" for k, v in out["counts"].items()), "",
             "| ID | 처음 심각도 | 처리 결과 | 다시 확인 | 근거 | 남은 위험 |", "|---|---|---|---|---|---|"]
    for r in out["rows"]:
        lines.append(f"| {r['id']} | {r['severity']} | {r['result']} | {r['check']} | {r['evidence']} | {r['residual'] or ''} |")
    if out["sheet_problems"]:
        lines += ["", "## 표의 빈칸", ""] + [f"- {p}" for p in out["sheet_problems"]]
    return "\n".join(lines) + "\n"


def _commit() -> str:
    r = subprocess.run(["git", "rev-parse", "--short=8", "HEAD"], cwd=ROOT, capture_output=True, text=True)
    return r.stdout.strip() or "unknown"


def _write(kind: str, data: dict, md: str | None = None) -> Path:
    OUT.mkdir(parents=True, exist_ok=True)
    base = OUT / f"{kind}-{time.strftime('%Y%m%d-%H%M')}-{data.get('commit', _commit())}"
    base.with_suffix(".json").write_text(json.dumps(data, ensure_ascii=False, indent=2), "utf-8")
    if md:
        base.with_suffix(".md").write_text(md, "utf-8")
    return base


def latest(kind: str) -> dict | None:
    files = sorted(OUT.glob(f"{kind}-*.json")) if OUT.exists() else []
    return json.loads(files[-1].read_text("utf-8")) if files else None


def _score(stack: str) -> dict:
    from daon import cli
    cli.cmd_score(argparse.Namespace(stack=stack))
    return json.loads((ROOT / "scoreboard" / f"{stack}.json").read_text("utf-8"))


def final_score(stack: str, echo=print) -> dict:
    """같은 재연 37개로 시작 지점(1-12)과 최종 지점(15-3 완료)을 잰다. 앱을 두 번 다시 빌드한다(수십 분)"""
    saved = course.position()
    out: dict = {"commit": _commit(), "at": time.strftime("%Y-%m-%d %H:%M")}
    try:
        for name, lid, done in (("start", "1-12", False), ("final", "15-3", True)):
            echo(f"── {name}: make goto L={lid}{' AT=done' if done else ''}")
            course.goto(lid, done=done)
            st.ensure_fresh(stack)
            sc = _score(stack)
            out[name] = {"lesson": lid, "done": done, "red": sc["red"], "made": sc["made"], "total": sc["total"],
                         "red_list": [r["T"] for r in sc["rows"] if r["state"].startswith("레드")]}
            if name == "start":
                # 시작 상태 재연은 쿠폰 같은 시드 데이터를 바꾼다. 그 상태로 10-2의
                # 유니크 제약을 켜면 중복 행 때문에 적용되지 않으므로 먼저 되돌린다.
                from daon import snapshot
                if not snapshot.fast_wipe(stack):
                    raise RuntimeError("시작 상태 시드 스냅샷이 없습니다. 시작 지점에서 make wipe HARD=1 뒤 make up을 실행하세요")
    finally:
        if saved:
            course.goto(saved["lesson"], done=saved["done"])
    md = (f"# 점수판 비교 · {out['at']} · 커밋 {out['commit']}\n\n| 지점 | 취약 | 재연 |\n|---|---|---|\n"
          + "".join(f"| {k} ({out[k]['lesson']}) | {out[k]['red']} | {out[k]['made']}/{out[k]['total']} |\n" for k in ("start", "final") if k in out))
    _write("score", out, md)
    echo(f"시작 지점 취약 {out.get('start', {}).get('red')} / 최종 지점 취약 {out.get('final', {}).get('red')} (재연 {out.get('final', {}).get('made')}개)")
    return out


def cmd_audit(a) -> None:
    stack = a.stack or current_stack()
    out = run_audit(stack, online=a.online)
    print(f"\n처리 결과: " + " · ".join(f"{k} {v}" for k, v in out["counts"].items()))
    for p in out["sheet_problems"][:20]:
        print(f"  표 빈칸: {p}")
    if out["fixed_not_confirmed"]:
        print(f"  「고침」인데 다시 확인이 레드: {', '.join(out['fixed_not_confirmed'])}")
    print(f"결과: {OUT.relative_to(ROOT)}/")


def cmd_final_score(a) -> None:
    final_score(a.stack or current_stack())


def register(add, stack_opt) -> None:
    add("audit", cmd_audit, stack_opt, (["--online"], {"action": "store_true"}))
    add("final-score", cmd_final_score, stack_opt)
