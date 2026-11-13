"""10-8 1원에 산 보조배터리: 가격은 서버가 정한다 · T37(점검표 F45, + F42·F46·F09 확장).

시작 지점: price:1로 담은 SKU 2002가 1원 주문이 된다, 가격 없이 담으면 주문이 500(9-2 뒤), 남의 장바구니 줄을 규격 바꾸기로 지울 수 있다 → 레드.
완료 지점: ① price:1 주문의 total_amount = 24,000 ② price.tampered(WARN, sku:2002) ③ 가격 없이 담아도 정가로 주문
          ④ T37 재연 그린 ⑤ /cart/update/attr가 남의 줄을 지우지 않음.
정상 시험: 맞는 가격을 보내는 옛 화면 주문은 그대로(정가), 수량 0은 400(9-3), 단품 할인 상품(promotion_type 1)의 계산이 바뀌지 않음(회귀).
만든 주문은 끝에 닫는다. 단품 할인 시험은 상품 229를 잠깐 단품 할인으로 바꿨다가 되돌린다(랩 도구, 시드에 할인 상품이 없음).
"""
import pytest

from replay import _logs
from replay import _money as m
from replay import t37_price_tamper as t37
from replay._http import new_trace_id, portal

PRODUCT, SKU, LIST_PRICE = 202, 2002, 24000


def order_with(price, user: str = "bob", trace: str | None = None) -> tuple[dict | None, str]:
    with portal() as c:
        h = {"Authorization": m.login(c, user), "X-Trace-Id": trace or new_trace_id("s108")}
        cid = m.cart_line(c, h, PRODUCT, 1, price=price)
        r = m.place_order(c, h, user, cid)
    o = m.order_of(r)
    if o:
        m.close_orders([o["id"]])
    return o, r.text[:160]


def test_price_one_orders_at_list_price():
    o, raw = order_with(1)
    assert o, f"기대: 주문은 만들어짐(서버 가격으로). 실제 {raw}"
    assert float(o["totalAmount"]) == LIST_PRICE, f"기대: total_amount {LIST_PRICE}. 실제 {o['totalAmount']}"


def test_price_tamper_is_logged():
    since = _logs.now()
    order_with(1)
    e = _logs.wait_event("mall-portal", since, "price.tampered", "10-8", timeout=8)
    assert e and e.get("target") == f"sku:{SKU}" and e.get("actor") == "member:102", f"기대: price.tampered(sku:{SKU}, member:102). 실제 {e}"
    assert "1원" not in str(e) and '"1"' not in str(e), "기대: 보낸 가격 값은 넣지 않음"


def test_no_price_orders_at_list_price():
    o, raw = order_with(None)
    assert o and float(o["totalAmount"]) == LIST_PRICE, f"기대: 가격 없이 담아도 정가 주문(F46이 다시 나지 않음). 실제 {raw}"


def test_t37_replay_is_green():
    r = t37.run("spring")
    assert not r.vulnerable, r.show()


def test_update_attr_cannot_remove_others_line():
    with portal() as c:
        ha = {"Authorization": m.login(c, "alice")}
        alice_line = m.cart_line(c, ha, 203, 1)
        hb = {"Authorization": m.login(c, "bob"), "X-Trace-Id": new_trace_id("s108attr")}
        c.post("/cart/update/attr", headers=hb, json={"id": alice_line, "productId": 203, "productSkuId": 2003, "quantity": 1})
        still = [i["id"] for i in (c.get("/cart/list", headers=ha).json().get("data") or [])]
        mine_b = [i["id"] for i in (c.get("/cart/list", headers=hb).json().get("data") or []) if i.get("productId") == 203]
        if mine_b:
            c.post("/cart/delete?ids=" + ",".join(map(str, mine_b)), headers=hb)
        c.post(f"/cart/delete?ids={alice_line}", headers=ha)
    assert alice_line in still, "기대: bob의 규격 바꾸기 요청이 alice의 장바구니 줄을 지우지 않음(F09 확장)"


@pytest.mark.normal
def test_honest_price_orders_normally():
    since = _logs.now()
    trace = new_trace_id("s108honest")
    o, raw = order_with(LIST_PRICE, trace=trace)
    assert o and float(o["totalAmount"]) == LIST_PRICE, f"기대: 맞는 가격을 보낸 주문은 정가. 실제 {raw}"
    assert not _logs.wait_event("mall-portal", since, "price.tampered", "10-8", timeout=2, trace_id=trace), "기대: 맞는 가격에는 price.tampered 없음(오탐 없음)"


@pytest.mark.normal
def test_quantity_zero_is_400():
    with portal() as c:
        h = {"Authorization": m.login(c, "bob")}
        r = c.post("/cart/add", headers=h, json={"productId": PRODUCT, "productSkuId": SKU, "quantity": 0})
    assert r.status_code == 400, f"기대: 수량 0은 400(9-3). 실제 {r.status_code}"


@pytest.mark.normal
def test_single_item_promotion_unchanged():
    """단품 할인 갈래는 원래 SKU 가격을 다시 읽었다. 고친 뒤에도 금액이 같아야 한다(setPrice를 할인 계산 뒤에 두지 않았는지)"""
    m.mysql("UPDATE pms_product SET promotion_type=1 WHERE id=229")
    m.mysql("UPDATE pms_sku_stock SET promotion_price=10000 WHERE id=2029")
    try:
        with portal() as c:
            h = {"Authorization": m.login(c, "bob"), "X-Trace-Id": new_trace_id("s108promo")}
            cid = m.cart_line(c, h, 229, 1, price=14000)
            conf = c.post("/order/generateConfirmOrder", headers=h, json=[cid]).json()
            c.post(f"/cart/delete?ids={cid}", headers=h)
        calc = (conf.get("data") or {}).get("calcAmount") or {}
        assert float(calc.get("totalAmount", -1)) == 14000 and float(calc.get("promotionAmount", -1)) == 4000, \
            f"기대: 단품 할인 상품 합계 14,000·할인 4,000·결제 10,000. 실제 {calc}"
    finally:
        m.mysql("UPDATE pms_product SET promotion_type=0 WHERE id=229")
        m.mysql("UPDATE pms_sku_stock SET promotion_price=NULL WHERE id=2029")
