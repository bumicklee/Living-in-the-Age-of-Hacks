"""3-3 정렬·검색 조건 같은 동적 쿼리를 안전하게 · T06(정렬 경우)."""
import json
import subprocess
import sys

import pytest

from daon.config import ROOT
from replay import _logs
from replay import t06_query_build as t06
from replay._http import new_trace_id

SCANNER = ROOT / "tools" / "scan_mybatis_dollar.py"


def test_hidden_column_sort_rejected(stack):
    r = t06.search("다온", t06.SORT_HIDDEN_DESC)
    b = t06.body(r)
    assert r.status_code == 400 and b.get("code") == 400, \
        f"기대: 화면에 없는 컬럼(재고)으로 정렬하는 값은 HTTP 400·code 400. 실제: HTTP {r.status_code} code={b.get('code')}"


def test_broken_sort_rejected_before_sql(stack):
    r = t06.search("다온", t06.SORT_BROKEN)
    b = t06.body(r)
    assert r.status_code == 400 and b.get("code") == 400 and not t06.sql_error(r), \
        f"기대: 컬럼 이름이 아닌 값은 SQL까지 가지 않고 400. 실제: HTTP {r.status_code} code={b.get('code')} message={str(b.get('message'))[:60]!r}"


@pytest.mark.normal
def test_normal_sorts_keep_their_order(stack):
    rows = {}
    for sort in ("new", "price_asc", "price_desc", "sale"):
        r = t06.search("다온", sort)
        b = t06.body(r)
        assert r.status_code == 200 and b.get("code") == 200, f"기대: 정상 정렬 {sort}는 200. 실제: HTTP {r.status_code} {b.get('message')!r}"
        rows[sort] = b["data"]
    ids = [p["id"] for p in rows["new"]]
    assert ids == sorted(ids, reverse=True) and len(ids) >= 30, f"기대: 신상품순은 번호 큰 순. 실제: {ids[:8]}"
    up = [float(p["price"]) for p in rows["price_asc"]]
    down = [float(p["price"]) for p in rows["price_desc"]]
    assert up == sorted(up) and down == sorted(down, reverse=True), "기대: 가격 오름·내림 순서"
    sale = [p["sale"] for p in rows["sale"]]
    assert sale == sorted(sale, reverse=True), "기대: 판매량 많은 순"


def test_rejected_sort_is_logged(stack):
    since = _logs.now()
    tid = new_trace_id("s3-03")
    t06.search("다온", t06.SORT_HIDDEN_DESC, trace_id=tid)
    e = _logs.wait_event("mall-portal", since, "input.rejected.sort_field", "3-3", stack, trace_id=tid)
    assert e, f"기대: 허용 밖 정렬 값은 mall-portal 로그에 SECURITY input.rejected.sort_field(3-3)로 남아야 합니다(trace_id={tid})"
    assert e.get("actor") == "anonymous" and "stock" not in json.dumps(e, ensure_ascii=False), \
        f"기대: 로그인 없는 요청(anonymous), 받은 정렬 값은 로그에 넣지 않음. 실제: {e}"


def test_scan_script_finds_no_input_dollar(stack):
    assert SCANNER.exists(), "기대: tools/scan_mybatis_dollar.py(3-2)"
    r = subprocess.run([sys.executable, str(SCANNER), "spring"], cwd=ROOT, capture_output=True, text=True)
    assert r.returncode == 0, "기대: 정렬까지 고치면 입력 경로 ${ 가 하나도 없습니다(종료 코드 0)\n" + r.stdout[-400:]


def test_t06_replay_green(stack):
    r = t06.run(stack)
    assert not r.vulnerable, "기대: 검색어·정렬 두 경우 모두 막힘\n" + r.show()
