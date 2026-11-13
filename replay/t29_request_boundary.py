"""T29 · 게이트웨이와 앱이 요청의 끝을 다르게 볼 수 있는 설정(요청 밀반입 조건, P1). 수업 11-3.

[회사 설정] 재연은 공격 요청을 보내지 않는다. **설정 점검**(make audit-gateway와 같은 표)만 돈다.
  레드: 고칠 것이 하나라도 있다(고객 API가 게이트웨이 뒤에 없음, 모호한 요청 거절 규칙·이벤트 줄 없음, 이미지 태그만 고정 …)
  그린: 모든 항목 확인(A7: 8086 입구, 거절 표, request.denied.malformed 줄, 뒷단 재사용 끔, 다이제스트 고정)
거절이 실제로 일어나는지는 make check L=11-3 의 「방어 확인」 시험 세 개가 본다(본문에 다른 요청이 없는 요청만).
"""
from __future__ import annotations

from daon import boundary
from replay import NotReady, Result

T = "T29"
TITLE = "게이트웨이·앱 요청 경계 설정(모호한 요청 거절·연결 재사용·버전 고정)"
LESSON = "11-3"
SOURCE = "[회사 설정] A1 게이트웨이(nginx)에 고객 API 경로·거절 규칙·이벤트 줄이 없고, 이미지를 태그로만 고정"
STACKS = ("spring",)


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s11-03, 같은 게이트웨이를 쓴다).")
    rows = boundary.gateway_rows()
    ev = [f"{'고칠 것' if ok is False else ('확인' if ok else '해당 없음'):<6} {name}: {val}" for name, val, ok in rows]
    bad = boundary.gateway_failed(rows)
    if bad:
        return Result(True, f"설정 점검 고칠 것 {len(bad)}개(공격 요청은 보내지 않음)", ev)
    return Result(False, "설정 점검 항목 모두 확인", ev)
