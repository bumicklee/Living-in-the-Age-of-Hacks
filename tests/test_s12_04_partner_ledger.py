"""12-4 연동 앱 대장과 대량 내보내기 탐지 · T33 · A9 + A5.

시작 지점(12-3 완료): 대장 API·일괄 폐기 기능이 없고, 내보내기가 기록되지 않아 평소의 몇 배로 내보내도 경보가 없다 → 레드.
완료 지점:
  ① make partner-audit(관리자 앱 GET /daon/partner/apps)가 시드 대장에서 「90일 넘게 안 쓴 앱」 legacy-01, 「내보내기 범위」 analytics-01
  ② 내보내기 응답마다 partner.export(INFO, count = 내보낸 행 수, 응답 본문 없음)
  ③ 분석 파트너가 내보내기를 기준선(100행/10분)의 3배 넘게 부르면 Grafana 경보 → Mailpit 「[다온 경보] partner.export.bulk」
  ④ make partner-revoke APP=courier-01 뒤 그 앱의 이미 발급된 토큰·새 발급 모두 401 + partner.denied.revoked
  ⑤ 범위를 넓히면 partner.app.changed(mall-admin, actor admin:<계정>)
정상 시험: 내보내기 범위가 있는 분석 파트너는 내보내기를 받는다.
경보는 평가 주기(30초)·묶음 대기 때문에 바로 오지 않는다. 최대 DAON_ALERT_WAIT(기본 150초) 기다린다.
같은 파트너의 경보가 이미 떠 있으면(10분 안에 다시 돌림) 새 메일이 오지 않을 수 있다(8-4 시험과 같은 한계).
시험이 바꾼 상태(폐기·범위)는 끝에 되돌린다(make partner-reset).
"""
import os
import time

import pytest

from daon import partner as dp
from replay import _logs, _obs
from replay import _partner as pt
from replay import t33_partner_token as t33

WAIT = float(os.environ.get("DAON_ALERT_WAIT", "150"))


@pytest.fixture
def restore():
    yield
    dp.reset()


def test_partner_audit_finds_stale_and_export_apps():
    rows = dp.ledger()
    assert rows is not None, "기대: 관리자 앱에 연동 앱 대장(GET /daon/partner/apps)"
    ids = {r["id"] for r in rows}
    assert {"courier-01", "analytics-01", "legacy-01"} <= ids, f"기대: 시드 파트너 셋이 대장에. 실제 {sorted(ids)}"
    assert all(r.get("owner") for r in rows if r["id"] in ("courier-01", "analytics-01", "legacy-01")), "기대: 대장에 담당자 칸"
    found = dp.audit(rows)
    assert found["stale"] == ["legacy-01"], f"기대: 90일 넘게 안 쓴 앱 1개(legacy-01). 실제 {found['stale']}"
    assert found["export_scope"] == ["analytics-01"], f"기대: 내보내기 범위를 가진 앱 1개(analytics-01). 실제 {found['export_scope']}"


def test_export_is_logged_with_row_count_only():
    since = _logs.now()
    a = pt.app(pt.ANALYTICS)
    a.token("orders:read:assigned orders:export")
    st, body = a.export()
    assert st == 200, f"기대: 분석 파트너 내보내기 200. 실제 HTTP {st}"
    e = _logs.wait_event("mall-portal", since, "partner.export", "12-4", timeout=8)
    assert e, "기대: 내보내기 응답마다 SECURITY partner.export 한 줄"
    assert e.get("actor") == "partner:analytics-01" and e.get("count") == body.get("count"), \
        f"기대: actor partner:analytics-01, count = 내보낸 행 수 {body.get('count')}. 실제 {e.get('actor')}, {e.get('count')}"
    items = body.get("items") or [{}]
    leaked = [v for v in (items[0].get("receiverName"), items[0].get("orderSn")) if v and str(v) in str(e)]
    assert not leaked, "기대: 이벤트에 응답 본문(받는 사람·주문 번호)을 넣지 않음"


def test_export_spike_sends_alert_mail():
    assert _obs.a05_on(), "기대: A5(Loki·Grafana·Mailpit)가 켜져 있음"
    since = _obs.now_utc() - 5
    r = t33.export_scene()
    assert r["rows"] > 300, f"기대: 재연이 기준선의 3배(300행) 넘게 내보냄. 실제 {r['rows']}행"
    m = _obs.wait_mail("partner.export.bulk", since, timeout=WAIT)
    assert m, f"기대: {WAIT:.0f}초 안에 Mailpit에 「[다온 경보] partner.export.bulk」. 경보 규칙 infra/observability/rules/partner.yaml"
    assert "analytics-01" in m["subject"], f"기대: 메일 제목에 어느 파트너인지. 실제 {m['subject']}"


def test_partner_revoke_kills_issued_tokens_and_secrets(restore):
    tok = pt.token(pt.COURIER)
    assert pt.get("/partner/orders?page=1&size=5", tok, cert_of=pt.COURIER).status_code == 200, "준비: 폐기 전에는 200"
    since = _logs.now()
    body = dp.revoke(pt.COURIER)
    assert body and body.get("code") == 200, f"기대: 관리자 앱 POST /daon/partner/apps/courier-01/revoke. 실제 {body}"
    time.sleep(1)
    r = pt.get("/partner/orders?page=1&size=5", tok, cert_of=pt.COURIER)
    assert r.status_code == 401, f"기대: 폐기 전에 받은 토큰도 401(폐기 목록). 실제 HTTP {r.status_code}"
    e = _logs.wait_event("mall-portal", since, "partner.denied.revoked", "12-4", timeout=8)
    assert e and e.get("actor") == "partner:courier-01", f"기대: partner.denied.revoked(actor partner:courier-01). 실제 {e}"
    st, _ = pt.app(pt.COURIER).request("POST", "/partner/token", form={
        "grant_type": "client_credentials", "client_id": pt.COURIER, "client_secret": dp.seed_secret(pt.COURIER)})
    assert st == 401, f"기대: 폐기한 앱은 새 토큰도 못 받음. 실제 HTTP {st}"
    ch = _logs.wait_event("mall-admin", since, "partner.app.changed", "12-4", timeout=8)
    assert ch and str(ch.get("actor", "")).startswith("admin:") and ch.get("target") == "partner:courier-01", \
        f"기대: mall-admin partner.app.changed(actor admin:…, target partner:courier-01). 실제 {ch}"


def test_scope_expansion_is_logged(restore):
    since = _logs.now()
    body = dp.set_scopes(pt.LEGACY, "orders:read:assigned orders:export")
    assert body and body.get("code") == 200, f"기대: 관리자 앱 POST /daon/partner/apps/legacy-01/scopes. 실제 {body}"
    e = _logs.wait_event("mall-admin", since, "partner.app.changed", "12-4", timeout=8, target="partner:legacy-01")
    assert e and "orders:export" in str(e.get("reason", "")), f"기대: 범위 확대 → partner.app.changed(reason에 +orders:export). 실제 {e}"


@pytest.mark.normal
def test_analytics_partner_can_export_with_scope():
    a = pt.app(pt.ANALYTICS)
    a.token("orders:read:assigned orders:export")
    st, body = a.export()
    assert st == 200 and body.get("count", 0) > 0, f"기대: 내보내기 범위가 있는 분석 파트너는 내보내기 200. 실제 HTTP {st}"
