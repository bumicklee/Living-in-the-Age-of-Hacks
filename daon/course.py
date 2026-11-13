"""수업 목록과 수업 이동(book-3 cs-crew와 같은 방식: git 태그 대신 바뀐 파일만 덮어쓰기).

- course/base/        강의 시작 상태. 수업이 고치는 파일만 저장소와 같은 경로로 둔다(예: course/base/spring/mall-portal/...).
- solutions/<수업>/   그 수업을 마친 모습. 바뀐 파일 **전체**를 같은 경로로 둔다.
- goto L=x            base를 깔고, x 앞 수업들의 solutions를 차례로 덮는다(AT=done이면 x까지).
                      base에는 없고 solutions에만 있는 파일(수업에서 새로 만드는 파일)은 먼저 지운다.
"""
from __future__ import annotations

import json
import os
import re
import shutil
from pathlib import Path

import yaml

from daon.config import DATA, ROOT

COURSE = ROOT / "course"
BASE = COURSE / "base"
# 수업 정답은 저장소 안 solutions/에 둔다. 시험용으로 다른 폴더를 쓰려면 DAON_SOLUTIONS로 바꾼다.
SOLUTIONS = Path(os.environ.get("DAON_SOLUTIONS", ROOT / "solutions")).resolve()
CARDS = ROOT / "lessons"
PROGRESS = DATA / "progress.json"
SKIP = {"__pycache__", ".DS_Store"}


def norm(lesson: str) -> str:
    """'2-2', '02-02', 's2-02' → 's2-02' · 섹션 미션 노트: '2-mission', '2-m', '2-미션' → 's2-mission'"""
    text = str(lesson).strip().lower()
    m = re.fullmatch(r"s?0*(\d+)-(mission|m|미션)", text)
    if m:
        return f"s{int(m.group(1))}-mission"
    m = re.fullmatch(r"s?0*(\d+)-0*(\d+)", text)
    if not m:
        raise SystemExit(f"수업 번호는 2-2처럼 씁니다(섹션 미션은 2-mission): {lesson}")
    return f"s{int(m.group(1))}-{int(m.group(2)):02d}"


def short(lid: str) -> str:
    sec, no = lid[1:].split("-")
    return f"{int(sec)}-{no}" if no == "mission" else f"{int(sec)}-{int(no)}"


def sections() -> list[dict]:
    return yaml.safe_load((COURSE / "lessons.yaml").read_text("utf-8"))


def lessons() -> list[dict]:
    return [l | {"section": s["section"]} for s in sections() for l in s["lessons"]]


def find(lid: str) -> dict:
    lid = norm(lid)
    for l in lessons():
        if l["id"] == lid:
            return l
    raise SystemExit(f"없는 수업입니다: {short(lid)}")


def order() -> list[str]:
    return [l["id"] for l in lessons()]


def tests_for(lid: str) -> list[Path]:
    return sorted((ROOT / "tests").glob(f"test_{norm(lid).replace('-', '_')}_*.py"))


def _files(d: Path) -> list[Path]:
    return sorted(f for f in d.rglob("*") if f.is_file() and not (SKIP & set(f.parts)))


def managed_paths() -> set[Path]:
    """수업 이동이 손대는 저장소 경로 전부(base + 모든 solutions)."""
    out = {f.relative_to(BASE) for f in _files(BASE)} if BASE.exists() else set()
    if SOLUTIONS.exists():
        for sol in SOLUTIONS.iterdir():
            if sol.is_dir():
                out |= {f.relative_to(sol) for f in _files(sol)}
    return out


def goto(lid: str, *, done: bool = False, root: Path = ROOT) -> list[str]:
    target = norm(lid)
    ids = order()
    if target not in ids:
        raise SystemExit(f"없는 수업입니다: {short(target)}")
    stop = ids.index(target) + (1 if done else 0)
    base_files = {f.relative_to(BASE) for f in _files(BASE)} if BASE.exists() else set()
    for rel in managed_paths() - base_files:            # 수업에서 새로 만드는 파일은 시작 상태에 없다
        (root / rel).unlink(missing_ok=True)
    for rel in base_files:
        dest = root / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(BASE / rel, dest)
    applied = []
    for i in ids[:stop]:
        sol = SOLUTIONS / i
        if sol.exists():
            for f in _files(sol):
                dest = root / f.relative_to(sol)
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, dest)
            applied.append(i)
    set_position(target, done)
    return applied


def set_position(lid: str, done: bool) -> None:
    DATA.mkdir(exist_ok=True)
    PROGRESS.write_text(json.dumps({"lesson": norm(lid), "done": done}), "utf-8")


def position() -> dict | None:
    try:
        return json.loads(PROGRESS.read_text("utf-8"))
    except (FileNotFoundError, ValueError):
        return None


def card(lid: str) -> str:
    path = CARDS / f"{norm(lid)}.md"
    if path.exists():
        return path.read_text("utf-8")
    l = find(lid)
    return f"# {l['no']} {l['title']}\n\n(이 수업의 따라 하기 카드는 아직 준비 중입니다.)\n"
