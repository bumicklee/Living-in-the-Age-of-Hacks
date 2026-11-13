"""따라 하기 도우미: make where · make next · make doctor.

- where : 지금 수업 지점과 그 수업의 카드 보는 법
- next  : 지금 지점에서 다음에 칠 명령(시작 지점이면 레드 확인 → 고칠 파일 → 확인, 완료 지점이면 다음 수업)
- doctor: 랩이 안 될 때 먼저 볼 것(Docker·메모리·디스크·설정 파일·컨테이너·방어 스위치)을 점검하고 해결 방법을 알려 준다
명령과 고칠 파일은 수업 카드(lessons/sN-NN.md)의 「따라 하기」에서 읽는다. 카드가 정본이다.
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess

from daon import course
from daon.config import ROOT

FENCE = re.compile(r"```(\w*)\n(.*?)```", re.S)


def _card_steps(lid: str) -> dict:
    """카드의 「따라 하기」에서 레드 확인 명령(첫 명령 묶음), 고칠 파일(2단계부터 처음 나오는 파일 경로),
    확인 명령(마지막 명령 묶음)을 꺼낸다. 명령 묶음이 하나뿐이면 그것을 레드 확인으로 본다."""
    text = course.card(lid)
    part = text.split("## 따라 하기", 1)[1] if "## 따라 하기" in text else ""
    part = re.split(r"\n## ", part, maxsplit=1)[0]
    blocks = []
    for m in FENCE.finditer(part):
        if m.group(1) not in ("", "bash", "sh"):
            continue
        cmds = [l.strip() for l in m.group(2).splitlines() if l.strip().startswith("make ")]
        if cmds:
            blocks.append(cmds)
    fix = None
    steps = re.split(r"\n(?=\d+\. \*\*)", part)
    for st in steps[2:] if len(steps) > 2 else steps[1:]:
        body = FENCE.sub("", st)
        m = re.search(r"`((?:spring|express|infra|docs|packages|tools|replay)/[^`\s]+)`", body)
        if m:
            fix = m.group(1)
            break
    lines = text.splitlines()
    head = lines[1] if len(lines) > 1 and lines[1].startswith("⏱") else ""
    first = blocks[0] if blocks else []
    # 확인 명령: 「확인합니다」 단계의 묶음 → make check가 든 마지막 묶음 → 마지막 묶음 순으로 고른다
    last = []
    for st in reversed(steps[1:]):
        if re.match(r"\d+\. \*\*[^*]*확인", st.strip()):
            got = [c for m in FENCE.finditer(st) if m.group(1) in ("", "bash", "sh")
                   for c in (l.strip() for l in m.group(2).splitlines()) if c.startswith("make ")]
            if got:
                last = got
                break
    if not last and len(blocks) > 1:
        with_check = [b for b in blocks[1:] if any(c.startswith("make check") for c in b)]
        last = with_check[-1] if with_check else blocks[-1]
    if first and not last:   # 명령 묶음이 하나뿐인 카드: 고친 뒤에는 같은 시험·재연을 다시 돌린다
        last = [c.split("#")[0].strip() for c in first if c.startswith(("make check", "make replay"))]
    return {"first": first, "last": last, "fix": fix, "head": head}


def _here() -> tuple[dict | None, dict | None]:
    pos = course.position()
    if not pos:
        return None, None
    return pos, course.find(pos["lesson"])


def cmd_where(a):
    pos, l = _here()
    if not pos:
        print("아직 수업 지점이 없습니다. 처음이라면: make goto L=2-1")
        return
    print(f"지금 지점: {l['no']} {'완료' if pos['done'] else '시작'} · {l['title']}")
    print(f"이 수업 카드: make lesson L={l['no']}")
    print("다음에 칠 명령: make next")


def cmd_next(a):
    pos, l = _here()
    if not pos:
        print("수업 지점이 없습니다. 첫 실습 수업으로 갑니다: make goto L=2-1")
        return
    lid = course.norm(pos["lesson"])
    if pos["done"]:
        ids = course.order()
        nxt = ids[ids.index(lid) + 1] if lid in ids and ids.index(lid) + 1 < len(ids) else None
        if not nxt:
            print("마지막 수업까지 왔습니다. 최종 점수판: make score")
            return
        n = course.find(nxt)
        print(f"{l['no']} 수업은 완료 지점입니다. 다음 수업: {n['no']} {n['title']}")
        print(f"  make goto L={n['no']}")
        print(f"  make lesson L={n['no']}   # 수업 카드")
        return
    s = _card_steps(lid)
    print(f"{l['no']} {l['title']}" + (f"  ({s['head'].lstrip('⏱ ')})" if s["head"] else ""))
    if not (s["first"] or s["fix"] or s["last"]):
        if not (course.CARDS / f"{lid}.md").exists() and l["no"].startswith("4-"):
            print(f"  4장 수업은 교재 4.{l['no'].split('-')[1]}절의 「따라 하기」를 그대로 따라 합니다. 시작: make goto L={l['no']}")
        else:
            print("  이 수업은 따라 하기 명령이 없는 설명 수업입니다. 카드를 읽어 보세요: make lesson L=" + l["no"])
        return
    if s["first"]:
        print("① 먼저 지금 상태(레드)를 봅니다")
        for c in s["first"]:
            print(f"  {c}")
    if s["fix"]:
        mark = f"  (✏️ {l['no']} 표시를 찾으세요)" if s["fix"].endswith((".java", ".ts", ".tsx", ".js", ".vue", ".xml", ".py", ".conf")) else ""
        print(f"② 고칠 곳: {s['fix']}{mark}. 자세한 순서는 make lesson L={l['no']}")
        print(f"   막히면 정답과 비교: make solution L={l['no']}")
    elif s["first"] and (course.SOLUTIONS / lid).exists():
        print(f"② 고칠 곳과 순서: make lesson L={l['no']} 의 「따라 하기」 2단계부터")
        print(f"   막히면 정답과 비교: make solution L={l['no']}")
    if s["last"]:
        print("③ 고친 뒤 확인합니다")
        for c in s["last"]:
            print(f"  {c}")


def _docker(*args: str) -> subprocess.CompletedProcess | None:
    exe = shutil.which("docker") or "/Applications/Docker.app/Contents/Resources/bin/docker"
    try:
        return subprocess.run([exe, *args], capture_output=True, text=True, timeout=20)
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return None


def cmd_doctor(a):
    problems = 0

    def say(ok: bool, msg: str, fix: str = ""):
        nonlocal problems
        print(f"{'✔' if ok else '✘'} {msg}")
        if not ok:
            problems += 1
            if fix:
                print(f"   해결: {fix}")

    if not shutil.which("docker"):
        print("! docker 명령을 PATH에서 찾지 못했습니다. Docker Desktop을 쓰면 터미널에서 다음을 한 번 실행하세요:")
        print('   export PATH=/Applications/Docker.app/Contents/Resources/bin:$PATH')
    info = _docker("info", "--format", "{{json .}}")
    if not info or info.returncode != 0:
        say(False, "Docker가 실행 중이 아닙니다", "Docker Desktop(또는 OrbStack·Rancher Desktop)을 켠 뒤 다시 make doctor")
        print(f"\n문제 {problems}개")
        return
    d = json.loads(info.stdout or "{}")
    say(True, f"Docker 실행 중 (버전 {d.get('ServerVersion', '?')})")
    mem_gb = (d.get("MemTotal") or 0) / 1024 ** 3
    say(mem_gb >= 7.5, f"Docker에 준 메모리 {mem_gb:.1f}GB (권장 8GB 이상, 여유가 있으면 12GB)",
        "Docker Desktop → Settings → Resources → Memory를 올린 뒤 make up STACK=spring")
    free_gb = shutil.disk_usage(ROOT).free / 1024 ** 3
    say(free_gb >= 10, f"디스크 여유 {free_gb:.0f}GB (10GB 이상 필요)", "docker system prune 으로 쓰지 않는 이미지를 지우거나 공간을 확보")
    say((ROOT / "infra" / ".env").exists(), "설정 파일 infra/.env 있음", "make setup")
    ps = _docker("ps", "-a", "--filter", "name=daon-", "--format", "{{.Names}}\t{{.State}}\t{{.Status}}")
    rows = [r.split("\t") for r in (ps.stdout.strip().splitlines() if ps and ps.returncode == 0 else []) if r]
    if not rows:
        say(False, "다온마켓 컨테이너가 하나도 없습니다", "make up STACK=spring (처음에는 10분 넘게 걸릴 수 있습니다)")
    else:
        running = [r for r in rows if r[1] == "running"]
        unhealthy = [r[0] for r in running if "unhealthy" in r[2]]
        # 준비 작업만 하고 정상 종료(Exited (0))하는 일회용 컨테이너는 문제가 아니다
        exited = [r[0] for r in rows if r[1] == "exited" and "Exited (0)" not in r[2]]
        say(not unhealthy, f"실행 중 컨테이너 {len(running)}개" + (f" · 응답 없음: {', '.join(unhealthy)}" if unhealthy else ""),
            "make logs S=<이름> 으로 오류를 보고, make up STACK=spring 으로 다시 띄우기")
        say(not exited, "멈춘 컨테이너 없음" if not exited else f"멈춘 컨테이너: {', '.join(exited[:6])}",
            "make up STACK=spring. 같은 포트를 쓰는 다른 프로그램이 있으면 먼저 끄세요(make ps 로 포트 확인)")
    pos = course.position()
    say(pos is not None, f"수업 지점: {course.short(pos['lesson'])} {'완료' if pos['done'] else '시작'}" if pos else "수업 지점이 없습니다",
        "make goto L=2-1")
    try:
        from daon import lab
        off = lab.off_switches()
    except Exception:
        off = []
    say(not off, "꺼진 방어 스위치 없음" if not off else f"꺼진 방어 스위치: {', '.join(off)}", "make switch K=all V=on")
    print(f"\n{'문제 없음. 다음 명령은 make next' if problems == 0 else f'문제 {problems}개. 위의 해결 방법을 차례로 해 보세요'}")


def register(add, stack_opt):
    add("where", cmd_where)
    add("next", cmd_next)
    add("doctor", cmd_doctor)
