"""9-3 입력 상한: 본문 크기(청크 포함)·페이지 크기(F41)·수량(F42)·쿠폰 코드 길이 + 선형 시간 정규식(T23).

시작 지점: pageSize=100000이 그대로 LIMIT에 들어감(고객·관리자), 수량 0 담기 성공, 1.5MB 본문을 받음,
          9-1의 느린 정규식 입력이 0.5초를 넘김 → 레드.
완료 지점: 400·400·400·413(Content-Length·청크 둘 다)·400(21자) + 같은 입력이 0.5초 안 판정, 이벤트 넷.
정상 시험: 상품 검색 pageSize=20, 쿠폰 코드 확인, 수량 1 담기.
"""
import pytest

from replay import _logs
from replay._faults import measure_regex, regex_slow, regex_table
from replay._http import admin, admin_login, new_trace_id, portal, portal_login

BIG = 1_500_000          # 1MB 상한보다 큰 가짜 본문(공백). 한 번만 보낸다
CART_PRODUCT = 203       # 시드 상품(다온 ○○ 8000원)


def _new_cart_lines(c, h, before):
    return [i["id"] for i in (c.get("/cart/list", headers=h).json().get("data") or []) if i["id"] not in before]


def test_portal_page_size_over_100_is_400():
    since = _logs.now()
    t = new_trace_id("s93page")
    with portal() as c:
        r = c.get("/product/search", params={"pageNum": 1, "pageSize": 100000}, headers={"X-Trace-Id": t})
    assert r.status_code == 400, f"기대: pageSize 100 초과는 HTTP 400(F41). 실제 {r.status_code}"
    e = _logs.wait_event("mall-portal", since, "input.denied.page_size", "9-3", timeout=8, trace_id=t)
    assert e and e.get("target") == "path:/product/search", f"기대: input.denied.page_size(target path:/product/search). 실제 {e}"


def test_admin_page_size_over_100_is_400():
    from replay.t09_login_guard import clear_counters
    with admin() as c:
        clear_counters()
        tok = admin_login(c, "staff_order")
        r = c.get("/order/list", params={"pageNum": 1, "pageSize": 100000}, headers={"Authorization": tok})
    assert r.status_code == 400, f"기대: 관리자 주문 목록도 pageSize 100 초과는 HTTP 400. 실제 {r.status_code} {r.text[:60]}"


def test_quantity_zero_is_400():
    with portal() as c:
        h = {"Authorization": portal_login(c, "alice")}
        before = {i["id"] for i in (c.get("/cart/list", headers=h).json().get("data") or [])}
        r = c.post("/cart/add", headers=h, json={"productId": CART_PRODUCT, "productSkuId": 2003, "quantity": 0, "price": 8000})
        new = _new_cart_lines(c, h, before)
        if new:
            c.post("/cart/delete?ids=" + ",".join(map(str, new)), headers=h)
    assert r.status_code == 400, f"기대: 수량 0 담기는 HTTP 400(F42). 실제 HTTP {r.status_code} code={r.json().get('code')}"


def test_body_over_1mb_is_413():
    since = _logs.now()
    t = new_trace_id("s93big")
    with portal() as c:
        h = {"Authorization": portal_login(c, "alice"), "Content-Type": "application/json", "X-Trace-Id": t}
        r = c.post("/order/generateConfirmOrder", headers=h, content=b"[" + b" " * BIG + b"]")
    assert r.status_code == 413, f"기대: 1MB를 넘는 JSON 본문은 HTTP 413. 실제 {r.status_code}"
    e = _logs.wait_event("mall-portal", since, "input.denied.too_large", "9-3", timeout=8, trace_id=t)
    assert e and "1~10MB" in str(e.get("reason")), f"기대: input.denied.too_large(바이트 수 대신 구간). 실제 {e}"


def test_chunked_body_over_1mb_is_413():
    def chunks():
        yield b"["
        for _ in range(BIG // 65536 + 1):
            yield b" " * 65536
        yield b"]"
    with portal() as c:
        h = {"Authorization": portal_login(c, "alice"), "Content-Type": "application/json"}
        r = c.post("/order/generateConfirmOrder", headers=h, content=chunks())
    assert r.status_code == 413, f"기대: Content-Length 없는 청크 전송도 읽은 바이트로 세어 HTTP 413. 실제 {r.status_code}"


def test_slow_regex_input_is_judged_fast():
    rows = measure_regex()
    assert not regex_slow(rows), "기대: 9-1의 입력이 모든 길이에서 0.5초 안에 판정\n" + "\n".join(regex_table(rows))


def test_long_coupon_code_is_400_and_logged():
    since = _logs.now()
    t = new_trace_id("s93long")
    with portal() as c:
        r = c.get("/daon/coupon/check", params={"code": "A" * 21}, headers={"Authorization": portal_login(c, "alice"), "X-Trace-Id": t})
    assert r.status_code == 400, f"기대: 20자를 넘는 쿠폰 코드는 정규식 전에 HTTP 400. 실제 {r.status_code}"
    e = _logs.wait_event("mall-portal", since, "input.denied.too_long", "9-3", timeout=8, trace_id=t)
    assert e and e.get("target") == "field:couponCode", f"기대: input.denied.too_long(field:couponCode). 실제 {e}"


@pytest.mark.normal
def test_normal_search_coupon_and_cart_work():
    with portal() as c:
        r = c.get("/product/search", params={"pageNum": 1, "pageSize": 20})
        assert r.status_code == 200 and r.json().get("code") == 200, f"기대: pageSize=20 검색 정상. 실제 {r.status_code}"
        h = {"Authorization": portal_login(c, "alice")}
        r = c.get("/daon/coupon/check", params={"code": "DAON-2026-SPRING"}, headers=h)
        assert r.status_code == 200 and r.json()["data"]["valid"] is True, f"기대: 정상 쿠폰 코드 확인. 실제 {r.text[:80]}"
        before = {i["id"] for i in (c.get("/cart/list", headers=h).json().get("data") or [])}
        r = c.post("/cart/add", headers=h, json={"productId": CART_PRODUCT, "productSkuId": 2003, "quantity": 1, "price": 8000})
        new = _new_cart_lines(c, h, before)
        if new:
            c.post("/cart/delete?ids=" + ",".join(map(str, new)), headers=h)
    assert r.status_code == 200 and r.json().get("code") == 200, f"기대: 수량 1 담기 정상. 실제 {r.status_code} {r.text[:80]}"
