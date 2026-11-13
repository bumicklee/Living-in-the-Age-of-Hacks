"""8-4 모으기와 경보(A5) · 인증 실패 상태 코드(F32) · 재연 T20.

시작 지점: Loki·Grafana가 없다, T20 뒤에도 경보 메일이 없다, 로그인 없는 요청이 HTTP 200 → 레드.
완료 지점: ① T20 뒤 Mailpit에 [다온 경보] data.read.bulk ② T01 6번 → access.denied.not_owner 경보
          ③ 로그인 없음 401·권한 없음 403 + auth.denied.token ④ 경보 규칙 파일이 SECURITY_EVENTS.md에서 만든 것과 같음
          ⑤ 평범한 사용에는 경보가 없음(정상 시험).
경보는 평가 주기(30초)·묶음 대기 때문에 바로 오지 않는다. 시험은 최대 150초씩 기다린다(DAON_ALERT_WAIT).
"""
import os
import sys
import time
import uuid

import httpx
import pytest

from daon.config import ROOT
from replay import _logs, _obs
from replay import t01_order_idor as t01
from replay import t20_bulk_read as t20
from replay._http import admin, admin_login, new_trace_id, portal

WAIT = float(os.environ.get("DAON_ALERT_WAIT", "150"))


def test_unauthenticated_admin_request_is_http_401():
    with admin() as c:
        r = c.get("/daon/customer/list")
    assert r.status_code == 401 and r.json().get("code") == 401, f"기대: 로그인 없는 요청은 HTTP 401(본문 code 401 그대로). 실제 HTTP {r.status_code}"


def test_unauthenticated_portal_request_is_http_401():
    """보안 필터가 막는 주소(/cart/list). /sso/info처럼 컨트롤러가 직접 본문 code 401을 돌려주는 곳은 아직 HTTP 200이다(구현-메모 문제 목록)"""
    with portal() as c:
        r = c.get("/cart/list")
    assert r.status_code == 401, f"기대: 고객 API도 로그인 없는 요청은 HTTP 401. 실제 HTTP {r.status_code}"


def test_forbidden_is_http_403():
    from replay.t09_login_guard import clear_counters
    with admin() as c:
        clear_counters()
        tok = admin_login(c, "staff_goods")
        r = c.get("/daon/customer/list", headers={"Authorization": tok})
    assert r.status_code == 403 and r.json().get("code") == 403, f"기대: 권한 없는 직원은 HTTP 403. 실제 HTTP {r.status_code}"


def test_auth_denied_token_is_logged_without_token_value():
    since = _logs.now()
    t = f"lab-s84-{uuid.uuid4().hex[:10]}"
    fake = "eyJsYWJGYWtlSGVhZGVy.eyJsYWJGYWtlQm9keQ.ZmFrZVNpZw"
    with admin() as c:
        c.get("/daon/customer/list", headers={"X-Trace-Id": t})
        c.get("/daon/customer/list", headers={"X-Trace-Id": t + "b", "Authorization": "Bearer " + fake})
    e = _logs.wait_event("mall-admin", since, "auth.denied.token", "8-4", timeout=8, trace_id=t)
    assert e and e.get("reason") == "토큰 없음" and e.get("target") == "path:/daon/customer/list", f"기대: auth.denied.token(8-4, 토큰 없음). 실제 {e}"
    e2 = _logs.wait_event("mall-admin", since, "auth.denied.token", "8-4", timeout=8, trace_id=t + "b")
    assert e2 and e2.get("reason") == "토큰 만료·서명 불일치", f"기대: 틀린 토큰은 「토큰 만료·서명 불일치」. 실제 {e2}"
    assert fake not in str(e2), "기대: 토큰 값은 이벤트에 넣지 않음"


def test_alert_rules_file_is_generated_from_security_events():
    sys.path.insert(0, str(ROOT / "tools"))
    import alert_rules
    out = alert_rules.OUT
    assert out.exists(), "기대: make alert-rules로 만든 infra/observability/rules/security.yaml이 있음"
    text, rules, _ = alert_rules.build()
    assert out.read_text("utf-8") == text, "기대: 규칙 파일이 지금 SECURITY_EVENTS.md 표에서 만든 것과 같음(표를 바꿨으면 make alert-rules)"
    assert any(r.event == "access.denied.not_owner" and r.threshold == 5 for r in rules), "기대: 번호 훑기 규칙(같은 actor 10분 5건)"


def test_a5_collects_security_audit_and_tool_streams():
    assert _obs.a05_on(), "기대: infra/arch.txt에 a05(A5: Loki·Alloy·Grafana)"
    assert _obs.loki_ready(timeout=5), f"기대: Loki가 떠 있음({_obs.LOKI}/ready)"
    t20.bulk_read("s84-audit", calls=1)
    sec = _obs.wait_loki('{stream="security"}', timeout=60, since_s=86400)
    assert sec and sec[0][0].get("event"), "기대: 앱 SECURITY 줄이 stream=security, event 레이블로 Loki에"
    assert sec[0][1].startswith("{"), "기대: SECURITY 줄은 JSON 부분만 줄로 남김"
    aud = _obs.wait_loki('{stream="audit", app="mall-admin"}', timeout=60, since_s=3600)
    assert aud, "기대: 감사 로그 파일이 stream=audit으로 Loki에"
    for labels, _ in sec[:50] + aud[:50]:
        assert not {"actor", "target", "trace_id"} & set(labels), f"기대: 값이 많은 것은 레이블로 두지 않음. 실제 {labels}"


def test_grafana_loaded_generated_and_bulk_rules():
    assert _obs.wait_grafana(timeout=90), f"기대: Grafana가 떠 있음({_obs.GRAFANA})"
    titles = {r.get("title") for r in _obs.grafana_rules()}
    for want in ("access.denied.not_owner", "data.read.bulk", "auth.denied.token", "egress.traffic.high"):
        assert want in titles, f"기대: Grafana가 경보 규칙 {want}을 읽음. 읽은 것 {len(titles)}개"


def test_bulk_read_sends_alert_mail():
    r = t20.run("spring")
    assert not r.vulnerable, "기대: 관리자 대량 조회 뒤 [다온 경보] data.read.bulk 메일\n" + r.show()


def test_order_number_scan_sends_alert_mail():
    assert _obs.a05_on() and _obs.wait_grafana(timeout=90), "기대: 경보를 만드는 곳(A5: Grafana)이 떠 있음"
    start = _obs.now_utc()
    for _ in range(6):
        t01.run("spring")
    got = _obs.wait_alert("access.denied.not_owner", since=start - 900, actor_part="member:", timeout=WAIT)
    assert got, f"기대: 남의 주문 6번 → [다온 경보] access.denied.not_owner member:… 메일 또는 울리는 경보({int(WAIT)}초 안)"


@pytest.mark.normal
def test_normal_paging_sends_no_alert():
    """두 요청의 trace_id에 대량 조회 이벤트가 없어야 한다. 이전 시험의 지연 메일은 이 요청의 결과가 아니다."""
    from replay.t09_login_guard import clear_counters
    since = _logs.now()
    traces = [new_trace_id("s84page") for _ in range(2)]
    with admin() as c:
        clear_counters()
        tok = admin_login(c, "staff_order")
        for page, tid in enumerate(traces, 1):
            r = c.get("/order/list", params={"pageNum": page, "pageSize": 10},
                      headers={"Authorization": tok, "X-Trace-Id": tid})
            assert r.json().get("code") == 200, f"기대: 주문 목록 정상. 실제 {r.text[:80]}"
    time.sleep(2)
    noisy = [e for e in _logs.security_events("mall-admin", since)
             if e.get("event") == "data.read.bulk" and e.get("trace_id") in traces]
    assert not noisy, f"기대: 한 쪽 10건씩 두 번의 정상 조회에는 대량 조회 이벤트가 없음. 실제 {noisy}"
