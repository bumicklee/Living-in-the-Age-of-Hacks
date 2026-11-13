#!/usr/bin/env python3
"""MyBatis 매퍼 XML에서 `${`(문자열 치환) 줄을 찾아 「상수」와 「입력」으로 가른다. 수업 3-2.

    python tools/scan_mybatis_dollar.py            # 입력 줄과 파일별 상수 개수
    python tools/scan_mybatis_dollar.py --all      # 상수 줄까지 한 줄씩
    python tools/scan_mybatis_dollar.py spring     # 찾을 폴더(기본: spring)

- 생성 매퍼의 `${criterion.condition}`·`${orderByClause}`는 코드 안 상수만 받는다 → 「상수」(표시만).
- 나머지 `${…}`는 요청 값이 닿을 수 있다고 보고 「입력」 → 하나라도 있으면 종료 코드 1(시험·CI에서 실패).
- `<mapper`가 없는 XML(logback 설정 등)의 `${}`는 MyBatis가 아니므로 보지 않는다.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

CONSTANT = re.compile(r"\$\{(criterion\.condition|orderByClause)\}")
DOLLAR = re.compile(r"\$\{[^}]*\}")


def dollar_lines(root: Path):
    for path in sorted(root.rglob("*.xml")):
        if {"target", "node_modules"} & set(path.parts):
            continue
        text = path.read_text("utf-8", errors="replace")
        if "<mapper" not in text:
            continue
        for lineno, line in enumerate(text.splitlines(), 1):
            if "${" in line:
                yield path, lineno, line


def main(argv: list[str]) -> int:
    show_all = "--all" in argv
    args = [a for a in argv if not a.startswith("--")]
    root = Path(args[0] if args else "spring")
    failed = False
    constants: dict[Path, int] = {}
    for path, lineno, line in dollar_lines(root):
        # 한 줄의 ${…}가 모두 상수 경로일 때만 「상수」
        kind = "상수" if all(CONSTANT.fullmatch(m.group(0)) for m in DOLLAR.finditer(line)) else "입력"
        if kind == "상수":
            constants[path] = constants.get(path, 0) + 1
            if not show_all:
                continue
        print(f"{kind}  {path}:{lineno}  {line.strip()}")
        failed |= kind == "입력"
    print(f"상수  매퍼 {len(constants)}개 파일, {sum(constants.values())}줄 (criterion.condition·orderByClause)")
    print("결과: " + ("입력 경로 `${` 있음 → 바인딩(#{})이나 허용 목록으로 고치세요" if failed else "입력 경로 `${` 없음"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
