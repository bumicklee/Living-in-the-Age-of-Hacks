"""12-3 토큰 바인딩: 훔친 토큰이 다른 곳에서 안 되게 · T33 · A9(mTLS).

시작 지점(12-2 완료): 파트너 토큰은 무기명이다. 같은 토큰을 같은 PC 안의 다른 클라이언트 설정(인증서 없이·다른 파트너 인증서)이
  내밀어도 배정 주문이 보인다 → 레드.
완료 지점(파트너 입구 https + 클라이언트 인증서, 토큰 cnf = 인증서 지문):
  ① 토큰에 cnf {"x5t#S256": courier-01 인증서 지문}
  ② 같은 토큰을 인증서 없이 → 401 + token.denied.binding_mismatch(actor partner:courier-01)
  ③ 같은 토큰을 다른 파트너(analytics-01)의 인증서로 → 401
  ④ 밖에서 X-Daon-Client-Cert 헤더를 꾸며 넣어도(인증서 없이) 401 = 게이트웨이가 그 헤더를 지운다
  ⑤ 인증서 없이는 토큰도 받지 못한다(발급 401)
정상 시험: 자기 인증서를 쓰는 파트너 앱은 배정 주문을 그대로 본다.
안전 기준: 토큰을 빼내는 방법은 다루지 않는다. 「다른 곳」은 이 PC 안의 다른 클라이언트 설정이다.
"""
import pytest

from daon import partner as dp
from replay import _logs
from replay import _partner as pt

PATH = "/partner/orders?page=1&size=5"


def test_token_is_bound_to_client_certificate():
    tok = pt.token(pt.COURIER)
    cnf = pt.payload(tok).get("cnf") or {}
    assert cnf.get("x5t#S256"), f"기대: 토큰에 cnf.x5t#S256(인증서 지문). 실제 cnf {cnf}"
    paths = dp.cert_paths(pt.COURIER)
    assert paths.get("cert"), "기대: 파트너 입구가 https이고 파트너 인증서가 있음(make partner-certs)"
    assert cnf["x5t#S256"] == dp.thumbprint(paths["cert"]), "기대: cnf 지문 = courier-01 인증서의 SHA-256 지문"


def test_same_token_without_certificate_is_401_and_logged():
    tok = pt.token(pt.COURIER)
    since = _logs.now()
    r = pt.get(PATH, tok, cert_of=None)
    assert r.status_code == 401, f"기대: 같은 토큰을 인증서 없는 클라이언트가 내밀면 401. 실제 HTTP {r.status_code}"
    e = _logs.wait_event("mall-portal", since, "token.denied.binding_mismatch", "12-3", timeout=8)
    assert e and e.get("actor") == "partner:courier-01" and e.get("target") == "path:/partner/orders", \
        f"기대: token.denied.binding_mismatch(actor 토큰 주인 partner:courier-01, target path:/partner/orders). 실제 {e}"


def test_same_token_with_other_partner_certificate_is_401():
    tok = pt.token(pt.COURIER)
    r = pt.get(PATH, tok, cert_of=pt.ANALYTICS)
    assert r.status_code == 401, f"기대: 다른 파트너(analytics-01) 인증서로 같은 토큰을 내밀면 401. 실제 HTTP {r.status_code}"


def test_forged_certificate_header_is_ignored():
    tok = pt.token(pt.COURIER)
    r = pt.get(PATH, tok, cert_of=None, headers={"X-Daon-Client-Cert": pt.cert_header(pt.COURIER)})
    assert r.status_code == 401, f"기대: 밖에서 넣은 X-Daon-Client-Cert는 게이트웨이가 지움 → 401. 실제 HTTP {r.status_code}"


def test_token_request_without_certificate_is_refused():
    pt.require_a9()
    a = pt.app(pt.COURIER, cert_of=None)
    st, body = a.request("POST", "/partner/token", form={"grant_type": "client_credentials", "client_id": pt.COURIER,
                                                          "client_secret": dp.seed_secret(pt.COURIER)})
    assert st == 401, f"기대: 클라이언트 인증서 없이 토큰 발급 401. 실제 HTTP {st} {body.get('error')}"


@pytest.mark.normal
def test_partner_with_its_certificate_reads_assigned_orders():
    body = pt.app(pt.COURIER).orders(1, 10)
    assert body.get("items"), "기대: 자기 인증서를 쓰는 파트너 앱은 배정 주문을 그대로 봄"
