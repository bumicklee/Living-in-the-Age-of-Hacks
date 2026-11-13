"""T25 · 확인한 뒤 갱신(경쟁 조건): 1인 1장 쿠폰을 여러 장, 포인트 1,000점으로 주문 여러 개. 수업 10-1(보기)·10-2(막기). 점검표 F43·F38.

장면 1(쿠폰, F43): alice(회원 101)가 쿠폰 101(「다온 첫 주문 3천원 할인」, 1인 한도 1장)의 받기 요청을 **100개 한 번에** 보낸다.
  레드: 성공 응답 2개 이상, 또는 alice의 쿠폰 101 이력이 2장 이상, 또는 줄어든 수량 ≠ 저장된 행 수(잃어버린 갱신)
  그린: 성공 1·거절 99, 이력 1장, 줄어든 수량 1
장면 2(포인트, F38): alice 포인트를 1,000점으로 맞추고, 1,000점을 쓰는 주문 요청 10개를 한 번에 보낸다(같은 장바구니 줄).
  레드: 주문이 2개 이상 만들어짐   그린: 1개만, 남은 포인트 0
요청은 모두 정상 요청이다(한 번에 최대 100개, 반복 없음). 만든 결제 대기 주문은 끝에 닫는다(랩 데이터 보존). 쿠폰 이력은 SQL로 다시 세어 보도록 남긴다.
"""
from __future__ import annotations

from replay import Result
from replay import _money as m
from replay._http import new_trace_id, portal

T = "T25"
TITLE = "확인한 뒤 갱신: 1인 1장 쿠폰 여러 장·포인트 한 번에 여러 주문(경쟁 조건)"
LESSON = "10-2"
SOURCE = "[원본 확인됨] UmsMemberCouponServiceImpl.add 조회→비교→저장→count-1(F43), 주문 포인트 차감이 캐시된 회원 값으로 덮어쓰기(F38)"
STACKS = ("spring",)

USER = "alice"
COUPON = 101
COUPON_REQUESTS = 100        # 계획 10-1: 100개 한 번에(상한 m.MAX_CONCURRENT)
POINT_REQUESTS = 10
POINTS = 1000
PRODUCT = 202


def coupon_scene(user: str = USER) -> dict:
    mid = m.member_id(user)
    m.reset_coupon(mid, COUPON)
    before = m.coupon_state(mid, COUPON)
    with portal() as c:
        token = m.login(c, user)

    def receive(i):
        with portal() as c:
            r = c.post(f"/member/coupon/add/{COUPON}", headers={"Authorization": token, "X-Trace-Id": new_trace_id(f"t25c{i}")})
            try:
                return r.json().get("code")
            except ValueError:
                return r.status_code
    codes = m.concurrent(receive, COUPON_REQUESTS)
    after = m.coupon_state(mid, COUPON)
    return {"ok": sum(1 for x in codes if x == 200), "denied": sum(1 for x in codes if x != 200), "rows": after["rows"],
            "drop": before["count"] - after["count"], "recv_up": after["receive"] - before["receive"]}


def points_scene(user: str = USER) -> dict:
    m.set_points(user, POINTS)
    with portal() as c:
        token = m.login(c, user)
        h = {"Authorization": token}
        cid = m.cart_line(c, h, PRODUCT, 1)

    def order(i):
        with portal() as c:
            return m.place_order(c, {"Authorization": token, "X-Trace-Id": new_trace_id(f"t25p{i}")}, user, cid, POINTS)
    rs = m.concurrent(order, POINT_REQUESTS)
    orders = [o for o in (m.order_of(r) for r in rs if not isinstance(r, Exception)) if o]
    left = m.points(m.member_id(user))
    m.close_orders([o.get("id") for o in orders])
    return {"orders": len(orders), "left": left, "ids": [o.get("id") for o in orders]}


def run(stack: str = "spring") -> Result:
    from replay import NotReady
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s10-02, 쿠폰 사용 횟수 트리거).")
    cs = coupon_scene()
    ps = points_scene()
    ev = [f"장면 1 쿠폰: alice 토큰으로 POST /member/coupon/add/{COUPON} × {COUPON_REQUESTS}개 동시",
          "  성공 응답  저장된 행(alice·쿠폰 101)  줄어든 수량  늘어난 받은 수",
          f"  {cs['ok']:>8}  {cs['rows']:>22}  {cs['drop']:>10}  {cs['recv_up']:>12}",
          f"장면 2 포인트: alice 포인트 {POINTS} → {POINTS}점 쓰는 주문 {POINT_REQUESTS}개 동시 → 만들어진 주문 {ps['orders']}개, 남은 포인트 {ps['left']}",
          "만든 결제 대기 주문은 닫았습니다. SQL: SELECT COUNT(*) FROM sms_coupon_history WHERE coupon_id=101 AND member_id=101"]
    bad = []
    if cs["ok"] > 1 or cs["rows"] > 1:
        bad.append(f"1인 1장 쿠폰을 {max(cs['ok'], cs['rows'])}장 받음")
    if cs["drop"] != cs["rows"]:
        bad.append(f"줄어든 수량 {cs['drop']} ≠ 저장된 행 {cs['rows']}(잃어버린 갱신)")
    if ps["orders"] > 1:
        bad.append(f"포인트 {POINTS}점으로 주문 {ps['orders']}개")
    if bad:
        return Result(True, " · ".join(bad), ev)
    return Result(False, "쿠폰은 1장만, 포인트 주문은 1개만 성공했다(검사와 갱신이 한 문장)", ev)
