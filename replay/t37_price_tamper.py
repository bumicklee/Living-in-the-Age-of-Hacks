"""T37 · 장바구니에 보낸 가격으로 주문 금액이 정해짐(가격 신뢰, A06). 수업 10-8. 점검표 F45.

bob이 상품 202(다온 보조배터리 10000)를 장바구니에 담으면서 가격(price)을 1로 보낸다.
주문 확인(/order/generateConfirmOrder) 금액이 SKU 정가가 아니라 1원으로 나오면 레드.
주문은 만들지 않는다(확인 단계까지만). 끝나면 담은 장바구니 줄을 지운다(랩 데이터 보존).
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._http import new_trace_id, portal, portal_login

T = "T37"
TITLE = "장바구니에 보낸 가격으로 주문 금액이 정해짐(1원 주문)"
LESSON = "10-8"
SOURCE = "[원본 확인됨] 장바구니 담기가 요청의 price를 저장하고, 할인 없는 상품은 주문 금액을 그 값으로 계산"
STACKS = ("spring",)

USER = "bob"
PRODUCT_ID = 202
FAKE_PRICE = 1


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(EverShop은 상품 표에서 가격을 다시 읽는다).")
    with portal() as c:
        token = portal_login(c, USER)
        h = {"Authorization": token, "X-Trace-Id": new_trace_id("t37")}
        d = c.get(f"/product/detail/{PRODUCT_ID}", headers=h).json().get("data") or {}
        p, skus = d.get("product") or {}, d.get("skuStockList") or []
        if not p or not skus:
            raise NotReady(f"상품 {PRODUCT_ID}이 없습니다. 시드를 확인하세요(make wipe).")
        sku = skus[0]
        list_price = float(sku["price"])
        old = [i["id"] for i in (c.get("/cart/list", headers=h).json().get("data") or [])
               if i.get("productId") == PRODUCT_ID]
        if old:
            c.post("/cart/delete?ids=" + ",".join(map(str, old)), headers=h)
        c.post("/cart/add", headers=h, json={
            "productId": PRODUCT_ID, "productSkuId": sku["id"], "quantity": 1, "price": FAKE_PRICE,
            "productName": p.get("name"), "productSn": p.get("productSn"),
            "productCategoryId": p.get("productCategoryId"), "productSkuCode": sku.get("skuCode")})
        cart = c.get("/cart/list", headers=h).json().get("data") or []
        mine = [i for i in cart if i.get("productId") == PRODUCT_ID]
        if not mine:
            raise NotReady("장바구니에 담기지 않았습니다.")
        cid = mine[-1]["id"]
        try:
            conf = c.post("/order/generateConfirmOrder", headers=h, json=[cid]).json()
        finally:
            c.post(f"/cart/delete?ids={cid}", headers=h)
        amount = ((conf.get("data") or {}).get("calcAmount") or {}).get("totalAmount")
        ev = [f"요청: {USER} 토큰으로 POST /cart/add  상품 {PRODUCT_ID}({p.get('name')}), 정가 {list_price:,.0f}원, 보낸 price={FAKE_PRICE}",
              f"주문 확인: POST /order/generateConfirmOrder → code={conf.get('code')} 주문 금액 {amount}",
              "주문은 만들지 않았고, 담은 장바구니 줄은 지웠습니다"]
        if amount is not None and float(amount) < list_price:
            return Result(True, f"정가 {list_price:,.0f}원 상품의 주문 금액이 {float(amount):,.0f}원으로 계산된다", ev)
    return Result(False, f"주문 금액은 정가({list_price:,.0f}원)로 계산된다", ev)
