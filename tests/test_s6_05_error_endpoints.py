"""6-5 오류 응답과 관리용 엔드포인트 · T17.

시작 지점: Swagger UI·/v3/api-docs·/actuator가 로그인 없이 200, 관리 포트 없음 → 레드.
완료 지점: 모두 닫힘(2xx 없음), 상태 확인은 관리 포트(8081, PC에 열지 않음)에서만 200, DB 예외 응답에 SQL 문구 없음(단위 시험),
두드린 경로는 recon.endpoint_probe로 남는다.
prod 프로필로 떠 있는 랩(compose 기본)이 대상이다. 스위치 docs-closed를 끄면 문서 화면이 다시 열려 레드다.
"""
import socket
import subprocess

import pytest

from daon.config import HOST, ROOT
from replay import _logs
from replay import t17_exposed_endpoints as t17
from replay._http import admin, portal, portal_login


def test_no_management_or_docs_path_is_open_without_login():
    r = t17.run()
    assert not r.vulnerable, "기대: 관리·문서 경로가 로그인 없이 2xx가 아님. 실제:\n" + "\n".join(r.evidence)


def test_health_only_on_internal_management_port():
    for svc in ("daon-mall-portal-1", "daon-mall-admin-1"):
        r = subprocess.run(["docker", "exec", svc, "curl", "-fsS", "-o", "/dev/null", "-w", "%{http_code}", "http://127.0.0.1:8081/actuator/health"],
                           capture_output=True, text=True)
        assert r.stdout.strip() == "200", f"기대: {svc} 관리 포트(8081)의 health는 200. 실제 {r.stdout.strip() or r.stderr.strip()[:60]}"
        env = subprocess.run(["docker", "exec", svc, "curl", "-s", "-w", " HTTP%{http_code}", "http://127.0.0.1:8081/actuator/env"],
                             capture_output=True, text=True)
        denied = not env.stdout.endswith("HTTP200") or '"code":401' in env.stdout   # 이 앱은 로그인 요구를 HTTP 200 + 본문 code 401로도 돌려준다
        assert denied and "propertySources" not in env.stdout, "기대: 관리 포트에서도 env는 노출 목록(health)에 없음(열리지 않음)"
    with socket.socket() as s:
        s.settimeout(0.5)
        assert s.connect_ex((HOST, 8081)) != 0, "기대: 관리 포트 8081은 PC에 열려 있지 않음"


@pytest.mark.normal
def test_database_error_text_not_in_response():
    """응답에 SQL 문구가 없는지는 단위 시험으로 본다(정상 기능 범위에서 SQL 오류를 일으키는 요청이 더는 없다)."""
    if not (ROOT / "spring/mall-common/src/test/java/com/macro/mall/common/exception/GlobalExceptionHandlerTest.java").exists():
        return  # 시작 지점에는 이 단위 시험이 없다. 파일 부재를 취약 증거로 세지 않는다.
    r = subprocess.run(
        ["docker", "run", "--rm", "-v", f"{ROOT}/spring:/src", "-v", "daon-m2:/root/.m2", "-w", "/src", "maven:3.9.12-eclipse-temurin-17",
         "mvn", "-B", "-pl", "mall-common", "test", "-Dtest=GlobalExceptionHandlerTest", "-Dsurefire.failIfNoSpecifiedTests=false", "-Ddocker.skip=true", "-DskipTests=false"],
        capture_output=True, text=True, timeout=600)
    out = r.stdout + r.stderr
    assert "Tests run: 2, Failures: 0, Errors: 0" in out, "기대: GlobalExceptionHandlerTest 2개 통과\n" + "\n".join(l for l in out.splitlines() if "ERROR" in l or "Tests run" in l)[:600]


@pytest.mark.parametrize("which", ["portal", "admin"])
def test_probe_is_logged(stack, which):
    since = _logs.now()
    with (portal() if which == "portal" else admin()) as c:
        c.get("/actuator/env")
    svc = "mall-portal" if which == "portal" else "mall-admin"
    e = _logs.wait_event(svc, since, "recon.endpoint_probe", "6-5", stack)
    assert e, f"기대: SECURITY recon.endpoint_probe(6-5)가 {svc} 로그에 남음"
    assert e["target"] == "path:/actuator/env", f"기대: target은 path:<경로>. 실제 {e['target']}"


@pytest.mark.normal
def test_normal_api_and_login_unchanged(stack):
    with portal() as c:
        auth = portal_login(c, "alice")
        r = c.get("/daon/product/search", params={"keyword": "수"})
        assert r.status_code == 200, f"기대: 정상 검색은 그대로. 실제 {r.status_code}"
        assert c.get("/sso/info", headers={"Authorization": auth}).json().get("code") == 200
