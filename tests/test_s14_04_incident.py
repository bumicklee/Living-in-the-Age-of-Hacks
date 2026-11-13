"""14-4 첫 한 시간 런북: 격리·증거·연락 · A09 · 8장 결과물(감사 로그·해시 체인) + 2~13장의 공격 징후 로그.

시작 지점(14-3 완료): 사고 대응 격리 API가 없다. 한 회원의 토큰을 모두 끊는 방법이 없다(로그아웃한 토큰 하나씩만, 4-8) → 레드.
완료 지점:
  ① 격리(POST /daon/incident/lock-actor)는 권한 상승(13-6, 묶음 incident-response) 없이는 403 + ops.denied.no_elevation
  ② 잠근 계정의 토큰은 모두 401, 새 로그인은 같은 문구로 거부(auth.denied.disabled_account, 14-4), incident.actor.locked(INFO)
     잠금을 풀어도 그 전 토큰은 살아나지 않는다(계정 단위 폐기)
정상 시험(랩 도구라 시작 지점에서도 그린이어야 한다):
  - 다른 고객(alice)의 정상 요청은 그대로
  - make drill D=14-4 → python -m daon timeline이 정답 표(이벤트·actor별 건수, 같은 ip의 계정 셋, 시도당한 주문 12건·열린 주문 0건)와 같다
  - make evidence가 로그·DB 스냅샷·이미지 다이제스트 + sha256 목록을 잠금 버킷(A11)에 올린다 · 8-6 해시 체인 확인 통과
"""
import hashlib
import time

import pytest

from daon import incident, observe
from daon import timeline as tl
from replay import _approval as ap
from replay import _incident as inc
from replay import _logs
from replay._http import admin, new_trace_id, portal
from replay._money import mysql
from replay.drills import d14_04
from replay.t09_login_guard import clear_counters

VICTIM = 133                       # 격리 시험에 쓰는 가짜 고객(daon133). 모의 침해(117~119)와 겹치지 않게
PW = "daon-pass-1!"


def _reset_victim():
    mysql(f"UPDATE ums_member SET status=1 WHERE id={VICTIM}")
    from daon import stack as st
    st.redis_cli("DEL", f"mall:ums:member:daon{VICTIM}", f"deny:sub:daon-portal:daon{VICTIM}")
    clear_counters()


@pytest.fixture(autouse=True)
def clean():
    _reset_victim()
    inc.reset_mode()
    yield
    ap.reset_clock()
    _reset_victim()
    inc.reset_mode()


def _login(user: str) -> tuple[bool, str]:
    with portal() as c:
        r = c.post("/sso/login", data={"username": user, "password": PW})
    body = r.json()
    if body.get("code") == 200:
        return True, body["data"]["tokenHead"] + body["data"]["token"]
    return False, str(body.get("message"))


def _info(token: str) -> int:
    with portal() as c:
        return c.get("/sso/info", headers={"Authorization": token}).status_code


def _lock(path: str, member: int = VICTIM) -> "object":
    with admin() as c:
        return c.post(f"/daon/incident/{path}", headers={"Authorization": ap.must_login("admin"), "X-Trace-Id": new_trace_id("s14-4")},
                      json={"memberId": member, "incidentId": "INC-14-4", "reason": "모의 침해 대응(가짜)"})


def test_lock_actor_needs_elevation():
    since = _logs.now()
    r = _lock("lock-actor")
    assert r.status_code == 403, f"기대: 권한 상승 없이 격리 403. 실제 HTTP {r.status_code}"
    e = _logs.wait_event("mall-admin", since, "ops.denied.no_elevation", "13-6", timeout=8, target="path:/daon/incident/lock-actor")
    assert e, "기대: ops.denied.no_elevation(target path:/daon/incident/lock-actor). 격리는 고위험 기능(묶음 incident-response)"


def test_lock_actor_revokes_all_tokens_and_blocks_login():
    ok, token = _login(f"daon{VICTIM}")
    assert ok and _info(token) == 200, "준비: 가짜 고객 daon133 로그인·정보 보기"
    inc.elevate("admin")
    since = _logs.now()
    r = _lock("lock-actor")
    assert r.status_code == 200, f"기대: 권한 상승 뒤 격리 200. 실제 HTTP {r.status_code} {r.text[:120]}"
    e = _logs.wait_event("mall-admin", since, "incident.actor.locked", "14-4", timeout=8)
    assert e and e.get("actor") == "admin:admin" and e.get("target") == f"member:{VICTIM}", f"기대: incident.actor.locked(target member:{VICTIM}). 실제 {e}"
    assert _info(token) == 401, "기대: 잠근 계정의 기존 토큰은 401"
    ok2, msg = _login(f"daon{VICTIM}")
    assert not ok2, f"기대: 잠근 계정의 새 로그인 거부. 실제 성공"
    d = _logs.wait_event("mall-portal", since, "auth.denied.disabled_account", "14-4", timeout=8)
    assert d and d.get("target") == f"member:{VICTIM}", f"기대: auth.denied.disabled_account(14-4, member:{VICTIM}). 실제 {d}"
    # 잠금을 풀어도 그 전 토큰은 살아나지 않는다(계정 단위 폐기)
    assert _lock("unlock-actor").status_code == 200, "기대: 격리 풀기 200"
    clear_counters()
    assert _info(token) == 401, "기대: 잠금을 풀어도 격리 전에 받은 토큰은 계속 401"
    time.sleep(1.1)                                   # 끊은 시각(초)과 같은 초에 새로 로그인하지 않게
    ok3, fresh = _login(f"daon{VICTIM}")
    assert ok3 and _info(fresh) == 200, "기대: 잠금을 풀면 새로 로그인한 토큰은 통한다"


@pytest.mark.normal
def test_other_customer_is_not_affected():
    from replay._http import portal_login
    with portal() as c:
        r = c.get("/order/detail/9001", headers={"Authorization": portal_login(c, "alice")})
    assert r.status_code == 200 and r.json().get("code") == 200, f"기대: alice는 자기 주문을 그대로 본다. 실제 HTTP {r.status_code}"


@pytest.mark.normal
def test_drill_timeline_matches_answer_table():
    out = d14_04.run(echo=lambda *_: None)
    time.sleep(3)                                      # 로그가 컨테이너 출력에 다 나올 때까지
    rows = tl.security_rows(since=out["start"])
    want = d14_04.expected()
    picked, note = tl.build(rows, actor="member:117", trace_prefix=d14_04.PREFIX)
    got: dict = {}
    for r in picked:
        key = (r["event"], r["actor"])
        got[key] = got.get(key, 0) + 1
    missing = {k: v for k, v in want["rows"].items() if got.get(k) != v}
    assert not missing, f"기대: 정답 표와 같은 건수. 다른 줄 {missing} · 실제 {got}"
    members = {a for a in note.get("2_actors", []) if a.startswith("member:")}
    assert members == want["actors_by_ip"], f"기대: 같은 ip로 넓히면 계정 셋(117·118·119). 실제 {members}"
    imp = tl.impact(picked, out["opened"])
    assert imp["조회를 시도당한 주문"] == want["orders_tried"] and out["opened"] == want["orders_opened"], f"기대: 시도 12건·열린 0건. 실제 {imp}"
    assert all("010-" not in str(r) for r in picked), "기대: 타임라인 줄에 전화번호 모양이 없음(ID만)"


@pytest.mark.normal
def test_evidence_is_hashed_and_locked():
    r = incident.collect("INC-S14-CHECK", echo=lambda *_: None)
    assert r["files"] >= 4 and r["uploaded"], f"기대: 증거 4개 이상을 잠금 버킷에 올림. 실제 {r}"
    base = incident.incident_dir("INC-S14-CHECK")
    for line in (base / "evidence.sha256").read_text("utf-8").splitlines()[1:]:
        digest, name = line.split("  ", 1)
        assert hashlib.sha256((base / "evidence" / name).read_bytes()).hexdigest() == digest, f"기대: 목록의 해시와 파일이 같음({name})"
    names = incident.uploaded_list("INC-S14-CHECK")
    assert any(n.endswith("evidence.sha256") for n in names), f"기대: 잠금 버킷에 sha256 목록이 있음. 실제 {names[:5]}"


@pytest.mark.normal
def test_audit_chain_still_verifies():
    rep = observe.verify("spring", emit=False)
    assert rep.status == "ok", f"기대: 8-6 감사 로그 해시 체인 확인 통과(이 타임라인을 보고서에 쓸 수 있다). 실제 {rep.status} {rep.summary}"
