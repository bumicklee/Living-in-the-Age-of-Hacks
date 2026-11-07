"""명령 모음. Makefile이 부르고, make가 없으면 `python -m daon <명령>`으로 같은 일을 한다."""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from daon import course, stack as st
from daon.config import ROOT, current_stack

PY = sys.executable


def _pytest(paths: list[Path], *extra: str, stack: str) -> int:
    env = os.environ | {"DAON_STACK": stack}
    return subprocess.call([PY, "-m", "pytest", *map(str, paths), *extra], cwd=ROOT, env=env)


# ── 띄우기 ──
def cmd_up(a):
    from daon import snapshot
    s = a.stack or current_stack()
    snapshot.mark_if_new_volume(s)          # DB 볼륨을 새로 만들면 시드가 막 들어간 상태 → 초기화용 스냅샷을 뜬다(A12)
    st.up(s, build=a.build)
    snapshot.take_if_pending(s)
    if s == "spring":
        print("고객 웹    http://127.0.0.1:{}  (alice / daon-pass-1!)".format(st.ENV.get("DAON_WEB_PORT", 8088)))
        print("고객 API  http://127.0.0.1:{}/swagger-ui.html".format(st.ENV.get("DAON_PORTAL_API_PORT", 8085)))
        print("관리자 API http://127.0.0.1:{}/swagger-ui.html".format(st.ENV.get("DAON_ADMIN_API_PORT", 8080)))
        print("관리자 화면 http://127.0.0.1:{}  (admin / macro123)".format(st.ENV.get("DAON_ADMIN_WEB_PORT", 8090)))
    else:
        print("EverShop  http://127.0.0.1:{}  관리자 /admin".format(st.ENV.get("DAON_EVERSHOP_PORT", 3000)))
    print("가짜 메일   http://127.0.0.1:{}  · 가짜 결제·문자·택배: stubs/README.md".format(st.ENV.get("DAON_MAILPIT_PORT", 8025)))


def cmd_down(a):
    from daon import snapshot
    if a.wipe and not a.hard and snapshot.fast_wipe(a.stack):
        return                              # 스냅샷으로 몇 초 만에 시드 상태로(A12). 컨테이너는 그대로
    st.down(a.stack, wipe=a.wipe)
    if a.wipe:
        for s in ([a.stack] if a.stack else st.STACKS):
            snapshot.mark_pending(s)
        print("볼륨까지 지웠습니다. 다음 make up 때 시드를 새로 넣고, 초기화용 스냅샷을 뜹니다.")


def cmd_ps(a):
    st.compose(a.stack, "ps")


def cmd_logs(a):
    st.compose(None, "logs", "--tail", "200", *( [a.service] if a.service else []))


def cmd_mem(a):
    st.mem()


# ── 수업 ──
def cmd_goto(a):
    applied = course.goto(a.lesson, done=(a.at == "done"))
    l = course.find(a.lesson)
    where = "완료 지점" if a.at == "done" else "시작 지점"
    print(f"{l['no']} {l['title']} · {where}으로 맞췄습니다. 적용한 정답: {', '.join(map(course.short, applied)) or '없음'}")
    print("앱 소스가 바뀌었으면 다음 make check·make replay 때 자동으로 다시 빌드합니다.")


def cmd_lesson(a):
    print(course.card(a.lesson))


def cmd_status(a):
    pos = course.position()
    if pos:
        print(f"지금 지점: {course.short(pos['lesson'])} {'완료' if pos['done'] else '시작'}")
    for s in course.sections():
        print(f"\n섹션 {s['section']}. {s['title']}")
        for l in s["lessons"]:
            mark = "시험 있음" if course.tests_for(l["id"]) else ""
            print(f"  {l['no']:>5}  {l['title']}  {mark}")


def cmd_check(a):
    stack = a.stack or current_stack()
    st.ensure_fresh(stack)
    tests = course.tests_for(a.lesson) if a.lesson else sorted((ROOT / "tests").glob("test_*.py"))
    if not tests:
        print(f"{a.lesson} 수업은 시험이 없습니다(사고·원리 수업). make replay로 화면을 보세요.")
        return
    sys.exit(_pytest(tests, stack=stack))


def cmd_replay(a):
    import replay
    stack = a.stack or current_stack()
    st.ensure_fresh(stack)
    mods = replay.all_modules() if a.t in (None, "", "all") else {"x": replay.get(a.t)}
    for mod in mods.values():
        print(f"\n{mod.T} {mod.TITLE}  ({stack}, 막는 수업 {mod.LESSON})")
        try:
            print(mod.run(stack).show())
        except replay.NotReady as e:
            print(f"[준비 안 됨] {e}")


def cmd_solution(a):
    import difflib
    lid = course.norm(a.lesson)
    sol = course.SOLUTIONS / lid
    if not sol.exists():
        print(f"{course.short(lid)} 수업은 정답 파일이 없습니다.")
        return
    for f in course._files(sol):
        rel = f.relative_to(sol)
        mine = (ROOT / rel).read_text("utf-8").splitlines(keepends=True) if (ROOT / rel).exists() else []
        diff = list(difflib.unified_diff(mine, f.read_text("utf-8").splitlines(keepends=True), f"내 파일/{rel}", f"정답/{rel}"))
        sys.stdout.writelines(diff or [f"같음: {rel}\n"])


def cmd_apply_solution(a):
    import shutil
    lid = course.norm(a.lesson)
    sol = course.SOLUTIONS / lid
    if not sol.exists():
        raise SystemExit(f"{course.short(lid)} 수업은 정답 파일이 없습니다.")
    backup = course.DATA / "backup" / time.strftime("%Y%m%d-%H%M%S")
    for f in course._files(sol):
        rel = f.relative_to(sol)
        if (ROOT / rel).exists():
            (backup / rel).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / rel, backup / rel)
        (ROOT / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(f, ROOT / rel)
        print(f"정답으로 덮음: {rel}")
    print(f"내 파일은 {backup.relative_to(ROOT)}에 남겼습니다.")


TOTAL_T = 36


def cmd_score(a):
    import replay
    stack = a.stack or current_stack()
    st.ensure_fresh(stack)
    rows, red = [], 0
    for t, mod in replay.all_modules().items():
        try:
            r = mod.run(stack)
            state = "레드(취약)" if r.vulnerable else "그린(막힘)"
            red += r.vulnerable
        except replay.NotReady as e:
            state = f"준비 안 됨: {e}"
        rows.append({"T": t, "title": mod.TITLE, "state": state})
    print(f"다온마켓 점수판 · {stack} · {time.strftime('%Y-%m-%d %H:%M')}")
    for r in rows:
        print(f"  {r['T']}  {r['state']:<10}  {r['title']}")
    made = len(rows)
    print(f"\n취약 {red}개 / 재연을 만든 {made}개 (전체 목록 {TOTAL_T}개, 나머지 {TOTAL_T - made}개는 재연 준비 중)")
    out = ROOT / "scoreboard" / f"{stack}.json"
    out.write_text(json.dumps({"stack": stack, "at": time.strftime("%F %T"), "red": red, "made": made,
                               "total": TOTAL_T, "rows": rows}, ensure_ascii=False, indent=2), "utf-8")


def cmd_verify_course(a):
    """강사용: 시험이 있는 수업마다 시작 지점=레드(핵심 시험 하나하나), 완료 지점=그린인지. 랩이 떠 있어야 한다."""
    stack = a.stack or current_stack()
    saved = course.position()
    targets = [l for l in course.lessons() if course.tests_for(l["id"])]
    if a.only:
        targets = [l for l in targets if l["id"] == course.norm(a.only)]
    problems = []
    try:
        for l in targets:
            tests = course.tests_for(l["id"])
            normal = _collect(tests, "normal", stack)
            course.goto(l["id"], done=False)
            st.ensure_fresh(stack)
            start = _outcomes(tests, stack)
            course.goto(l["id"], done=True)
            st.ensure_fresh(stack)
            end = _outcomes(tests, stack)
            bad_start = [t for t, ok in start.items() if ok and t not in normal]
            bad_norm = [t for t in normal if not start.get(t)]
            bad_end = [t for t, ok in end.items() if not ok]
            ok = not (bad_start or bad_norm or bad_end) and start
            print(f"{l['no']:>5}  {'통과' if ok else '문제'}  시작 {sum(not v for v in start.values())}레드/{len(start)} · 완료 {sum(end.values())}그린/{len(end)}")
            for t in bad_start:
                problems.append(f"{l['no']} 시작 지점에서 이미 그린: {t}")
            for t in bad_norm:
                problems.append(f"{l['no']} 정상 요청 시험이 시작 지점에서 레드: {t}")
            for t in bad_end:
                problems.append(f"{l['no']} 완료 지점에서 레드: {t}")
            if not start:
                problems.append(f"{l['no']} 시험을 찾지 못함")
        if targets and not a.only:
            last = targets[-1]["id"]
            course.goto(last, done=True)
            st.ensure_fresh(stack)
            full = _outcomes(sorted((ROOT / "tests").glob("test_*.py")), stack)
            failed = [t for t, ok in full.items() if not ok]
            print(f"전체 시험(마지막 수업 완료 지점): {len(full) - len(failed)}/{len(full)} 그린")
            problems += [f"회귀: {t}" for t in failed]
    finally:
        if saved:
            course.goto(saved["lesson"], done=saved["done"])
        else:
            course.goto(course.order()[0])
    if problems:
        print("\n".join(["", "문제:"] + [f"  - {p}" for p in problems]))
        sys.exit(1)
    print(f"\nverify-course 그린: {len(targets)}개 수업 ({stack})")


def _collect(tests, marker, stack) -> set[str]:
    r = subprocess.run([PY, "-m", "pytest", "--collect-only", "-m", marker, *map(str, tests)],
                       cwd=ROOT, text=True, capture_output=True, env=os.environ | {"DAON_STACK": stack})
    return {line.strip() for line in r.stdout.splitlines() if "::" in line}


def _outcomes(tests, stack) -> dict[str, bool]:
    with tempfile.TemporaryDirectory() as d:
        xml = Path(d) / "r.xml"
        subprocess.run([PY, "-m", "pytest", "-q", f"--junitxml={xml}", *map(str, tests)], cwd=ROOT,
                       capture_output=True, env=os.environ | {"DAON_STACK": stack})
        out = {}
        for tc in ET.parse(xml).iter("testcase"):
            f = tc.get("file") or tc.get("classname", "").replace(".", "/") + ".py"
            node = f"{f}::{tc.get('name')}"
            out[node] = not any(ch.tag in ("failure", "error", "skipped") for ch in tc)
        return out


def main(argv=None):
    p = argparse.ArgumentParser(prog="daon", description="다온마켓 랩 도구")
    sub = p.add_subparsers(dest="cmd", required=True)

    def add(name, fn, *args):
        sp = sub.add_parser(name)
        for a in args:
            sp.add_argument(*a[0], **a[1])
        sp.set_defaults(fn=fn)

    stack_opt = (["--stack"], {"choices": ["spring", "express"]})
    add("up", cmd_up, stack_opt, (["--build"], {"action": "store_true"}))
    add("down", cmd_down, stack_opt, (["--wipe"], {"action": "store_true"}), (["--hard"], {"action": "store_true"}))
    add("ps", cmd_ps, stack_opt)
    add("logs", cmd_logs, (["service"], {"nargs": "?"}))
    add("mem", cmd_mem)
    add("goto", cmd_goto, (["lesson"], {}), (["at"], {"nargs": "?", "default": ""}))
    add("lesson", cmd_lesson, (["lesson"], {}))
    add("status", cmd_status)
    add("check", cmd_check, (["lesson"], {"nargs": "?"}), stack_opt)
    add("solution", cmd_solution, (["lesson"], {}))
    add("apply-solution", cmd_apply_solution, (["lesson"], {}))
    add("replay", cmd_replay, (["t"], {"nargs": "?"}), stack_opt)
    add("score", cmd_score, stack_opt)
    add("verify-course", cmd_verify_course, stack_opt, (["--only"], {}))
    a = p.parse_args(argv)
    a.fn(a)
