"""재연 스크립트(T 번호별). 랩 전용: 127.0.0.1의 다온마켓만 부르고, 값은 전부 가짜다.

재연 하나 = 모듈 하나(`replay/tNN_*.py`). 모듈은 다음을 가진다.
  T       : "T01"
  TITLE   : 한 줄 제목
  LESSON  : 막는 수업 번호(예: "2-2")
  SOURCE  : BASELINE 출처 표시([원본 기본값]/[회사 설정]/[교육용 추가]/[원본 확인됨])
  STACKS  : 재연을 만든 스택(예: ("spring",))
  run(stack) -> Result   취약하면 Result.vulnerable=True(레드)

시험(tests/)도 같은 run()을 부른다. 재연과 시험이 한 벌이라 Spring·Express를 같은 기준으로 잰다.
"""
from __future__ import annotations

import importlib
import pkgutil
from dataclasses import dataclass, field


@dataclass
class Result:
    vulnerable: bool
    summary: str
    evidence: list[str] = field(default_factory=list)

    def show(self) -> str:
        head = "[레드] 취약: " if self.vulnerable else "[그린] 막힘: "
        return "\n".join([head + self.summary, *("  " + e for e in self.evidence)])


class NotReady(Exception):
    """랩이 떠 있지 않거나 이 스택의 재연이 아직 없다."""


def all_modules() -> dict[str, object]:
    out = {}
    for m in pkgutil.iter_modules(__path__):
        if m.name.startswith("t") and m.name[1:3].isdigit():
            mod = importlib.import_module(f"replay.{m.name}")
            out[mod.T] = mod
    return dict(sorted(out.items()))


EXTRA = {"T18D": "deserialize_cache_type", "T20X": "audit_tamper", "T46": "f46_error_path",   # 새 T 번호를 주지 않는 방어 확인 재연(점수판에 세지 않는다)
         "T14-3": "r14_03_slow_dependency", "TR14-3": "r14_03_slow_dependency"}                  # 14-3 회귀 재연(make replay R=14-3)


def get(t: str):
    key = "T" + str(t).upper().lstrip("T").zfill(2)
    if key in EXTRA:
        return importlib.import_module(f"replay.{EXTRA[key]}")
    mods = all_modules()
    if key not in mods:
        have = ", ".join(mods) or "없음"
        raise SystemExit(f"{key} 재연이 아직 없습니다(지금 있는 것: {have}).")
    return mods[key]
