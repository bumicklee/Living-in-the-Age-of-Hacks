"""6-6 CORS 허용 출처 목록·보안 헤더 (점검표 F28·E20, T 번호 없음).

시작 지점: 목록에 없는 출처(이 랩 안의 가짜 이름 evil.example)의 사전 요청에도 두 앱이 그 출처를 그대로 허용+자격증명 허용, 보안 헤더 없음 → 레드.
완료 지점: 허용 헤더 없음, 허용 목록의 출처(고객 웹 8088·관리자 웹 8090)는 정상, API·화면 응답에 헤더 4종, 서버 버전 헤더 없음.
스위치 cors-allowlist·sec-headers를 끄면 각각 레드. 사전 요청은 이 랩의 두 앱에 헤더만 보내 응답 헤더만 읽는다(읽기 전용 점검).
EXPRESS(E02·E03·E19)는 diffs/s6-06/express.diff(실행하지 않음).
"""
import httpx
import pytest

from daon.config import URLS
from replay import _logs

BAD = "http://evil.example"
APPS = {"portal": ("/sso/login", "http://127.0.0.1:8088"), "admin": ("/admin/login", "http://127.0.0.1:8090")}
HEADERS = ["x-content-type-options", "referrer-policy", "x-frame-options", "permissions-policy"]


def preflight(app: str, origin: str) -> httpx.Response:
    path, _ = APPS[app]
    return httpx.options(URLS["spring"][app] + path, headers={"Origin": origin, "Access-Control-Request-Method": "POST",
                                                              "Access-Control-Request-Headers": "content-type"}, timeout=5)


@pytest.mark.parametrize("app", ["portal", "admin"])
def test_unlisted_origin_gets_no_cors_headers(app):
    r = preflight(app, BAD)
    assert "access-control-allow-origin" not in r.headers, f"기대: 목록 밖 출처에는 Access-Control-Allow-Origin이 없음. 실제 {r.headers.get('access-control-allow-origin')!r}"
    assert "access-control-allow-credentials" not in r.headers, "기대: 목록 밖 출처에는 자격증명 허용도 없음"


@pytest.mark.parametrize("app", ["portal", "admin"])
@pytest.mark.normal
def test_listed_origin_still_allowed(app):
    origin = APPS[app][1]
    r = preflight(app, origin)
    assert r.headers.get("access-control-allow-origin") == origin, f"기대: 허용 목록의 출처({origin})는 그대로 허용. 실제 {r.status_code} {r.headers.get('access-control-allow-origin')!r}"


def test_similar_looking_origin_is_not_allowed():
    """포함 검사가 아니라 정확 일치여야 한다."""
    r = preflight("portal", "http://127.0.0.1.evil.example:8088")
    assert "access-control-allow-origin" not in r.headers, "기대: 비슷한 이름은 통과하지 않음(정확 일치)"


@pytest.mark.parametrize("url", ["portal:/sso/info", "admin:/admin/info", "web:/", "adminweb:/"])
def test_security_headers_present_and_no_server_version(url):
    which, path = url.split(":", 1)
    base = {"portal": URLS["spring"]["portal"], "admin": URLS["spring"]["admin"], "web": "http://127.0.0.1:8088", "adminweb": URLS["spring"]["admin_web"]}[which]
    r = httpx.get(base + path, timeout=5)
    missing = [h for h in HEADERS if h not in r.headers]
    assert not missing, f"기대: {which} 응답에 보안 헤더 4종이 모두 있음. 빠진 것 {missing}"
    assert "x-powered-by" not in r.headers, "기대: X-Powered-By 없음"
    assert not any(ch.isdigit() for ch in r.headers.get("server", "")), f"기대: Server 헤더에 버전 숫자가 없음. 실제 {r.headers.get('server')!r}"


@pytest.mark.normal
def test_pages_and_login_still_work(stack):
    assert httpx.get("http://127.0.0.1:8088/", timeout=5).status_code == 200
    assert httpx.get(URLS["spring"]["admin_web"] + "/", timeout=5).status_code == 200
    r = httpx.post(URLS["spring"]["portal"] + "/sso/login", data={"username": "alice", "password": "daon-pass-1!"}, timeout=5)
    assert r.json().get("code") == 200


def test_denied_origin_event_logged(stack):
    since = _logs.now()
    preflight("portal", BAD)
    e = _logs.wait_event("mall-portal", since, "cors.denied_origin", "6-6", stack)
    assert e, "기대: SECURITY cors.denied_origin(6-6)이 mall-portal 로그에 남음"
    assert e["target"] == "origin:" + BAD, f"기대: target은 origin:<값>. 실제 {e['target']}"
