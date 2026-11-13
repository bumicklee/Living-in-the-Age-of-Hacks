"""11-4 남의 계정 페이지가 캐시에 남는다 · T30.

시작 지점(A7, 회사 설정 VCL): /api/ GET을 60초 강제 캐시 → bob·토큰 없는 요청이 alice 정보를 받음 → 레드.
완료 지점: ① Varnish: 인증 요청 pass, 오리진 private·no-store·no-cache는 저장 안 함, 오류 응답 저장 안 함
          ② 오리진(DaonCacheHeaderFilter): 인증 응답 Cache-Control: private, no-store, 공개 상품 목록 public, max-age=60
          ③ 게이트웨이: 인증 요청에 캐시 적중이면 cache.hit.authenticated(설정 확인. 완료 지점에서는 적중이 생기지 않는다)
정상 시험: 공개 상품 목록은 여전히 캐시 적중, alice는 자기 정보를 받는다. 시험마다 캐시를 먼저 비운다.
/sso/info는 로그인 없는 허용 목록(/sso/**)이라 토큰이 없으면 HTTP 200 + 본문 code 401(data 없음)이다.
"""
import httpx
import pytest

from daon import boundary
from replay import _boundary as gb
from replay import t30_private_cache as t30
from replay._http import ACCOUNTS, portal, portal_login

ALICE, BOB = ACCOUNTS["alice"]["member_id"], ACCOUNTS["bob"]["member_id"]


@pytest.fixture
def tokens():
    gb.require_gateway()
    boundary.purge_cache()
    with portal() as c:
        return portal_login(c, "alice"), portal_login(c, "bob")


def test_bob_gets_his_own_info(tokens):
    alice, bob = tokens
    t30.info(alice)
    st, mid, xc, _ = t30.info(bob)
    assert mid == BOB, f"기대: bob은 자기 정보(회원 {BOB}). 실제 회원 {mid}, X-Cache {xc}"


def test_anonymous_does_not_get_alice_info(tokens):
    alice, _ = tokens
    t30.info(alice)
    st, mid, xc, _ = t30.info(None)
    assert mid is None, f"기대: 토큰 없는 요청은 회원 정보 없음(본문 code 401). 실제 HTTP {st}, 회원 {mid}, X-Cache {xc}"


def test_authenticated_response_never_hits_cache(tokens):
    alice, _ = tokens
    t30.info(alice)
    _, _, xc, _ = t30.info(alice)
    assert xc != "HIT", f"기대: 인증 요청은 캐시를 거치지 않음(PASS). 실제 X-Cache {xc}"


def test_authenticated_response_is_private_no_store(tokens):
    alice, _ = tokens
    _, _, _, cc = t30.info(alice)
    assert "private" in cc and "no-store" in cc, f"기대: 오리진이 Cache-Control: private, no-store. 실제 {cc!r}"


def test_error_response_is_not_cached(tokens):
    path = gb.SHOP + "/api/daon/lab/no-such-page"
    first = httpx.get(path, timeout=10)
    second = httpx.get(path, timeout=10)
    assert first.status_code >= 400, f"기대: 없는 주소는 오류. 실제 {first.status_code}"
    assert second.headers.get("x-cache") != "HIT", "기대: 오류 응답(4xx·5xx)은 캐시에 저장하지 않음. 실제 두 번째가 HIT"


def test_gateway_flags_authenticated_cache_hit():
    gb.require_gateway()
    text, where = boundary.gateway_config()
    assert "cache.hit.authenticated" in text and "$daon_auth_hit" in text, \
        f"기대: 게이트웨이 설정에 「인증 요청 + X-Cache: HIT」이면 cache.hit.authenticated 한 줄({where})"


@pytest.mark.normal
def test_public_product_list_is_still_cached(tokens):
    url = gb.SHOP + "/api/product/search"
    httpx.get(url, params={"pageNum": 1, "pageSize": 5}, timeout=10)
    r = httpx.get(url, params={"pageNum": 1, "pageSize": 5}, timeout=10)
    assert r.status_code == 200 and r.headers.get("x-cache") == "HIT", \
        f"기대: 로그인 없는 상품 목록은 두 번째부터 캐시 적중. 실제 HTTP {r.status_code}, X-Cache {r.headers.get('x-cache')}"


@pytest.mark.normal
def test_alice_gets_her_own_info(tokens):
    alice, _ = tokens
    st, mid, _, _ = t30.info(alice)
    assert st == 200 and mid == ALICE, f"기대: alice는 자기 정보(회원 {ALICE}). 실제 HTTP {st}, 회원 {mid}"
