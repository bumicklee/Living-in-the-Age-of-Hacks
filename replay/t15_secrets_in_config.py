"""T15 · 설정 파일·문서·이력에 시크릿이 그대로 있다(점검만, 읽기 전용). 수업 6-2(찾기·고치기)·6-3(옮기기).

gitleaks로 이 랩 저장소의 작업 트리(spring/)를 점검해 **건수만** 보고한다. 값은 어디에도 찍지 않는다.
레드: 작업 트리에 설정 파일 평문 자격증명이나 토큰 모양 값이 남아 있음. 이력(git log)에 남은 건수는 참고로만 센다(다시 쓰지 않으므로 줄지 않는다. 교체가 답이다).
6-3부터는 「교체 대상 목록」(infra/secrets/rotation-list.yaml)에 교체 전인 항목이 남아 있어도 레드다.
"""
from __future__ import annotations

from daon import secretscan as ss
from daon.config import ROOT
from replay import NotReady, Result

T = "T15"
TITLE = "설정 파일·문서에 시크릿이 그대로 있음(점검만)"
LESSON = "6-2·6-3"
SOURCE = "[원본 기본값]+[회사 설정]"
STACKS = ("spring", "express")
ROTATION = ROOT / "infra" / "secrets" / "rotation-list.yaml"


def pending_rotation() -> int | None:
    """교체 대상 목록에서 아직 교체하지 않은 항목 수. 목록이 없으면 None(6-2 시작 전)."""
    if not ROTATION.exists():
        return None
    return sum(1 for l in ROTATION.read_text("utf-8").splitlines() if l.strip().startswith("status:") and "rotated" not in l)


def run(stack: str = "spring") -> Result:
    try:
        tree = ss.scan_tree("reference" if not ss.CONFIG_LAB.exists() else "auto")
        hist = ss.scan_history("reference" if not ss.CONFIG_LAB.exists() else "auto")
    except ss.ScanUnavailable as e:
        raise NotReady(str(e))
    ev = [f"작업 트리 점검(spring/): {len(tree)}건 (값은 가림)", *ss.summarize(tree),
          f"git 이력 참고: {len(hist)}건(이력은 다시 쓰지 않는다 → 교체가 답)"]
    pend = pending_rotation()
    if pend is not None:
        ev.append(f"교체 대상 목록: 교체 전 {pend}건")
    bad = len(tree) > 0 or bool(pend)
    if bad:
        return Result(True, f"설정·문서에 시크릿 {len(tree)}건이 그대로 있거나 교체가 끝나지 않았다", ev)
    return Result(False, "작업 트리에 시크릿이 남아 있지 않고 교체 대상이 모두 교체됐다", ev)
