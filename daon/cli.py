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


def _incidents_without_evidence() -> list[str]:
    """14-4: 사고 기록 폴더(.daon/incident/<번호>/) 가운데 증거 목록(evidence.sha256)이 아직 없는 것"""
    base = course.DATA / "incident"
    return sorted(d.name for d in base.iterdir() if d.is_dir() and not (d / "evidence.sha256").exists()) if base.exists() else []


def cmd_down(a):
    from daon import snapshot
    pending = _incidents_without_evidence() if a.wipe else []
    if pending and not a.force:
        raise SystemExit(f"사고 중에는 wipe 금지(14-4 런북 첫 줄): 증거를 모으지 않은 사고 기록이 있습니다({', '.join(pending)}).\n"
                         f"먼저 make evidence INCIDENT={pending[0]} · 그래도 지우려면 make wipe FORCE=1")
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


def cmd_scan_secrets(a):
    """6-2: 작업 트리·git 이력·도커 이미지에서 시크릿 찾기. 값은 찍지 않고 규칙·파일·건수만 보여 준다(gitleaks)."""
    from daon import secretscan as ss
    use = a.rules or "auto"
    try:
        if a.image:
            found = ss.scan_image(a.image, use)
            where = f"이미지 {a.image}의 설정 파일"
        elif a.history:
            found = ss.scan_history(use)
            where = "git 이력 전체"
        else:
            found = ss.scan_tree(use)
            where = "작업 트리(" + ", ".join(ss.TREE_ROOTS) + ")"
    except ss.ScanUnavailable as e:
        raise SystemExit(str(e))
    print(f"시크릿 점검 · {where} · 규칙 {a.rules or ('.gitleaks.toml' if ss.CONFIG_LAB.exists() else '기준 규칙')}")
    print(f"찾은 곳: {len(found)}건 (값은 가렸습니다)")
    print("\n".join(ss.summarize(found)))
    if a.fail and found:
        sys.exit(1)


def cmd_hooks(a):
    """6-2: 커밋 전 시크릿 점검 훅(pre-commit) 설치."""
    import shutil
    src = ROOT / "tools" / "hooks" / "pre-commit"
    if not src.exists():
        raise SystemExit("훅 파일(tools/hooks/pre-commit)이 아직 없습니다. 6-2에서 만드는 파일입니다.")
    dest = ROOT / ".git" / "hooks" / "pre-commit"
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dest)
    dest.chmod(0o755)
    print(f"설치했습니다: {dest.relative_to(ROOT)} (커밋 전에 시크릿을 점검합니다)")


def cmd_dump_db(a):
    """랩 전용(가짜 시드): DB·캐시에 개인정보가 평문으로 읽히는지 건수만 센다(5-3). 값은 찍지 않는다"""
    from replay import t14_pii_at_rest as t14
    lines, leaked = t14.summary()
    print("\n".join(lines))
    print(f"평문으로 읽히는 곳: {leaked}")


def cmd_peek(a):
    """랩 전용(5-3·5-4 영상용): 관리자 응답을 가짜 회원(alice·bob)만 골라 보여 준다. 원본 데모 회원의 번호는 찍지 않는다.
    list = 회원 목록 · search 번호 = 번호로 찾기 · order ID = 주문 상세의 수령인 번호 · reveal ID [사유] = 원문 보기"""
    from replay._http import ADMINS, admin
    from replay.t09_login_guard import clear_counters
    with admin() as c:
        clear_counters()
        t = c.post("/admin/login", json={"username": "admin", "password": ADMINS["admin"]["password"]}).json()["data"]["token"]
        h = {"Authorization": "Bearer " + t}
        if a.what == "list":
            for r in c.get("/daon/customer/list", headers=h).json()["data"]:
                if r["username"] in ("alice", "bob"):
                    print(f'{r["username"]:6} phone={r["phone"]}')
        elif a.what == "search":
            rows = c.get("/daon/customer/search", params={"phone": a.arg}, headers=h).json()["data"]
            print(f"검색 {a.arg} → {len(rows)}건")
            for r in rows:
                if r["username"] in ("alice", "bob"):
                    print(f'{r["username"]:6} phone={r["phone"]}')
        elif a.what == "order":
            o = c.get(f"/order/{a.arg}", headers=h).json()["data"]
            print(f'주문 {a.arg} 수령인 전화 = {o["receiverPhone"]}')
        elif a.what == "reveal":
            params = {"reason": a.reason} if a.reason else {}
            r = c.get(f"/daon/customer/{a.arg}/phone", params=params, headers=h)
            print(f"HTTP {r.status_code}" + (f' {r.json()["data"]}' if r.status_code == 200 else f' {r.json().get("message")}'))


def cmd_rotate_key(a):
    """랩 전용(5-2): 토큰 서명 키(OpenBao transit daon-jwt)를 회전·폐기·되돌린다. 키 값은 찍지 않고 버전만 보인다.
    rotate = 새 버전을 만든다(이후 새 토큰은 새 버전, 옛 토큰도 겹침 기간 동안은 통한다)
    retire = 겹침이 끝났다 → 가장 최근 버전 아래는 폐기(옛 토큰 거절)
    reset  = 폐기 해제(시험이 끝나고 처음 상태로)
    status = 버전만 보기"""
    import subprocess
    if a.key != "jwt":
        raise SystemExit("지금은 K=jwt만 됩니다")
    script = {
        "rotate": "bao write -f transit/keys/daon-jwt/rotate >/dev/null",
        "retire": 'L=$(bao read -field=latest_version transit/keys/daon-jwt); bao write transit/keys/daon-jwt/config min_decryption_version=$L >/dev/null',
        "reset": "bao write transit/keys/daon-jwt/config min_decryption_version=1 >/dev/null",
        "status": ":",
    }[a.action]
    show = ('echo "latest_version=$(bao read -field=latest_version transit/keys/daon-jwt)'
            ' min_decryption_version=$(bao read -field=min_decryption_version transit/keys/daon-jwt)"')
    full = ("export BAO_ADDR=http://openbao:8200; "
            "export BAO_TOKEN=$(tr -d '\\n ' < /bao-lab/init.json | sed -n 's/.*\"root_token\":\"\\([^\"]*\\)\".*/\\1/p'); "
            f"{script}; {show}")
    net = next((n for n in ("daon_admin", "daon_daon") if subprocess.run(["docker", "network", "inspect", n], capture_output=True).returncode == 0), "daon_daon")   # 6-4 뒤에는 admin 망(OpenBao가 있는 바깥으로 열린 망)
    r = subprocess.run(["docker", "run", "--rm", "--network", net, "-v", "daon_bao-lab:/bao-lab:ro",
                        "--entrypoint", "/bin/sh", "openbao/openbao:2.4.1", "-c", full], capture_output=True, text=True, timeout=60)
    if r.returncode:
        raise SystemExit("OpenBao에 닿지 않습니다(make up STACK=spring)")
    print(r.stdout.strip())


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
    from daon import lab, resilience
    lab.warn_if_off()
    resilience.warn_if_faulted()
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


TOTAL_T = 37   # 2026-11-07 T37(가격 신뢰, 점검표 F45) 추가


def cmd_score(a):
    import replay
    stack = a.stack or current_stack()
    st.ensure_fresh(stack)
    rows, red = [], 0
    for t, mod in replay.all_modules().items():
        try:
            # T20은 Grafana의 평가·메일 묶음 대기 뒤에야 판정할 수 있다.
            fast = "dynamic_run" in mod.run.__code__.co_varnames[:mod.run.__code__.co_argcount] and t != "T20"
            r = mod.run(stack, **({"dynamic_run": False} if fast else {}))
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
    from daon import lab
    if lab.off_switches():
        raise SystemExit(f"꺼진 방어 스위치가 있습니다({', '.join(lab.off_switches())}). make switch K=all V=on 뒤에 다시 실행하세요.")
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
            if l["no"] == "10-2":
                # 시작 시험은 의도적으로 같은 쿠폰 이력을 여러 행 만든다.
                # 완료 코드가 유니크 제약을 걸기 전에 재연 데이터만 되돌린다.
                from daon.money import cmd_money_reset
                cmd_money_reset(a)
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
    add("down", cmd_down, stack_opt, (["--wipe"], {"action": "store_true"}), (["--hard"], {"action": "store_true"}), (["--force"], {"action": "store_true"}))
    add("ps", cmd_ps, stack_opt)
    add("logs", cmd_logs, (["service"], {"nargs": "?"}))
    add("mem", cmd_mem)
    add("dump-db", cmd_dump_db)
    add("scan-secrets", cmd_scan_secrets, (["--history"], {"action": "store_true"}), (["--image"], {}),
        (["--rules"], {"choices": ["default", "reference"]}), (["--fail"], {"action": "store_true"}))
    add("hooks", cmd_hooks)
    add("peek", cmd_peek, (["what"], {"choices": ["list", "search", "order", "reveal"]}), (["arg"], {"nargs": "?"}), (["reason"], {"nargs": "?"}))
    add("rotate-key", cmd_rotate_key, (["key"], {"nargs": "?", "default": "jwt"}), (["action"], {"nargs": "?", "default": "rotate", "choices": ["rotate", "retire", "reset", "status"]}))
    add("goto", cmd_goto, (["lesson"], {}), (["at"], {"nargs": "?", "default": ""}))
    add("lesson", cmd_lesson, (["lesson"], {}))
    add("status", cmd_status)
    add("check", cmd_check, (["lesson"], {"nargs": "?"}), stack_opt)
    add("solution", cmd_solution, (["lesson"], {}))
    add("apply-solution", cmd_apply_solution, (["lesson"], {}))
    add("replay", cmd_replay, (["t"], {"nargs": "?"}), stack_opt)
    add("score", cmd_score, stack_opt)
    add("verify-course", cmd_verify_course, stack_opt, (["--only"], {}))
    from daon import guide                    # 따라 하기 도우미: make where · next · doctor
    guide.register(add, stack_opt)
    from daon import lab                      # 방어 스위치(make switch)·랩 상황판(make lab)
    lab.register(add, stack_opt)
    from daon import supply                   # 섹션 7: SBOM·KEV·원본 패치·로컬 CI·배포 확인
    supply.register(add, stack_opt)
    from daon import observe                  # 섹션 8: 기록과 경보(A5)
    observe.register(add, stack_opt)
    from daon import resilience               # 섹션 9: 스텁 장애·측정·설정 점검(A6)
    resilience.register(add, stack_opt)
    from daon import money                    # 섹션 10: 돈이 오가는 기능(원장 깨기·대사·랩 값 되돌리기)
    money.register(add, stack_opt)
    from daon import boundary                 # 섹션 11: 두 번 해석되는 요청(설정 점검·캐시 비우기, A7)
    boundary.register(add, stack_opt)
    from daon import partner                  # 섹션 12: 파트너 입구(A9)·인증서·연동 앱 대장·일괄 폐기
    partner.register(add, stack_opt)
    from daon import approval                 # 섹션 13: 승인 서비스(A10)·고액 환불·권한 상승
    approval.register(add, stack_opt)
    from daon import backup                   # 섹션 14: 불변 백업(A11)·복구 훈련
    backup.register(add, stack_opt)
    from daon import incident                 # 섹션 14: 증거 보존(make evidence). 모의 침해 재생은 make drill D=14-4
    incident.register(add, stack_opt)
    from daon import timeline                 # 섹션 14: 사고 타임라인(python -m daon timeline)
    timeline.register(add, stack_opt)
    from daon import capstone                 # 섹션 15: 캡스톤(make capstone PART=1|status|answer, RESET=1)
    capstone.register(add, stack_opt)
    from daon import audit                    # 섹션 15: 최종 점검표 74행(make audit)·점수판 비교(make final-score)
    audit.register(add, stack_opt)
    from daon import kit                      # 섹션 15: secure-kit 예제 앱(make kit-up·kit-down·kit-contract)
    kit.register(add, stack_opt)
    from daon import responder                # 섹션 15: 사고 대응 랩 도구(daon keys·tokens·gitea·incident)
    responder.register(add, stack_opt)
    a = p.parse_args(argv)
    a.fn(a)
