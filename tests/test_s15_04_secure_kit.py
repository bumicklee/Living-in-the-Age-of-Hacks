"""15-4 최종 소스 받기와 내 서비스에 secure-kit 적용하기 · 교육용-소스-설계 7절 · TODO 0-9.

예제 앱은 도커로 빌드·실행한다(daon/kit.py, 127.0.0.1:18181~18184). 소스가 바뀌면 시험이 다시 빌드한다(처음에는 수 분).
시작 지점(15-3 완료): 가게 예제(spring-shop·express-shop)에 부품이 없다 → 오류 응답에 내부 문구, 로그인 제한 없음, 감사 기록 없음 → 레드.
                    점검 보고서 견본(packages/examples/점검-보고서-견본.md)도 없다.
완료 지점: 부품 셋(error-response · rate-limit.login · @Audited/kit.audited)을 붙인 두 가게 앱이 같은 계약 시험을 통과, 견본이 양식의 필수 칸을 채움.
정상 시험(시작 지점에서도 그린): 부품 예제 두 앱(spring-kit-demo·express-kit-demo)이 부품 24개 계약을 모두 지킨다 · 부품 목록 정적 확인.
"""
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "packages" / "contract-tests"))

from daon import kit  # noqa: E402
from kitcontract.client import APPS, Kit  # noqa: E402
from kitcontract.parts import PARTS  # noqa: E402
from kitcontract.shop import CHECKS  # noqa: E402

SAMPLE = ROOT / "packages" / "examples" / "점검-보고서-견본.md"
TEMPLATE = ROOT / "docs" / "templates" / "최초-점검-보고서.md"


@pytest.fixture(scope="module")
def shops():
    kit.ensure(["spring-shop", "express-shop"], echo=lambda *_: None)
    return {n: Kit(APPS[n], n) for n in ("spring-shop", "express-shop")}


@pytest.fixture(scope="module")
def demos():
    kit.ensure(["spring-demo", "express-demo"], echo=lambda *_: None)
    return {n: Kit(APPS[n], n) for n in ("spring-demo", "express-demo")}


@pytest.mark.parametrize("app", ["spring-shop", "express-shop"])
@pytest.mark.parametrize("key", list(CHECKS))
def test_shop_contract(shops, app, key):
    CHECKS[key](shops[app])


def _headings(text: str) -> list[str]:
    return [line.lstrip("#").strip() for line in text.splitlines() if line.startswith("## ")]


def test_report_sample_fills_the_template():
    assert SAMPLE.exists(), f"기대: 예제 앱을 「내 서비스」 대신 점검한 견본 {SAMPLE.relative_to(ROOT)}"
    text = SAMPLE.read_text("utf-8")
    need = _headings(TEMPLATE.read_text("utf-8"))
    missing = [h for h in need if h not in _headings(text)]
    assert not missing, f"기대: 양식(docs/templates/최초-점검-보고서.md)의 절이 모두 있음. 빠진 절 {missing}"
    rows = [line for line in text.splitlines() if line.startswith("| S")]
    assert len(rows) >= 3, f"기대: 항목표에 견본 세 줄 이상(S01~). 실제 {len(rows)}줄"
    assert all("TODO" not in r and "____" not in r for r in rows), "기대: 견본 줄에 빈칸 표시가 남지 않음"


@pytest.mark.normal
@pytest.mark.parametrize("app", ["spring-demo", "express-demo"])
@pytest.mark.parametrize("key", list(PARTS))
def test_kit_part_contract(demos, app, key):
    k = demos[app]
    k.reset()
    try:
        PARTS[key](k)
    finally:
        k.reset()


@pytest.mark.normal
def test_parts_manifest_static():
    import subprocess
    r = subprocess.run([sys.executable, "-m", "pytest", "-q", "-p", "no:cacheprovider", "test_manifest.py"],
                       cwd=ROOT / "packages" / "contract-tests", capture_output=True, text=True)
    assert r.returncode == 0, "기대: 부품 24개 목록·두 스택 구현 이름·MIT 라이선스(packages/contract-tests/test_manifest.py)\n" + r.stdout[-600:]
