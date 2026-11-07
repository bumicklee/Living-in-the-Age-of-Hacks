"""2-5 이미지 URL 가져오기가 내부망을 연다(SSRF) · T04 · 아키텍처 A2(이그레스 프록시)."""
import pytest

from daon import stack as st
from replay import _logs
from replay import t04_ssrf as t04


def _curl_from_admin_app(*args: str) -> str:
    """관리자 앱 컨테이너 안에서 curl. HTTP 코드만 돌려준다(닿지 않으면 000)."""
    r = st.compose("spring", "exec", "-T", "mall-admin", "curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                   "--max-time", "4", *args, check=False, capture=True)
    return (r.stdout or "").strip()[-3:] or "000"


def test_internal_addresses_blocked(stack):
    r = t04.run(stack)
    assert not r.vulnerable, "기대: 이미지 가져오기로 내부 주소의 응답을 받을 수 없어야 합니다\n" + r.show()


def test_app_reaches_outside_only_through_proxy(stack):
    code = _curl_from_admin_app("--noproxy", "*", t04.PARTNER_IMAGE)
    assert code == "000", f"기대: A2에서는 관리자 앱이 프록시 없이 밖(이미지 공급사)에 닿지 않아야 합니다. 실제 HTTP {code}"


def test_proxy_denies_internal(stack):
    code = _curl_from_admin_app("-x", "http://egress:3128", "http://mall-portal:8085/actuator/health")
    assert code == "403", f"기대: 이그레스 프록시가 내부 주소를 403으로 거부해야 합니다. 실제 HTTP {code}(000이면 프록시가 없음)"


def test_blocked_fetch_is_logged(stack):
    since = _logs.now()
    t04.fetch(t04.INTERNAL[0])
    e = _logs.wait_event("mall-admin", since, "egress.denied.internal_address", "2-5", stack)
    assert e, "기대: 내부 주소를 막으면 mall-admin 로그에 SECURITY egress.denied.internal_address(2-5)가 남아야 합니다"
    assert e.get("target") == "host:127.0.0.1" and "/ops/config" not in str(e), f"기대: target은 호스트 이름만. 실제: {e}"


@pytest.mark.normal
def test_partner_image_fetch_works(stack):
    r = t04.fetch(t04.PARTNER_IMAGE)
    assert r.headers.get("content-type", "").startswith("image/png") and r.content[:8] == b"\x89PNG\r\n\x1a\n", \
        f"기대: 허용한 공급사의 상품 이미지는 그대로 가져와야 합니다. 실제: {r.headers.get('content-type')} {r.text[:120]!r}"
