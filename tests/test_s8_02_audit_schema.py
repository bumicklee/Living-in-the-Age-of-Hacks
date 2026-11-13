"""8-2 감사 로그 설계 · 설계 수업이라 코드 대신 설계 파일(docs/audit-schema.yaml)을 검사한다.

시작 지점: 파일이 없다 → 레드. 완료 지점: 필수 필드 8개, 관리자 조회 4개의 action 이름, 넣지 않는 것 목록, Express 대응표.
"""
import yaml
import pytest

from daon.config import ROOT

SCHEMA = ROOT / "docs" / "audit-schema.yaml"
REQUIRED = ["ts", "trace_id", "actor", "action", "target", "count", "reason", "result"]
PATHS = ["/daon/customer/list", "/order/list", "/order/{id}", "/returnApply/list"]


@pytest.fixture
def schema():
    assert SCHEMA.exists(), "기대: docs/audit-schema.yaml(감사 로그 설계 파일)이 있음"
    return yaml.safe_load(SCHEMA.read_text("utf-8"))


def test_required_fields(schema):
    names = [f["name"] if isinstance(f, dict) else f for f in schema.get("required_fields", [])]
    missing = [n for n in REQUIRED if n not in names]
    assert not missing, f"기대: 필수 필드 8개(누가·무엇을·몇 건·왜·결과 …). 빠진 것: {missing}"


def test_actions_for_admin_reads(schema):
    acts = {a.get("path", ""): a.get("action") for a in schema.get("actions", [])}
    missing = [p for p in PATHS if not any(p in k and v for k, v in acts.items())]
    assert not missing, f"기대: 관리자 조회마다 action 이름이 정해져 있음. 빠진 곳: {missing}"
    names = [v for v in acts.values() if v]
    assert len(set(names)) == len(names), "기대: action 이름이 겹치지 않음"


def test_never_include_list(schema):
    never = " ".join(map(str, schema.get("never_include", [])))
    for word in ("이름", "전화", "주소", "토큰", "비밀번호", "요청 본문"):
        assert word in never, f"기대: 「넣지 않음」 목록에 {word}"


def test_express_mapping_and_threshold(schema):
    ex = schema.get("express") or []
    assert any(isinstance(e, dict) and e.get("query") and e.get("action") for e in ex), "기대: EverShop 관리자 GraphQL 질의 → 같은 action 이름 대응표"
    assert schema.get("bulk_threshold") == 30, "기대: 대량 조회 기준 30건(랩 값, 결정 #16)"
