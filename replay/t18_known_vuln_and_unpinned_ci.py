"""T18 · 알려진 취약 버전 의존성(결정 없음) + 고정되지 않은 CI 도구. 수업 7-2(SBOM·KEV 대조)·7-5(CI 도구 고정).

두 부분이다. 지금 지점에 해당하는 부분만 잰다.
  ① 7-2: SBOM이 없거나, KEV 일치·높음 이상 런타임 권고에 결정이 없으면 레드. (SBOM·KEV는 날짜를 박은 사본, 인터넷 없이)
  ② 7-5: A8이 켜져 있으면 CI 파일의 외부 단계(uses:)가 커밋 해시로 고정돼 있는지 본다. dynamic=True이면 실제로 재연한다:
         로컬 Gitea 안에서만 외부 단계의 v1 태그를 다른 커밋으로 옮기고 → 파이프라인을 돌려 → 「바뀐 단계」가 실행됐는지(환경 변수 이름이 로그에 보이는지) 본다. 끝나면 태그를 되돌린다.
         옮긴 단계는 환경 변수의 **이름**만 출력한다. 값은 출력하지 않는다. 127.0.0.1의 로컬 Gitea만 부른다.
"""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

from daon import ci, supply_extra as se
from daon.config import ROOT
from replay import NotReady, Result

T = "T18"
TITLE = "알려진 취약 버전 부품에 결정이 없음 · 고정되지 않은 CI 도구"
LESSON = "7-2·7-5"
SOURCE = "점검 후 결정 + [교육용 추가]"
STACKS = ("spring", "express")
MARK = "이 단계는 바뀌었습니다"
USES = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)")


def unpinned_steps() -> list[str] | None:
    """워크플로의 외부 단계 중 40자 해시로 고정되지 않은 것. 워크플로가 없으면 None."""
    wfs = sorted((ROOT / ".gitea/workflows").glob("*.yml"))
    if not wfs:
        return None
    out = []
    for wf in wfs:
        for no, line in enumerate(wf.read_text("utf-8").splitlines(), 1):
            m = USES.match(line)
            if m and not m.group(1).startswith(("./", "docker://")):
                ref = m.group(1).strip("'\"")
                if not re.fullmatch(r"[0-9a-f]{40}", ref.partition("@")[2]):
                    out.append(f"{wf.name}:{no} {ref}")
    return out


def sbom_part() -> tuple[bool, list[str]]:
    ev = []
    if not se.pom_has_plugin():
        return True, ["SBOM을 만드는 설정(CycloneDX 플러그인)이 spring/pom.xml에 없다 → SBOM 없음"]
    comps = {n: len(se.sbom_components(n)) for n in se.MAVEN_MODULES}
    if not all(comps.values()):
        return True, ["SBOM 파일이 없다(make sbom)"]
    rep = se.kev_report("fixture", emit=False)
    ev.append(f"SBOM 구성 요소: mall-admin {comps['mall-admin']}개 · mall-portal {comps['mall-portal']}개")
    ev.append(f"KEV 일치 {len(rep['kev'])}건 · 높음 이상 런타임 권고 {len(rep['runtime'])}건 → 결정 없음 {rep['undecided']}건")
    return rep["undecided"] > 0, ev


def dynamic() -> tuple[bool, list[str]]:
    """태그를 옮기고 파이프라인을 돌려 바뀐 단계가 실행됐는지. 반환: (실행됐나, 증거 줄)"""
    if not (ci.IN_DIR / "mall-portal.tar").exists():
        ci.build_image("ci-lesson")
    ev = []
    ci.require()
    before = ci.tag_target()
    try:
        new = ci.tag_move()
        ev.append(f"로컬 Gitea 안에서만: scan-step v1 태그 {before[:12]} → {new[:12]} (우리 CI 파일은 그대로)")
        run = ci.run_pipeline("ci: 태그 이동 뒤 실행")
    finally:
        ci.tag_restore()
    ev.append(f"파이프라인 결과: {run.status} · 작업 " + ", ".join(f"{k}={v}" for k, v in run.jobs.items()))
    ran = run.has(MARK)
    if ran:
        names = []
        log = run.log
        i = log.index("환경 변수의 이름")
        for line in log[i:].splitlines()[1:12]:
            body = line.split("Z ", 1)[-1].strip()
            if re.fullmatch(r"[A-Z][A-Z0-9_]{3,}", body):
                names.append(body)
        ev.append(f"바뀐 단계가 실행됐다. 로그에 보이는 환경 변수 이름: {', '.join(names) or '(없음)'}")
    else:
        why = "고정 검사에서 막혔다" if "lint" in run.jobs and run.jobs.get("lint") != "success" else "고정된 해시의 단계만 실행됐다"
        ev.append(f"바뀐 단계가 실행되지 않았다({why})")
    return ran, ev


def run(stack: str = "spring", dynamic_run: bool = True) -> Result:
    ev: list[str] = []
    bad = False
    s_bad, s_ev = sbom_part()
    ev += ["[7-2] " + e for e in s_ev]
    bad |= s_bad
    un = unpinned_steps()
    if un is None:
        ev.append("[7-5] CI 파일(.gitea/workflows)이 아직 없다(A8은 7-5에서)")
    else:
        ev.append(f"[7-5] 고정되지 않은 외부 단계 {len(un)}건" + (": " + "; ".join(un) if un else ""))
        bad |= bool(un)
        if dynamic_run and ci.available():
            try:
                ran, d_ev = dynamic()
            except ci.CiNotReady as e:
                raise NotReady(str(e))
            ev += ["[7-5] " + e for e in d_ev]
            bad |= ran
        elif dynamic_run:
            raise NotReady("로컬 CI(A8)가 꺼져 있습니다. make up STACK=spring")
    if bad:
        return Result(True, "결정 없는 취약 부품이 있거나, 고정되지 않은 CI 단계가 바뀐 채 실행된다", ev)
    if un is None:
        return Result(False, "부품마다 결정이 있다(CI 단계 고정은 7-5에서 확인한다)", ev)
    return Result(False, "부품마다 결정이 있고, CI 단계는 커밋 해시로 고정돼 바뀐 단계가 실행되지 않는다", ev)
