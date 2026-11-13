"""정적 확인(앱을 띄우지 않는다): 부품 목록 24개마다 계약 함수가 있고, 두 스택 소스에 그 이름이 있다."""
from pathlib import Path

import yaml

from kitcontract.parts import PARTS

PKG = Path(__file__).resolve().parent.parent
ROWS = yaml.safe_load((PKG / "parts.yaml").read_text("utf-8"))


def _text(root: Path, *globs: str) -> str:
    return "\n".join(f.read_text("utf-8", "replace") for g in globs for f in root.rglob(g)
                     if "node_modules" not in f.parts and "target" not in f.parts)


def test_24_parts_each_have_a_contract():
    assert len(ROWS) == 24 and [r["num"] for r in ROWS] == list(range(1, 25))
    assert [r["key"] for r in ROWS] == list(PARTS), "parts.yaml 순서와 계약 함수 순서가 같아야 한다"


def test_spring_and_express_name_every_part():
    java = _text(PKG / "secure-kit-spring", "*.java")
    js = _text(PKG / "secure-kit-express", "*.js")
    missing = [f"{r['key']}: spring {r['spring']}" for r in ROWS if r["spring"] not in java]
    missing += [f"{r['key']}: express {r['express']}" for r in ROWS if r["express"] not in js]
    assert not missing, "구현이 없는 부품: " + ", ".join(missing)


def test_license_is_mit():
    for d in ("secure-kit-spring", "secure-kit-express", "contract-tests"):
        lic = PKG / d / "LICENSE"
        assert lic.exists() and "MIT License" in lic.read_text("utf-8"), f"{d}/LICENSE(MIT)"
