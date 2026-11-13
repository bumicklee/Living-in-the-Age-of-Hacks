"""F46 · 장애가 401(로그인 문제)로 둔갑한다(오류 경로, A10). 수업 9-2. 점수판에 세지 않는 「방어 확인」(make replay T=46).

alice가 가격 없이 상품 202를 장바구니에 담고(원본은 담을 때 요청의 가격을 쓴다, F45) 그 줄로 주문을 만든다.
원본 handleRealAmount()가 null 가격에서 NullPointerException → 처리되지 않은 예외가 /error로 → /error가 인증 대상이라 401.
  레드: HTTP 401(로그인이 필요하다는 답). 앱 로그에는 NPE 스택이 있다
  그린: HTTP 500 + 본문 trace_id + 헤더 X-Trace-Id(가격 없는 주문은 여전히 실패한다: 근본 수정은 10-8)
주문은 만들어지지 않는다(예외로 끝난다). 담은 장바구니 줄은 지운다.
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._http import new_trace_id, portal, portal_login

T = "F46"
TITLE = "가격 없는 장바구니로 주문하면 장애가 401로 나감(방어 확인)"
LESSON = "9-2"
SOURCE = "[원본 확인됨] OmsPortalOrderServiceImpl.handleRealAmount NPE + /error 인증 대상(점검표 F46)"
STACKS = ("spring",)
PRODUCT_ID = 202


def order_without_price(user: str = "alice") -> tuple[int, dict, str]:
    """(HTTP 상태, 본문, 응답 X-Trace-Id). 담은 장바구니 줄은 지운다"""
    tid = new_trace_id("f46")
    with portal() as c:
        token = portal_login(c, user)
        h = {"Authorization": token}
        d = c.get(f"/product/detail/{PRODUCT_ID}", headers=h).json().get("data") or {}
        p, skus = d.get("product") or {}, d.get("skuStockList") or []
        if not p or not skus:
            raise NotReady(f"상품 {PRODUCT_ID}이 없습니다. 시드를 확인하세요(make wipe).")
        old = [i["id"] for i in (c.get("/cart/list", headers=h).json().get("data") or []) if i.get("productId") == PRODUCT_ID]
        if old:                                   # 같은 상품 줄이 있으면 수량만 늘어나므로 먼저 지운다(랩 데이터)
            c.post("/cart/delete?ids=" + ",".join(map(str, old)), headers=h)
        before = {i["id"] for i in (c.get("/cart/list", headers=h).json().get("data") or [])}
        c.post("/cart/add", headers=h, json={
            "productId": PRODUCT_ID, "productSkuId": skus[0]["id"], "quantity": 1,
            "productName": p.get("name"), "productSn": p.get("productSn"),
            "productCategoryId": p.get("productCategoryId"), "productSkuCode": skus[0].get("skuCode")})
        mine = [i for i in (c.get("/cart/list", headers=h).json().get("data") or []) if i["id"] not in before]
        if not mine:
            raise NotReady("장바구니에 담기지 않았습니다(make wipe 뒤 다시).")
        cid = mine[-1]["id"]
        try:
            r = c.post("/order/generateOrder", headers=h | {"X-Trace-Id": tid},
                       json={"cartIds": [cid], "memberReceiveAddressId": 101, "payType": 0})
        finally:
            c.post(f"/cart/delete?ids={cid}", headers=h)
    try:
        body = r.json()
    except ValueError:
        body = {}
    return r.status_code, body, r.headers.get("X-Trace-Id", "")


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 Spring만(EverShop은 장바구니 가격을 서버 값으로 쓴다).")
    status, body, tid = order_without_price()
    ev = ["요청: alice가 가격 없이 상품 202를 담고 POST /order/generateOrder",
          f"응답: HTTP {status} · 본문 code={body.get('code')} · message={body.get('message')} · trace_id={body.get('trace_id') or body.get('traceId')} · X-Trace-Id={tid}",
          f"원인 찾기: make logs S=mall-portal | grep {tid or '<X-Trace-Id>'}  (NullPointerException 스택)",
          "담은 장바구니 줄은 지웠습니다. 가격 없는 주문 자체는 10-8에서 막습니다"]
    if status == 401:
        return Result(True, "서버 내부 오류(NPE)가 HTTP 401 「로그인이 필요합니다」로 나갔다", ev)
    if status == 500 and (body.get("trace_id") or body.get("traceId")):
        return Result(False, "HTTP 500 + trace_id로 나간다(장애가 장애로 보인다)", ev)
    if status == 200 and body.get("code") == 200:
        _close_created(body)
        return Result(False, "10-8 뒤: 가격을 서버가 정해 주문이 정상으로 만들어졌다(오류 자체가 없어짐, 만든 주문은 닫음)", ev)
    return Result(True, f"HTTP {status}: 500 + trace_id가 아니다", ev)


def _close_created(body: dict) -> None:
    """10-8 뒤에는 이 재연이 진짜 주문을 만든다. 결제 대기 주문을 닫아 랩 데이터를 지킨다"""
    oid = ((body.get("data") or {}).get("order") or {}).get("id")
    if oid:
        try:
            from replay import _money
            _money.close_orders([oid])
        except NotReady:
            pass
