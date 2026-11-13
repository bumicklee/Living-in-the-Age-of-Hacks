#!/usr/bin/env python3
"""CI 고정 검사(7-5): 워크플로의 외부 단계(uses:)는 40자 커밋 해시로 고정돼 있어야 하고, ci/pins.lock과 같아야 한다.

    python tools/ci/check_pins.py [워크플로 파일 …]      (기본: .gitea/workflows/*.yml)
    make ci-lint                                          (같은 검사를 PC에서)

- `@v1` 같은 태그·브랜치는 거부한다: ci.denied.unpinned_step (태그는 다른 커밋으로 옮길 수 있다)
- 해시가 pins.lock과 다르면 거부한다: ci.step.changed (누군가 락 파일 없이 해시만 바꿨다)
- 이 저장소 안의 단계(./…)와 docker:// 는 대상이 아니다.
종료 코드: 0 통과, 1 거부.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))   # tools/
from secevent import emit  # noqa: E402

PIN = re.compile(r"^[0-9a-f]{40}$")
USES = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)")


def load_lock(path: Path) -> dict[str, str]:
    lock: dict[str, str] = {}
    if path.exists():
        for line in path.read_text("utf-8").splitlines():
            parts = line.split("#", 1)[0].split()
            if len(parts) >= 2:
                lock[parts[0]] = parts[1]
    return lock


def author() -> str:
    return "git:lab"


def uses_lines(path: Path):
    for no, line in enumerate(path.read_text("utf-8").splitlines(), 1):
        m = USES.match(line)
        if m:
            yield no, m.group(1).strip("'\"")


def main(argv: list[str]) -> int:
    root = Path.cwd()
    files = [Path(a) for a in argv] or sorted((root / ".gitea" / "workflows").glob("*.yml"))
    lock = load_lock(root / "ci" / "pins.lock")
    who = author()
    bad = 0
    for wf in files:
        for lineno, ref in uses_lines(wf):
            if ref.startswith("./") or ref.startswith("docker://"):
                continue
            name, _, rev = ref.partition("@")
            target = f"workflow:{wf.name}#{lineno}"
            if not PIN.match(rev):
                emit("ci.denied.unpinned_step", "7-5", who, target, "커밋 해시로 고정하지 않은 외부 단계")
                print(f"  {wf.name}:{lineno} {name}@{rev or '(없음)'} → 40자 커밋 해시로 바꾸세요(옆 주석에 원래 태그를 적습니다)")
                bad += 1
            elif lock.get(name) != rev:
                emit("ci.step.changed", "7-5", who, target, "고정 해시가 pins.lock과 다름")
                print(f"  {wf.name}:{lineno} {name}@{rev[:12]}… 이(가) ci/pins.lock과 다릅니다")
                bad += 1
    if bad:
        print(f"고정 검사 실패: {bad}건")
        return 1
    print("고정 검사 통과: 모든 외부 단계가 커밋 해시로 고정돼 있고 pins.lock과 같습니다")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
