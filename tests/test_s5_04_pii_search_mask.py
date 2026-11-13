"""5-4 개인정보 칸: 암호화한 채 찾고(검색용 HMAC), 서버에서 가리고, 원문은 사유와 기록으로 · T14.

시작 지점(5-3 완료 상태): ①전화번호 검색 0건 ②목록 응답에 원문 ③사유 없이 원문 보기. 완료 지점: 모두 그린.
스위치 pii-mask를 끄면 5-3 완료 상태로 돌아간다(관리자 앱을 다시 띄운 뒤).
"""

from replay import _logs
from replay._http import ADMINS, admin
from replay.t09_login_guard import clear_counters

KNOWN_PHONE = "010-0000-0101"          # 시드 회원 alice(가짜)
KNOWN_PLAIN = "01000000101"


def headers(c) -> dict:
    clear_counters()
    t = c.post("/admin/login", json={"username": "admin", "password": ADMINS["admin"]["password"]}).json()["data"]["token"]
    return {"Authorization": "Bearer " + t}


# normal 아님: 시작 지점(5-3 완료)에서는 암호화한 칸이라 0건 → 이 수업이 고치는 기능(①)
def test_search_by_phone_finds_one_member(stack):
    with admin() as c:
        h = headers(c)
        for q in (KNOWN_PHONE, KNOWN_PLAIN):                 # 하이픈이 있어도 없어도 같은 한 명
            rows = c.get("/daon/customer/search", params={"phone": q}, headers=h).json()["data"]
            assert len(rows) == 1, f"기대: {q!r}로 한 명이 찾아져야 함. 실제 {len(rows)}명"


def test_list_response_masks_phone(stack):
    with admin() as c:
        h = headers(c)
        rows = c.get("/daon/customer/list", headers=h).json()["data"]
        assert rows and all("****" in (r["phone"] or "****") for r in rows), "기대: 회원 목록 전화번호는 가운데가 가려짐"
        o = c.get("/order/9003", headers=h).json()["data"]
        assert "****" in o["receiverPhone"], f"기대: 주문 상세 수령인 전화번호도 가려짐. 실제 {o['receiverPhone']!r}"


def test_reveal_needs_reason(stack):
    since = _logs.now()
    with admin() as c:
        h = headers(c)
        r = c.get("/daon/customer/1/phone", headers=h)
    assert r.status_code == 400, f"기대: 사유 없이 원문 보기는 400. 실제 {r.status_code}"
    assert _logs.wait_event("mall-admin", since, "pii.reveal.denied", "5-4", stack), "기대: SECURITY pii.reveal.denied(5-4)"


# normal 아님: 원문 보기 주소(/daon/customer/{id}/phone)는 이 수업에서 새로 만든다(시작 지점 404)
def test_reveal_with_reason_returns_one_and_logs_without_number(stack):
    since = _logs.now()
    with admin() as c:
        h = headers(c)
        r = c.get("/daon/customer/1/phone", params={"reason": "고객 문의 확인"}, headers=h)
    assert r.status_code == 200 and r.json()["data"]["phone"], f"기대: 사유가 있으면 원문 한 건. 실제 {r.status_code}"
    e = _logs.wait_event("mall-admin", since, "pii.reveal", "5-4", stack)
    assert e, "기대: SECURITY pii.reveal(5-4)"
    assert r.json()["data"]["phone"] not in str(e), "기대: 기록에는 번호가 없어야 함"


def test_reveal_daily_cap(stack):
    from daon import stack as lab
    keys = lab.redis_cli("--scan", "--pattern", "rl:reveal:*").stdout.split()
    if keys:
        lab.redis_cli("DEL", *keys)
    since = _logs.now()
    codes = []
    with admin() as c:
        h = headers(c)
        for _ in range(52):
            codes.append(c.get("/daon/customer/1/phone", params={"reason": "상한 시험"}, headers=h).status_code)
    keys = lab.redis_cli("--scan", "--pattern", "rl:reveal:*").stdout.split()
    if keys:
        lab.redis_cli("DEL", *keys)
    assert codes[49] == 200 and codes[50] == 429, f"기대: 50건까지 200, 51번째 429. 실제 {codes[48:52]}"
    assert _logs.wait_event("mall-admin", since, "pii.reveal.burst", "5-4", stack), "기대: SECURITY pii.reveal.burst(5-4)"
