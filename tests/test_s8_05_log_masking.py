"""8-5 로그 속 토큰·전화번호 가리기 · 재연 T21.

시작 지점: 로그인·조회 흐름 뒤 앱 로그와 Loki에 평문 비밀번호 값·JWT 모양·전화번호 모양이 있다 → 레드.
완료 지점: 같은 흐름 뒤 0건. 수집기가 앱이 놓친 줄을 가리고 masked_at_collector="true"를 붙이며 경보 메일이 온다.
          SECURITY 줄의 trace_id·주문 번호 같은 값은 가리지 않는다(정상 시험).
"""
import os
import time
import uuid

import pytest

from daon import observe
from replay import _logs, _obs
from replay import t21_log_leak as t21

WAIT = float(os.environ.get("DAON_ALERT_WAIT", "150"))


def test_app_logs_have_no_secrets_after_login_flows():
    since = _logs.now()
    t21.flows()
    time.sleep(1.5)
    c = t21.count(t21.app_lines(since))
    assert sum(c.values()) == 0, f"기대: 앱 로그에 평문 비밀번호·JWT·휴대전화 모양 0줄. 실제 {c}"


def test_loki_has_no_secrets_after_login_flows():
    assert _obs.a05_on() and _obs.loki_ready(timeout=5), "기대: A5(Loki)가 떠 있음(8-4)"
    app, lk, _ = t21.scan()
    assert lk is not None and sum(lk.values()) == 0, f"기대: Loki에도 0줄. 실제 앱 {app}, Loki {lk}"


def test_web_log_aspect_no_longer_logs_response_body():
    from daon.config import ROOT
    src = (ROOT / "spring/mall-common/src/main/java/com/macro/mall/common/log/WebLogAspect.java").read_text("utf-8")
    assert "setResult(null)" in src and "LOGGER.debug" in src, "기대: WebLogAspect가 응답 본문을 남기지 않고 DEBUG로"


def test_collector_masks_line_the_app_missed_and_flags_it():
    marker = f"collector-test-{uuid.uuid4().hex[:10]}"
    observe.inject("mall-portal", observe.collector_samples(marker)["leak"])
    rows = _obs.wait_loki(f'{{app="mall-portal"}} |= "{marker}"', timeout=60)
    assert rows, "기대: 넣은 줄이 Loki에 들어감"
    labels, line = rows[0]
    assert observe.FAKE_JWT not in line and "eyJ***" in line, "기대: 수집기가 JWT 모양 값을 가림"
    assert labels.get("masked_at_collector") == "true" and labels.get("leak") == "jwt", f"기대: 가린 줄 표시(masked_at_collector·leak=jwt). 실제 {labels}"


def test_masked_line_raises_alert_mail():
    start = _obs.now_utc()
    marker = f"collector-test-{uuid.uuid4().hex[:10]}"
    observe.inject("mall-portal", observe.collector_samples(marker)["leak"])
    rows = _obs.wait_loki(f'{{app="mall-portal"}} |= "{marker}"', timeout=60)
    assert rows and rows[0][0].get("masked_at_collector") == "true", "기대: 수집기가 이 줄을 가리고 표시함(그래야 경보가 울린다)"
    mail = _obs.wait_mail("log.leak.masked_at_collector", since=start - 900, timeout=WAIT)
    assert mail, f"기대: [다온 경보] log.leak.masked_at_collector 메일({int(WAIT)}초 안)"


@pytest.mark.normal
def test_trace_id_and_order_number_are_not_masked():
    marker = f"collector-test-{uuid.uuid4().hex[:10]}"
    observe.inject("mall-portal", observe.collector_samples(marker)["security"])
    if not (_obs.a05_on() and _obs.loki_ready()):
        pytest.skip("A5가 없어 Loki 확인은 건너뜀")
    rows = _obs.wait_loki(f'{{app="mall-portal"}} |= "{marker}"', timeout=60)
    assert rows and "202611100100000001" in rows[0][1] and marker in rows[0][1], "기대: 주문 번호·trace_id는 가리지 않음"


@pytest.mark.normal
def test_member_login_and_orders_still_work():
    from replay._http import portal, portal_login
    with portal() as c:
        tok = portal_login(c, "alice")
        r = c.get("/order/list", params={"status": -1, "pageNum": 1, "pageSize": 5}, headers={"Authorization": tok})
        assert r.json().get("code") == 200, f"기대: 주문 목록 정상. 실제 {r.text[:80]}"
