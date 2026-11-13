"""11-3 요청 밀반입: 게이트웨이와 앱이 경계를 다르게 본다 · T29(방어 확인).

공격 요청은 보내지 않는다. 시험은 두 가지다.
  ① 설정 점검(make audit-gateway와 같은 표)이 모두 통과
  ② 「방어 확인」: 게이트웨이(8086)가 모호한 요청 세 가지를 400으로 끊고 request.denied.malformed 한 줄을 남긴다.
     요청 본문에는 다른 요청이 없다(길이 표시 둘은 두 해석이 같은 곳에서 끝나게 만든 것, 나머지는 본문 없음). replay/_boundary.py
시작 지점(A1 게이트웨이, 고객 API 입구 없음): ①은 고칠 것 4개, ②는 입구가 없어 「준비 안 됨」 → 레드.
정상 시험: 로그인·상품 목록·주문 상세와 정상 청크 전송 요청이 통과한다(A7 입구가 있으면 그 경로로, 없으면 앱 직접).
"""
import httpx
import pytest

from daon import boundary
from daon.config import URLS
from replay import _boundary as gb
from replay import _logs
from replay._http import ACCOUNTS


def test_audit_gateway_all_pass():
    rows = boundary.gateway_rows()
    bad = boundary.gateway_failed(rows)
    assert not bad, "기대: 설정 점검 모두 확인. 고칠 것: " + " · ".join(bad)


def _rejected_with_event(probe, label: str):
    gb.require_gateway()
    since = _logs.now()
    ua = gb.tag(label)
    code = probe(ua)
    assert code == 400, f"기대: 게이트웨이가 {label} 요청을 400으로 끊음. 실제 HTTP {code}"
    e = _logs.wait_event("gateway", since, "request.denied.malformed", "11-3", timeout=8, ua=ua)
    assert e, f"기대: 게이트웨이 로그에 SECURITY request.denied.malformed(ua={ua}) 한 줄"
    assert e.get("actor") == "anonymous" and str(e.get("target", "")).startswith("path:"), f"기대: actor anonymous, target path:. 실제 {e}"


def test_two_length_headers_rejected_with_event():
    _rejected_with_event(gb.probe_two_lengths, "two-lengths")


def test_duplicate_content_length_rejected_with_event():
    _rejected_with_event(gb.probe_duplicate_length, "dup-length")


def test_folded_header_rejected_with_event():
    _rejected_with_event(gb.probe_folded_header, "folded")


def _base() -> tuple[str, str]:
    """(주소, 앞붙임). A7 입구가 있으면 게이트웨이 경로(/api/…), 없으면 앱 직접."""
    return (gb.SHOP, "/api") if gb.gateway_up() else (URLS["spring"]["portal"], "")


@pytest.mark.normal
def test_normal_requests_pass():
    base, pre = _base()
    with httpx.Client(base_url=base, timeout=10) as c:
        r = c.post(pre + "/sso/login", data={"username": "alice", "password": ACCOUNTS["alice"]["password"]})
        body = r.json()
        assert r.status_code == 200 and body.get("code") == 200, f"기대: 로그인 200. 실제 HTTP {r.status_code} {body.get('message')}"
        token = body["data"]["tokenHead"] + body["data"]["token"]
        r = c.get(pre + "/product/search", params={"pageNum": 1, "pageSize": 5})
        assert r.status_code == 200, f"기대: 상품 목록 200. 실제 HTTP {r.status_code}"
        oid = ACCOUNTS["alice"]["orders"][0]
        r = c.get(f"{pre}/order/detail/{oid}", headers={"Authorization": token})
        assert r.status_code == 200 and r.json().get("code") == 200, f"기대: 내 주문 상세 200. 실제 HTTP {r.status_code}"


@pytest.mark.normal
def test_plain_chunked_body_is_not_rejected():
    base, pre = _base()
    form = f"username=alice&password={ACCOUNTS['alice']['password'].replace('!', '%21')}".encode()
    r = httpx.post(base + pre + "/sso/login", content=iter([form]),           # 길이를 모르는 본문 → httpx가 Transfer-Encoding: chunked로 보냄
                   headers={"Content-Type": "application/x-www-form-urlencoded"}, timeout=10)
    assert r.status_code == 200 and r.json().get("code") == 200, f"기대: Transfer-Encoding 하나뿐인 정상 요청은 통과. 실제 HTTP {r.status_code}"
