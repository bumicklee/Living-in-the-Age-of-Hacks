"""8-미션 감사·가리기·체인을 다른 관리자 기능에도(수업 노트 lessons/s8-mission.md).

시작 지점: 쿠폰 사용 내역·반품 상세 조회에는 감사 로그가 없고, 런북 세 개가 없다 → 레드.
완료 지점: 두 기능의 AUDIT 줄(체인 포함), 런북 세 개(먼저 볼 질의·첫 조치·누구에게 알리나), 체인 확인 통과, 로그 가리기 0건.
"""
import time
import uuid

import pytest

from daon import observe
from daon.config import ROOT
from replay import _logs, _obs
from replay import t21_log_leak as t21
from replay._http import admin, admin_login

RUNBOOKS = ["access.denied.not_owner", "data.read.bulk", "audit.chain.broken"]


def call(path: str, params=None) -> tuple[str, dict]:
    from replay.t09_login_guard import clear_counters
    t = f"lab-s8m-{uuid.uuid4().hex[:10]}"
    with admin() as c:
        clear_counters()
        tok = admin_login(c, "admin")
        r = c.get(path, params=params or {}, headers={"Authorization": tok, "X-Trace-Id": t})
    return r.headers.get("X-Trace-Id", t), r.json()


def test_coupon_history_list_is_audited():
    t, body = call("/couponHistory/list", {"pageNum": 1, "pageSize": 5})
    line = _obs.wait_audit(t, timeout=6)
    assert line and line.get("action") == "coupon.history.list", f"기대: 쿠폰 사용 내역 조회에 AUDIT(action coupon.history.list). 실제 {line}"
    assert line.get("count") == len((body.get("data") or {}).get("list") or []), "기대: count는 돌려준 건수"
    assert "hash" in line, "기대: 같은 체인(seq·prev·hash)"


def test_return_detail_is_audited():
    _, lst = call("/returnApply/list", {"pageNum": 1, "pageSize": 1})
    rows = (lst.get("data") or {}).get("list") or []
    rid = rows[0]["id"] if rows else 1
    t, _ = call(f"/returnApply/{rid}")
    line = _obs.wait_audit(t, timeout=6)
    assert line and line.get("action") == "return.detail" and line.get("target") == f"return:{rid}", f"기대: 반품 상세 조회에 AUDIT(action return.detail, target return:{rid}). 실제 {line}"


def test_three_runbooks_follow_the_template():
    for ev in RUNBOOKS:
        p = ROOT / "docs" / "runbooks" / f"{ev}.md"
        assert p.exists(), f"기대: 런북 docs/runbooks/{ev}.md"
        text = p.read_text("utf-8")
        for head in ("먼저 볼 질의", "첫 조치", "누구에게 알리나"):
            assert head in text, f"기대: {ev} 런북에 「{head}」"
        assert "____" not in text and "<event 이름>" not in text, f"기대: {ev} 런북 양식 칸을 채움"


@pytest.mark.normal
def test_chain_and_masking_hold():
    call("/couponHistory/list")
    rep = observe.verify("spring", emit=False)
    assert rep.status == "ok", f"기대: 감사 로그 체인 통과. 실제 {rep.summary}"
    since = _logs.now()
    t21.flows()
    time.sleep(1.5)
    assert sum(t21.count(t21.app_lines(since)).values()) == 0, "기대: 로그 가리기 0건(8-5)"
