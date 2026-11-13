"""11-2 한 번 파싱해서 검사와 사용에 같이 쓰기 · T28.

시작 지점: GET /daon/go?next= 가 문자열 앞부분만 비교 → 교과서 예시 세 개가 허용 목록 밖 호스트로 이동 → 레드.
완료 지점: SafeRedirect.resolve(): 한 번 파싱(실패하면 거부) → 정규화 → 규칙 → 같은 URI 객체로 Location
          ① 세 예시는 Location: / ② 파서가 못 읽는 주소도 / ③ redirect.denied.not_allowed(받은 주소 원문 없음)
정상 시험: 상대 경로(/orders/9001)와 허용 호스트의 정상 주소(https://shop.daon.example/…)는 그대로 이동한다.
"""
import pytest

from replay import _logs
from replay import t28_open_redirect as t28


@pytest.mark.parametrize("label,nxt", t28.EXAMPLES, ids=["double-slash", "suffix-host", "userinfo"])
def test_textbook_example_goes_home(label, nxt):
    loc = t28.go(nxt)
    assert loc == "/", f"기대: {label} 예시는 이동하지 않고 Location: /. 실제 {loc!r}(브라우저가 갈 호스트 {t28.browser_host(loc)})"


def test_unparseable_next_is_denied():
    loc = t28.go("/orders/%zz")
    assert loc == "/", f"기대: URI로 읽지 못하는 주소(잘못된 % 표기)는 거부하고 Location: /. 실제 {loc!r}"


def test_denied_redirect_is_logged_without_raw_value():
    since = _logs.now()
    nxt = "https://shop.daon.example@other.example/logcheck"
    assert t28.go(nxt) == "/", "기대: 거부"
    e = _logs.wait_event("mall-portal", since, "redirect.denied.not_allowed", "11-2", timeout=8)
    assert e, "기대: SECURITY redirect.denied.not_allowed 한 줄"
    assert e.get("target") == "path:/daon/go", f"기대: target path:/daon/go. 실제 {e.get('target')}"
    assert "other.example" not in str(e) and "logcheck" not in str(e), f"기대: 받은 주소 원문을 로그에 넣지 않음(규칙 번호만). 실제 {e}"
    assert "규칙" in str(e.get("reason", "")), f"기대: reason에 규칙 번호. 실제 {e.get('reason')}"


@pytest.mark.normal
def test_relative_path_is_kept():
    loc = t28.go("/orders/9001")
    assert loc == "/orders/9001", f"기대: 정상 상대 경로는 그대로 이동. 실제 {loc!r}"


@pytest.mark.normal
def test_allowed_host_is_kept():
    loc = t28.go("https://shop.daon.example/orders/9001")
    assert loc == "https://shop.daon.example/orders/9001", f"기대: 허용 호스트의 정상 주소는 그대로. 실제 {loc!r}"
