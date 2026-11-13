"""9-2 실패하면 막기(fail-closed, T22) · 오류 경로가 장애를 401로 바꾸지 않기(F46).

시작 지점: ① 문자 스텁이 500이면 틀린 번호로도 본인 확인 통과 ② 스텁이 3초씩 늦으면 시간 제한 없이 기다렸다가 「번호 틀림」(장애가 안 보임)
          ③ 가격 없는 장바구니 주문이 HTTP 401 ④ 일부러 낸 예외(/daon/lab/boom)도 HTTP 401 → 레드.
완료 지점: ① 503 + auth.fail_closed.dependency_error ② 2초에 끊고 503 ③ HTTP 500 + trace_id(본문·헤더 같음)
          ④ 500 + trace_id, 응답에 예외 이름·문구 없음, OPS 로거에 error.unhandled 한 줄.
정상 시험: 스텁이 정상이면 맞는 번호는 통과, 틀린 번호는 400. 로그인 없는 요청은 그대로 401.
"""
import json

import httpx
import pytest

from daon import stack as st
from replay import _logs
from replay import f46_error_path as f46
from replay import t22_fail_open as t22
from replay._faults import set_fault, stub_url
from replay._http import new_trace_id, portal, portal_login

ALICE_PHONE = "010-0000-0101"


def ops_events(since: str, service: str = "mall-portal") -> list[dict]:
    """앱 로그의 OPS 줄(운영 이벤트, SECURITY와 같은 키)"""
    r = st.compose("spring", "logs", "--no-log-prefix", "--since", since, service, check=False, capture=True)
    out = []
    for line in (r.stdout or "").splitlines():
        if " OPS " in line and "{" in line:
            try:
                out.append(json.loads(line[line.index("{"):]))
            except ValueError:
                continue
    return out


def test_dependency_500_is_denied_with_503():
    status, body, _ = t22.attempt(fault="500")
    assert status == 503, f"기대: 문자 사업자가 500이면 확인 불가로 HTTP 503. 실제 HTTP {status} {body.get('message')}"
    assert body.get("data") != "verified", "기대: 본인 확인이 통과되지 않음"


def test_dependency_timeout_is_denied_with_503():
    status, body, _ = t22.attempt(fault="slow", ms=3000)
    assert status == 503, f"기대: 스텁이 3초 늦으면 2초 시간 제한에서 끊고 HTTP 503. 실제 HTTP {status} {body.get('message')}"


def test_fail_closed_event_logged():
    since = _logs.now()
    status, _, tid = t22.attempt(fault="500")
    e = _logs.wait_event("mall-portal", since, "auth.fail_closed.dependency_error", "9-2", timeout=8, trace_id=tid)
    assert e, f"기대: auth.fail_closed.dependency_error(9-2, trace_id {tid}). HTTP {status}"
    assert e.get("target") == "dependency:sms-stub" and str(e.get("actor", "")).startswith("member:"), f"기대: target dependency:sms-stub, actor member:<ID>. 실제 {e}"


@pytest.mark.normal
def test_correct_code_passes_when_stub_is_healthy():
    set_fault("sms", "ok")
    with portal() as c:
        h = {"Authorization": portal_login(c, "alice")}
        r = c.post("/daon/verify/sms/send", headers=h)
        assert r.status_code == 200, f"기대: 문자 보내기 200. 실제 {r.status_code}"
        msgs = httpx.get(stub_url("sms") + "/v1/messages", params={"to": ALICE_PHONE}, timeout=5).json()
        code = [m["text"] for m in msgs if "인증번호" in m["text"]][-1].split()[-1]
        r = c.post("/daon/verify/sms/check", params={"code": code}, headers=h)
    assert r.status_code == 200 and r.json().get("data") == "verified", f"기대: 맞는 번호는 통과. 실제 HTTP {r.status_code} {r.text[:80]}"


@pytest.mark.normal
def test_wrong_code_is_400_when_stub_is_healthy():
    status, body, _ = t22.attempt(fault="ok")
    assert status == 400, f"기대: 스텁이 정상이면 틀린 번호는 HTTP 400. 실제 {status} {body.get('message')}"


def test_order_npe_is_500_not_401():
    status, body, tid = f46.order_without_price()
    assert status != 401, "기대: 서버 내부 오류가 401(로그인 문제)로 나가지 않음(F46)"
    if status == 200 and body.get("code") == 200:
        f46._close_created(body)
        return                                   # 10-8 완료 뒤: 가격을 서버가 정해 주문이 정상으로 만들어진다(근본 수정, 결정 #10)
    assert status == 500, f"기대: HTTP 500. 실제 {status}"
    assert body.get("trace_id") and body.get("trace_id") == tid, f"기대: 본문 trace_id = 헤더 X-Trace-Id. 실제 본문 {body} 헤더 {tid}"


def test_unexpected_exception_is_500_with_trace_and_no_internals():
    t = new_trace_id("s92boom")
    with portal() as c:
        r = c.get("/daon/lab/boom", headers={"Authorization": portal_login(c, "alice"), "X-Trace-Id": t})
    assert r.status_code == 500, f"기대: 일부러 낸 예외는 HTTP 500(랩 경로 /daon/lab/boom). 실제 {r.status_code}"
    assert r.headers.get("X-Trace-Id") == t and t in r.text, "기대: 헤더와 본문에 같은 trace_id"
    for leak in ("IllegalStateException", "일부러", "java.", "at com."):
        assert leak not in r.text, f"기대: 응답에 예외 이름·문구·스택 없음(6-5). 들어 있음: {leak}"


def test_error_unhandled_is_logged():
    since = _logs.now()
    t = new_trace_id("s92ops")
    with portal() as c:
        c.get("/daon/lab/boom", headers={"Authorization": portal_login(c, "alice"), "X-Trace-Id": t})
    import time
    for _ in range(10):
        hit = [e for e in ops_events(since) if e.get("event") == "error.unhandled" and e.get("trace_id") == t]
        if hit:
            break
        time.sleep(0.5)
    assert hit, f"기대: OPS 로거에 error.unhandled 한 줄(trace_id {t})"
    assert hit[0].get("target") == "handler:DaonLabBoomController.boom" and hit[0].get("reason") == "IllegalStateException", f"실제 {hit[0]}"


@pytest.mark.normal
def test_missing_login_is_still_401():
    with portal() as c:
        r = c.get("/cart/list")
    assert r.status_code == 401, f"기대: 보안 필터가 막는 주소는 로그인 없으면 401(8-4). 실제 {r.status_code}"
