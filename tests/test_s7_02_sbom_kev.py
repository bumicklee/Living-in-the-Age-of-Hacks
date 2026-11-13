"""7-2 SBOM과 악용 중 취약점(KEV) 대조 · T18(의존성 부분).

시작 지점: SBOM을 만드는 설정이 없고, 코드 생성 도구(mybatis-generator-core)가 운영 jar에 들어 있고, 결정 기록이 없다 → 레드.
완료 지점: SBOM(CycloneDX)이 모듈별로 만들어지고, KEV 대조(make kev)의 「결정 없음」이 0건이고, 운영 jar에서 코드 생성 도구가 빠졌다.
KEV 목록은 날짜를 박은 사본이다(기본: 교육용 가짜 항목 1건이 든 파일, KEV=real: 실제 CISA 사본 → 일치 0건). 인터넷 없이 같은 결과가 나온다.
"""
import hashlib
import json
import subprocess
import tempfile
import zipfile
from datetime import date
from pathlib import Path

import pytest

from daon import supply_extra as se
from daon.config import ROOT

POMS = ["spring/pom.xml", "spring/mall-mbg/pom.xml"]


def poms_hash() -> str:
    h = hashlib.sha256()
    for p in POMS:
        h.update((ROOT / p).read_bytes())
    return h.hexdigest()[:16]


def ensure_sbom() -> None:
    """설정이 있으면 SBOM을 (pom이 바뀌었을 때만) 다시 만든다. 설정이 없으면 시험이 실패해야 한다."""
    assert se.pom_has_plugin(), "기대: spring/pom.xml에 SBOM을 만드는 설정(cyclonedx-maven-plugin)이 있어야 함(make sbom)"
    stamp = se.SBOM_DIR / ".poms"
    ok = all((se.SBOM_DIR / f"{n}.cdx.json").exists() for n in se.SBOM_NAMES) and stamp.exists() and stamp.read_text().strip() == poms_hash()
    if not ok:
        class A:  # noqa
            pass
        se.cmd_sbom(A())
        stamp.write_text(poms_hash())


def test_pom_has_sbom_plugin():
    assert se.pom_has_plugin(), "기대: spring/pom.xml에 CycloneDX 플러그인이 있음"


def test_sbom_files_exist_for_three_apps():
    ensure_sbom()
    for n in se.SBOM_NAMES:
        comps = se.sbom_components(n)
        assert comps, f"기대: build/sbom/{n}.cdx.json에 구성 요소가 있음"
    names = {c["name"] for c in se.sbom_components("mall-portal")}
    assert "spring-webmvc" in names and "dom4j" in names, "기대: mall-portal SBOM에 spring-webmvc·dom4j가 있음(운영 jar의 부품)"


def runtime_jar_libs(image: str) -> list[str]:
    with tempfile.TemporaryDirectory() as d:
        cid = subprocess.run(["docker", "create", image], capture_output=True, text=True)
        assert cid.returncode == 0, f"{image} 이미지가 없습니다(make up으로 빌드)"
        try:
            subprocess.run(["docker", "cp", f"{cid.stdout.strip()}:/app/app.jar", f"{d}/app.jar"], check=True, capture_output=True)
        finally:
            subprocess.run(["docker", "rm", "-f", cid.stdout.strip()], capture_output=True)
        with zipfile.ZipFile(f"{d}/app.jar") as z:
            return [n for n in z.namelist() if n.startswith("BOOT-INF/lib/")]


@pytest.mark.parametrize("image", ["daon/mall-admin:dev", "daon/mall-portal:dev"])
def test_code_generator_is_not_in_runtime_jar(image):
    libs = runtime_jar_libs(image)
    hit = [n for n in libs if "mybatis-generator-core" in n]
    assert not hit, f"기대: 운영 jar에 코드 생성 도구(mybatis-generator-core)가 없음(provided 범위). 지금 들어 있음: {hit}"


def test_kev_report_has_no_undecided_component():
    ensure_sbom()
    rep = se.kev_report("fixture", emit=False)
    assert rep["total_required"] >= 1, "기대: 결정이 필요한 부품이 보임(KEV 일치·높음 이상 런타임 권고)"
    assert rep["undecided"] == 0, f"기대: 「결정 없음」 0건. 실제 {rep['undecided']}건(security/vuln-decisions.yaml에 올림 또는 예외를 적는다)"


def test_kev_match_is_found_and_logged(tmp_path, monkeypatch):
    ensure_sbom()
    monkeypatch.setenv("DAON_REPORTS_DIR", str(tmp_path))
    rep = se.kev_report("fixture", decisions={})
    hit = [r for r in rep["kev"] if r["purl"] == "pkg:maven/dom4j/dom4j@1.6.1"]
    assert hit and hit[0]["fixture"] and hit[0]["ids"] == ["LAB-KEV-0001"], f"기대: dom4j 1.6.1이 교육용 가짜 KEV 항목과 일치. 실제 {rep['kev']}"
    ev = [e for e in se.secevent.read("supply.kev_match")]
    assert ev and ev[0]["target"] == "pkg:maven/dom4j/dom4j@1.6.1" and ev[0]["actor"] == "tool:make-kev" and ev[0]["lesson"] == "7-2", f"기대: supply.kev_match(target 패키지 주소). 실제 {ev}"
    assert ev[0]["reason"] == "KEV 목록에 있는 취약점(결정 없음)", f"기대: 결정이 없으면 WARN 이유 「…(결정 없음)」. 실제 {ev[0]['reason']}"


def test_expired_exception_warns_again(tmp_path, monkeypatch):
    ensure_sbom()
    monkeypatch.setenv("DAON_REPORTS_DIR", str(tmp_path))
    d = {"pkg:maven/dom4j/dom4j@1.6.1": {"component": "pkg:maven/dom4j/dom4j@1.6.1", "decision": "exception", "reason": "확인 중", "until": "2026-01-01", "owner": "가짜 담당"}}
    rep = se.kev_report("fixture", decisions=d)
    assert rep["kev"][0]["state"] == "overdue", "기대: 기한이 지난 예외는 다시 경고 상태"
    ev = se.secevent.read("supply.kev_match")
    assert ev and ev[0]["reason"] == "KEV 목록에 있는 취약점(예외 기한 지남)", f"기대: 이유 「…(예외 기한 지남)」. 실제 {ev}"
    d["pkg:maven/dom4j/dom4j@1.6.1"]["until"] = "2099-01-01"
    d["pkg:maven/dom4j/dom4j@1.6.1"].pop("owner")
    assert se.kev_report("fixture", emit=False, decisions=d)["kev"][0]["state"] == "undecided", "기대: 사유·기한·담당 중 하나라도 없으면 결정이 아님"


def test_real_cisa_copy_has_no_match_in_these_components():
    ensure_sbom()
    rep = se.kev_report("real", emit=False, decisions={})
    assert rep["kev"] == [], f"기대: 2026-11-06 실제 KEV 사본과 일치하는 부품 0건(교재: 0건 화면). 실제 {rep['kev']}"
    assert rep["runtime"], "기대: 그래도 높음 이상 런타임 권고는 남아 있어 순서를 정해야 함"


@pytest.mark.normal
def test_decision_file_format_is_checked():
    assert se.decision_state(None, date(2026, 11, 9)) == "undecided"
    assert se.decision_state({"decision": "removed"}, date(2026, 11, 9)) == "decided"
    assert se.decision_state({"decision": "exception", "reason": "r", "until": "2026-11-08", "owner": "o"}, date(2026, 11, 9)) == "overdue"


@pytest.mark.normal
def test_normal_login_and_admin_product_list_still_work(stack):
    from replay._http import admin, admin_login, portal, portal_login
    with portal() as c:
        assert portal_login(c, "alice").startswith("Bearer "), "기대: 회원 로그인 정상"
    with admin() as c:
        tok = admin_login(c, "admin")
        r = c.get("/product/list", headers={"Authorization": tok})
        assert r.json().get("code") == 200, f"기대: 관리자 상품 목록 정상. 실제 {r.text[:100]}"
