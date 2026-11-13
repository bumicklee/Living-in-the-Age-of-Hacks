"""3-2 MyBatis `${}`와 문자열 조립 찾기·고치기 · T06(검색어 경우)."""
import json
import re
import subprocess
import sys

import pytest

from daon.config import ROOT
from replay import _logs
from replay import t06_query_build as t06
from replay._http import new_trace_id

SCANNER = ROOT / "tools" / "scan_mybatis_dollar.py"


def test_quote_is_searched_as_plain_text(stack):
    r = t06.search(t06.KEYWORD_QUOTE)
    b = t06.body(r)
    assert r.status_code == 200 and b.get("code") == 200 and b.get("data") == [], \
        f"기대: 작은따옴표는 그냥 글자로 검색되어 결과 0개(오류 없음). 실제: HTTP {r.status_code} code={b.get('code')} message={str(b.get('message'))[:80]!r}"


def test_always_true_keyword_shows_no_hidden_products(stack):
    got = t06.ids(t06.search(t06.KEYWORD_ALWAYS)) or []
    assert not (t06.HIDDEN & set(got)), f"기대: 검색어 칸 값으로 조건을 바꿀 수 없어야 합니다(비공개 상품 {sorted(t06.HIDDEN)} 안 나옴). 실제 결과 {len(got)}개"


@pytest.mark.normal
def test_normal_search_earphone(stack):
    got = t06.ids(t06.search(t06.NORMAL_KEYWORD))
    assert got == [201], f"기대: 「이어폰」 검색은 고치기 전과 같이 공개 상품 201 하나. 실제: {got}"


def test_scan_script_reports_no_keyword_input(stack):
    assert SCANNER.exists(), "기대: 소스 전체에서 ${ 를 찾는 검색 스크립트 tools/scan_mybatis_dollar.py가 있어야 합니다"
    r = subprocess.run([sys.executable, str(SCANNER), "spring"], cwd=ROOT, capture_output=True, text=True)
    lines = r.stdout.splitlines()
    inputs = [l for l in lines if l.startswith("입력")]
    assert not [l for l in inputs if "keyword" in l], "기대: 검색어 쪽 ${keyword}가 사라져야 합니다\n" + "\n".join(inputs)
    counts = [int(m.group(1)) for l in lines if l.startswith("상수") for m in [re.search(r"매퍼 (\d+)개", l)] if m]
    assert counts and counts[0] >= 76, \
        "기대: 원본 생성 매퍼 76개의 criterion.condition·orderByClause는 「상수」로 표시만 합니다(3-3 뒤에는 검색 DAO도 상수)\n" + r.stdout[-400:]


def test_sql_grammar_error_is_logged(stack):
    """정렬 쪽(3-3 전)은 아직 조립하므로 문법 오류를 낼 수 있다. 3-3을 마친 뒤에는 그 값이 SQL까지 가지 않고 400으로 끝난다."""
    since = _logs.now()
    tid = new_trace_id("s3-02")
    r = t06.search("다온", t06.SORT_BROKEN, trace_id=tid)
    if r.status_code == 400:                       # 3-3 허용 목록이 이미 막음: 문법 오류 대신 거절 이벤트
        e = _logs.wait_event("mall-portal", since, "input.rejected.sort_field", "3-3", stack, trace_id=tid)
        assert e, "기대: 3-3 이후에는 정렬 값이 SQL까지 가지 않고 input.rejected.sort_field로 남습니다"
        return
    e = _logs.wait_event("mall-portal", since, "db.error.bad_sql_grammar", "3-2", stack, trace_id=tid)
    assert e, f"기대: SQL 문법 오류는 mall-portal 로그에 SECURITY db.error.bad_sql_grammar(3-2)로 남아야 합니다(trace_id={tid})"
    assert e.get("target") == "path:/daon/product/search", f"기대: target은 요청 경로만. 실제: {e.get('target')}"
    assert "price" not in json.dumps(e, ensure_ascii=False), f"기대: 이벤트에 SQL 문장·입력 값이 없어야 합니다. 실제: {e}"


@pytest.mark.normal
def test_normal_search_leaves_no_sql_event(stack):
    since = _logs.now()
    tid = new_trace_id("s3-02n")
    t06.search(t06.NORMAL_KEYWORD, "price_asc", trace_id=tid)
    e = _logs.wait_event("mall-portal", since, "db.error.bad_sql_grammar", "3-2", stack, timeout=2.0, trace_id=tid)
    assert e is None, f"기대: 정상 검색은 보안 이벤트를 남기지 않습니다. 실제: {e}"
