"""12-2 파트너 API: 최소 범위·짧은 수명·회전 · T33 · A9.

시작 지점: 고정 키(X-Partner-Key) 하나로 모든 주문·내보내기, 만료 없음, 앱 직접 주소·고객 입구로도 열림 → 레드.
완료 지점(A9 파트너 입구 8087 + 15분 토큰 + @PartnerScope + 조회 조건에 파트너 + 비밀값 회전):
  ① 배송 파트너는 배정된 주문만(시드 81건), 배정 밖 주문 상세는 404 + access.denied.not_owner(actor partner:)
  ② orders:export 범위 없는 토큰으로 내보내기 403 + partner.denied.scope
  ③ 발급 16분 뒤(랩 시계 A13) 토큰 401 + partner.denied.token_expired
  ④ 비밀값 회전 중 옛 값·새 값 모두 발급, 겹침 10분 뒤 옛 값 거부
  ⑤ 파트너 토큰을 고객 입구(앱 고객 API)에 내밀면 401, 고객 웹·고객 API 입구로 파트너 경로를 부르면 404
정상 시험: 파트너 앱 스텁이 주문 첫 쪽을 받고, 배정된 주문 하나의 상세를 본다(시작 지점에서는 고정 키로, 완료 지점에서는 토큰으로).
시험이 바꾼 비밀값·랩 시계는 끝에 되돌린다(make partner-reset과 같은 일).
"""
import pytest

from daon import partner as dp
from replay import _logs
from replay import _partner as pt


@pytest.fixture
def restore():
    yield
    pt.reset_clock()
    dp.reset()


def test_courier_sees_only_assigned_orders():
    mine = pt.assigned(pt.COURIER)
    rows = pt.app(pt.COURIER).all_orders()
    got = {int(r["id"]) for r in rows}
    assert got == mine, f"기대: 배정된 주문 {len(mine)}건만. 실제 {len(got)}건(배정 밖 {len(got - mine)}건)"


def test_unassigned_order_is_404_and_logged():
    oid = pt.unassigned_order(pt.COURIER)
    since = _logs.now()
    tok = pt.token(pt.COURIER)
    r = pt.get(f"/partner/orders/{oid}", tok, cert_of=pt.COURIER)
    assert r.status_code == 404, f"기대: 배정 밖 주문 {oid}은 404(없는 주문과 같은 답). 실제 HTTP {r.status_code}"
    e = _logs.wait_event("mall-portal", since, "access.denied.not_owner", "12-2", timeout=8)
    assert e and e.get("actor") == "partner:courier-01" and e.get("target") == f"order:{oid}", \
        f"기대: access.denied.not_owner(actor partner:courier-01, target order:{oid}). 실제 {e}"


def test_export_without_scope_is_403_and_logged():
    since = _logs.now()
    tok = pt.token(pt.COURIER)
    assert "orders:export" not in str(pt.payload(tok).get("scope", "")), "기대: 내보내기 범위는 기본 발급에서 빠짐"
    r = pt.get("/partner/orders/export", tok, cert_of=pt.COURIER)
    assert r.status_code == 403, f"기대: orders:export 없는 토큰으로 내보내기 403. 실제 HTTP {r.status_code}"
    e = _logs.wait_event("mall-portal", since, "partner.denied.scope", "12-2", timeout=8)
    assert e and e.get("actor") == "partner:courier-01" and e.get("target") == "path:/partner/orders/export", \
        f"기대: partner.denied.scope(actor partner:courier-01, target path:/partner/orders/export). 실제 {e}"
    assert "eyJ" not in str(e), "기대: 토큰 값은 이벤트에 넣지 않음"


def test_courier_cannot_get_export_scope():
    a = pt.app(pt.COURIER)
    tok = a.token()
    assert tok, "기대: 파트너 토큰 발급(POST /partner/token)"
    st, body = a.request("POST", "/partner/token", form={"grant_type": "client_credentials", "client_id": pt.COURIER,
                                                          "client_secret": dp.seed_secret(pt.COURIER), "scope": "orders:export"})
    assert st == 400 and body.get("error") == "invalid_scope", f"기대: 대장에 없는 범위를 청하면 400 invalid_scope. 실제 HTTP {st} {body}"


def test_token_expires_after_15_minutes(restore):
    tok = pt.token(pt.COURIER)
    p = pt.payload(tok)
    assert 0 < int(p.get("exp", 0)) - int(p.get("iat", 0)) <= 900, f"기대: 수명 15분 이하. 실제 exp-iat {p.get('exp', 0) - p.get('iat', 0)}"
    since = _logs.now()
    pt.skip_minutes(16)
    r = pt.get("/partner/orders?page=1&size=5", tok, cert_of=pt.COURIER)
    assert r.status_code == 401, f"기대: 16분 뒤 같은 토큰 401. 실제 HTTP {r.status_code}"
    e = _logs.wait_event("mall-portal", since, "partner.denied.token_expired", "12-2", timeout=8)
    assert e and e.get("actor") == "partner:courier-01", f"기대: partner.denied.token_expired(INFO). 실제 {e}"


def test_secret_rotation_has_overlap_then_old_secret_is_refused(restore):
    old = dp.seed_secret(pt.COURIER)
    a = pt.app(pt.COURIER, secret=old)
    new = a.rotate()
    assert new and new != old, "기대: 회전하면 새 비밀값(한 번만 보임)"
    assert pt.app(pt.COURIER, secret=old).token(), "기대: 겹침 기간에는 옛 비밀값으로도 발급"
    assert pt.app(pt.COURIER, secret=new).token(), "기대: 새 비밀값으로 발급"
    pt.skip_minutes(11)
    st, body = pt.app(pt.COURIER, secret=old).request("POST", "/partner/token", form={
        "grant_type": "client_credentials", "client_id": pt.COURIER, "client_secret": old})
    assert st == 401, f"기대: 겹침 10분 뒤 옛 비밀값 401 invalid_client. 실제 HTTP {st} {body}"
    assert pt.app(pt.COURIER, secret=new).token(), "기대: 새 비밀값은 그대로 발급"


def test_partner_token_is_refused_at_customer_entrances():
    tok = pt.token(pt.COURIER)
    r = pt.customer_get(pt.CUSTOMER_PROBE, tok, "portal")
    assert r.status_code == 401, f"기대: 파트너 토큰(aud daon-partner)을 고객 API에 내밀면 401. 실제 HTTP {r.status_code}"
    w = pt.customer_get("/partner/orders?page=1&size=5", tok, "web")
    assert w.status_code == 404, f"기대: 고객 웹(8088)으로 부른 파트너 경로는 404(파트너 입구로만). 실제 HTTP {w.status_code}"


def test_partner_path_is_404_on_app_port():
    pt.require_a9()
    tok = pt.token(pt.COURIER)
    import httpx
    from daon.config import URLS
    r = httpx.get(URLS["spring"]["portal"] + "/partner/orders", headers={"Authorization": f"Bearer {tok}"}, timeout=10)
    assert r.status_code == 404, f"기대: 앱 직접 주소(8085)로 온 파트너 요청은 404(PartnerEntrance). 실제 HTTP {r.status_code}"


@pytest.mark.normal
def test_partner_app_reads_first_page():
    body = pt.app(pt.COURIER).orders(1, 10)
    assert body.get("items"), f"기대: 파트너 앱이 주문 첫 쪽을 받음. 실제 {list(body)}"


@pytest.mark.normal
def test_partner_app_reads_an_assigned_order():
    oid = sorted(pt.assigned(pt.COURIER))[0]
    st, body = pt.app(pt.COURIER).order(oid)
    assert st == 200 and body.get("id") == oid, f"기대: 배정된 주문 {oid} 상세 200. 실제 HTTP {st}"
