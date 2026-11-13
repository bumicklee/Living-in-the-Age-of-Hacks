"""13-6 운영 접근: 필요할 때만 권한·접근 기록 · T35 · F19 · F11 · A10(권한 상승 승인에 재사용).

시작 지점: 공용 계정 daon_ops·원본 데모 관리자 macro로 로그인되고, 슈퍼 관리자는 고위험 기능(관리자 역할 변경)을 언제나 바로 쓴다 → 레드.
완료 지점:
  ① 닫힌 계정(daon_ops·macro)은 틀린 비밀번호와 같은 문구로 거부 + auth.denied.disabled_account(WARN)
  ② 권한 상승 없이 고위험 기능 → 403 + ops.denied.no_elevation
  ③ 사유 + 다른 사람(staff_lead, 운영 책임) 승인 → 켜면 통과(ops.elevation.granted INFO), 랩 시계 31분 뒤 403
  ④ 권한 상승을 요청한 사람이 스스로 승인 → 403 + approval.denied.self_approval
정상 시험: 일상 업무(주문 목록·관리자 목록 보기)는 권한 상승 없이 그대로 · 본인 비밀번호 변경은 현재 비밀번호를 요구(F11, 4-7에서 고침).
"""
import pytest

from replay import _approval as ap
from replay import _logs
from replay._http import admin

SAME = "아이디 또는 비밀번호가 올바르지 않습니다"


@pytest.fixture(autouse=True)
def clean():
    ap.reset()
    yield
    ap.reset_clock()
    ap.reset()


def test_shared_account_is_closed_and_logged():
    since = _logs.now()
    ok, msg = ap.login_message("daon_ops")
    assert not ok, "기대: 공용 계정 daon_ops 로그인 거부"
    assert msg == SAME, f"기대: 틀린 비밀번호와 같은 문구 「{SAME}」. 실제 「{msg}」"
    e = _logs.wait_event("mall-admin", since, "auth.denied.disabled_account", "13-6", timeout=8)
    assert e and e.get("actor") == "admin:daon_ops", f"기대: auth.denied.disabled_account(actor admin:daon_ops). 실제 {e}"


def test_demo_admin_is_closed():
    ok, msg = ap.login_message("macro")
    assert not ok and msg == SAME, f"기대: 원본 데모 관리자 macro 거부(같은 문구). 실제 {ok} 「{msg}」"


def test_high_risk_without_elevation_is_403():
    since = _logs.now()
    r = ap.role_update_as("admin")
    assert r.status_code == 403, f"기대: 권한 상승 없이 관리자 역할 변경 403. 실제 HTTP {r.status_code}"
    e = _logs.wait_event("mall-admin", since, "ops.denied.no_elevation", "13-6", timeout=8)
    assert e and e.get("target") == "path:/admin/role/update", f"기대: ops.denied.no_elevation(target path:/admin/role/update). 실제 {e}"


def test_elevation_approved_by_other_person_then_expires():
    ap.require_a10()
    req = ap.elevation_request("admin", minutes=30)
    assert req.get("http") == 200 and req.get("id"), f"기대: 권한 상승 요청 접수. 실제 {req}"
    ok = ap.ap("POST", f"/elevations/{req['id']}/approve", "staff_lead")
    assert ok.status_code == 200, f"기대: 운영 책임(다른 사람) 승인 200. 실제 HTTP {ok.status_code}"
    since = _logs.now()
    assert ap.elevation_activate("admin", req["id"]) == 200, "기대: 승인된 권한 상승 켜기 200"
    e = _logs.wait_event("mall-admin", since, "ops.elevation.granted", "13-6", timeout=8)
    assert e and "OPS-13" not in str(e), f"기대: ops.elevation.granted(사유 원문은 넣지 않음). 실제 {e}"
    assert ap.role_update_as("admin").status_code == 200, "기대: 권한 상승 30분 안에는 고위험 기능 통과"
    ap.skip_minutes(31)
    late = ap.role_update_as("admin")
    assert late.status_code == 403, f"기대: 31분 뒤(만료) 다시 403. 실제 HTTP {late.status_code}"


def test_requester_cannot_approve_own_elevation():
    ap.require_a10()
    req = ap.elevation_request("admin", minutes=10)
    assert req.get("id"), f"기대: 권한 상승 요청 접수. 실제 {req}"
    since = _logs.now()
    r = ap.ap("POST", f"/elevations/{req['id']}/approve", "admin")
    assert r.status_code == 403, f"기대: 스스로 승인 403. 실제 HTTP {r.status_code}"
    e = _logs.wait_event("approval", since, "approval.denied.self_approval", "13-6", timeout=8)
    assert e and e.get("actor") == "admin:admin", f"기대: approval.denied.self_approval(13-6). 실제 {e}"


@pytest.mark.normal
def test_daily_work_needs_no_elevation():
    with admin() as c:
        r1 = c.get("/order/list", params={"pageNum": 1, "pageSize": 5}, headers={"Authorization": ap.must_login("staff_order")})
        r2 = c.get("/admin/list", params={"pageNum": 1, "pageSize": 5}, headers={"Authorization": ap.must_login("admin")})
    assert r1.status_code == 200 and r1.json().get("code") == 200, f"기대: staff_order 주문 목록 200. 실제 HTTP {r1.status_code}"
    assert r2.status_code == 200 and r2.json().get("code") == 200, f"기대: 관리자 목록 보기(GET)는 권한 상승 없이 200. 실제 HTTP {r2.status_code}"


@pytest.mark.normal
def test_password_change_needs_current_password():
    with admin() as c:
        r = c.post("/admin/updatePassword", headers={"Authorization": ap.must_login("admin")},
                   json={"username": "admin", "oldPassword": "not-the-password-1!", "newPassword": "lab-new-pass-13!"})
    assert r.json().get("code") != 200, "기대: 현재 비밀번호가 틀리면 비밀번호를 바꾸지 않음(F11, 4-7)"
