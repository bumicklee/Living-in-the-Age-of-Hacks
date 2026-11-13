"""8-6 감사 로그 줄 지우기 · 방어 확인(새 T 번호는 주지 않는다, 가칭 T20x: make replay T=20x).

「앱 컨테이너에 들어갈 수 있는 사람」을 랩 도구로 흉내 내 mall-admin의 감사 로그 파일에서 대량 조회 한 줄을 지운다.
이 랩 안의 파일 한 줄을 지우는 수준이고, 우회 기법은 없다. 끝나면 파일을 원래대로 되돌린다(DAON_KEEP_TAMPER=1이면 그대로 둔다).
  레드: 줄을 지워도 make audit-verify가 알아채지 못한다(8-6 전: 줄에 체인 정보가 없다)
  그린: make audit-verify가 「seq=N에서 체인이 끊김」을 찾고 audit.chain.broken을 남긴다
"""
from __future__ import annotations

import json
import os

from replay import NotReady, Result, _obs
from replay.t20_bulk_read import bulk_read

T = "T20x"
TITLE = "감사 로그 한 줄을 지워도 알 수 없음(방어 확인)"
LESSON = "8-6"
SOURCE = "[교육용 추가] 감사 로그(8-3)가 앱 옆 파일 하나뿐"
STACKS = ("spring",)


def remove_line(raw: str, trace_id: str) -> tuple[str, int]:
    """trace_id가 든 줄을 뺀 글자와 그 줄 번호(1부터). 없으면 (raw, 0)"""
    lines = raw.splitlines(keepends=True)
    for i, ln in enumerate(lines):
        try:
            if json.loads(ln).get("trace_id") == trace_id:
                return "".join(lines[:i] + lines[i + 1:]), i + 1
        except ValueError:
            continue
    return raw, 0


def run(stack: str = "spring", keep: bool | None = None) -> Result:
    from daon import observe
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s8-06).")
    keep = os.environ.get("DAON_KEEP_TAMPER") == "1" if keep is None else keep
    calls = bulk_read("t20x", calls=3)
    victim = calls[1][0]                                   # 가운데 줄(뒤에 줄이 하나 더 있다)
    if not _obs.wait_audit(calls[-1][0], stack, timeout=6):
        return Result(True, "감사 로그가 없다(8-3 전): 지울 기록조차 없다", ["make goto L=8-3 AT=done 뒤에 다시"])
    original = _obs.audit_raw(stack)
    tampered, lineno = remove_line(original, victim)
    if not lineno:
        raise NotReady("지울 줄을 찾지 못했습니다")
    ev = [f"관리자 회원 목록 × 3 → 감사 로그 {len(original.splitlines())}줄",
          f"앱 컨테이너 안에서 {lineno}번째 줄(trace_id={victim}, 대량 조회)을 지움 → 파일은 멀쩡해 보인다"]
    try:
        _obs.audit_write(tampered, stack)
        rep = observe.verify(stack, emit=True)
    finally:
        if not keep:
            _obs.audit_write(original, stack)
            ev.append("파일을 원래대로 되돌렸습니다(DAON_KEEP_TAMPER=1이면 그대로 둠)")
    ev += ["make audit-verify: " + rep.summary] + [f"  {d}" for d in rep.details[:4]]
    if rep.status == "broken":
        return Result(False, f"지운 자리를 찾았다(seq={rep.seq}) · audit.chain.broken", ev)
    return Result(True, "줄을 지워도 알아채지 못한다", ev)
