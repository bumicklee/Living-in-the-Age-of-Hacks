"""섹션 10 도우미: 돈이 오가는 기능(쿠폰·포인트·결제·문자 예산·가격)의 재연·시험이 같이 쓴다.

안전 기준(수업 상세 계획 10장): 127.0.0.1의 다온마켓과 랩 스텁만 부른다. 값은 시드의 가짜 값이다.
  - 동시 요청은 한 번에 최대 100개(MAX_CONCURRENT), 반복하지 않는다. 숫자는 이 파일에 박아 두고 인자로 늘릴 수 없다.
  - 카드는 랩의 가짜 테스트 토큰(tok_test_ + 숫자)만 쓴다. 실제 카드 번호 모양은 만들지 않는다.
  - 문자는 가짜 번호(010-0000-xxxx)와 형식 확인용 국외 모양 번호 하나만. 가짜 문자 스텁은 실제로 보내지 않는다.
  - DB를 직접 고치는 일(포인트 맞추기, 쿠폰 이력 지우기, 만든 주문 닫기)은 랩 도구로만 한다(컨테이너 안 mysql, 랩 전용 root).
"""
from __future__ import annotations

import hashlib
import hmac
import os
import subprocess
import threading
import time
from concurrent.futures import ThreadPoolExecutor

import httpx

from daon.config import ENV, HOST, URLS, port
from replay import NotReady
from replay._http import ACCOUNTS, new_trace_id, portal

MAX_CONCURRENT = 100                 # 동시 요청 상한(계획 10장 안전 기준). 이보다 크게 부를 수 없다
PASSWORD = "daon-pass-1!"            # 시드 고객 공통 가짜 비밀번호
PAY = f"http://{HOST}:{port('DAON_PAYMENT_PORT', 9101)}"
SMS = f"http://{HOST}:{port('DAON_SMS_PORT', 9102)}"
WEBHOOK_SECRET = ENV.get("DAON_PAYMENT_WEBHOOK_SECRET", "lab-fake-webhook-secret")   # 랩 전용 가짜 값(compose와 같은 기본값)
MEMBER_ID = {"alice": 101, "bob": 102}
ADDRESS_ID = {"alice": 101, "bob": 102}   # 배송지 id = 회원 id(seed/README.md)


def member_id(user: str) -> int:
    if user in MEMBER_ID:
        return MEMBER_ID[user]
    if user.startswith("daon") and user[4:].isdigit():
        return int(user[4:])
    raise ValueError(user)


# ── DB·캐시(랩 도구) ──
def mysql(sql: str) -> str:
    name = f"{os.environ.get('COMPOSE_PROJECT_NAME', 'daon')}-mysql-1"
    r = subprocess.run(["docker", "exec", name, "mysql", "-uroot", "-proot", "--default-character-set=utf8mb4", "mall", "-N", "-e", sql],
                       capture_output=True, text=True, timeout=60)
    if r.returncode:
        raise NotReady(f"MySQL에 닿지 않습니다(make up STACK=spring): {r.stderr.strip()[-120:]}")
    return r.stdout.strip()


def one(sql: str) -> str:
    out = mysql(sql)
    return out.splitlines()[0].strip() if out else ""


def table_exists(name: str) -> bool:
    return one(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mall' AND table_name='{name}'") == "1"


def drop_member_cache(user: str) -> None:
    from daon import stack
    stack.redis_cli("DEL", f"mall:ums:member:{user}")


def clear_counters() -> None:
    from replay.t09_login_guard import clear_counters as clear
    clear()


def ledger_sum(mid: int) -> int:
    v = one(f"SELECT IFNULL(SUM(CASE change_type WHEN 0 THEN change_count ELSE -change_count END),0) "
            f"FROM ums_integration_change_history WHERE member_id={int(mid)}")
    return int(float(v or 0))


def points(mid: int) -> int:
    return int(one(f"SELECT IFNULL(integration,0) FROM ums_member WHERE id={int(mid)}") or 0)


def set_points(user: str, n: int) -> None:
    """랩 도구: 회원 포인트를 n으로 맞추고, 원장 합계도 n이 되게 「랩 초기화」 줄을 하나 더한다(10-5 대사가 이 조정을 불일치로 보지 않게)."""
    mid = member_id(user)
    delta = int(n) - ledger_sum(mid)
    mysql(f"UPDATE ums_member SET integration={int(n)} WHERE id={mid}")
    if delta:
        kind = 0 if delta > 0 else 1
        mysql("INSERT INTO ums_integration_change_history (member_id, create_time, change_type, change_count, operate_man, operate_note, source_type) "
              f"VALUES ({mid}, NOW(), {kind}, {abs(delta)}, 'lab-reset', '랩 초기화(재연·시험 준비)', 1)")
    drop_member_cache(user)


def ledger_break(mid: int, extra: int = 500) -> None:
    """랩 도구(make ledger-break): 원장 없이 잔액만 바꾼다. 대사가 이 회원 하나를 집어내야 한다"""
    mysql(f"UPDATE ums_member SET integration = IFNULL(integration,0) + {int(extra)} WHERE id={int(mid)}")


def ledger_fix(mid: int) -> None:
    """랩 도구(make ledger-fix): 잔액을 원장 합계로 되돌린다(ledger-break 뒤 정리)"""
    mysql(f"UPDATE ums_member SET integration = {ledger_sum(mid)} WHERE id={int(mid)}")


def reset_coupon(mid: int, coupon_id: int) -> None:
    """랩 도구: 이 회원의 이 쿠폰 이력을 지우고 쿠폰 수량을 넉넉히(1000) 둔다"""
    mysql(f"DELETE FROM sms_coupon_history WHERE coupon_id={int(coupon_id)} AND member_id={int(mid)}")
    mysql(f"UPDATE sms_coupon SET count=1000 WHERE id={int(coupon_id)}")


def coupon_state(mid: int, coupon_id: int) -> dict:
    rows = int(one(f"SELECT COUNT(*) FROM sms_coupon_history WHERE coupon_id={int(coupon_id)} AND member_id={int(mid)}") or 0)
    c = one(f"SELECT count, IFNULL(receive_count,0) FROM sms_coupon WHERE id={int(coupon_id)}").split()
    return {"rows": rows, "count": int(c[0]), "receive": int(c[1])}


def order_status(order_id: int) -> int | None:
    v = one(f"SELECT status FROM oms_order WHERE id={int(order_id)}")
    return int(v) if v != "" else None


def sku_stock(sku_id: int) -> tuple[int, int]:
    s, l = one(f"SELECT stock, IFNULL(lock_stock,0) FROM pms_sku_stock WHERE id={int(sku_id)}").split()
    return int(s), int(l)


def close_orders(ids: list[int]) -> None:
    """랩 도구: 재연이 만든 결제 대기 주문을 닫고(4) 잠근 재고를 푼다. 지연 취소 메시지가 나중에 와도 status=0이 아니라 건너뛴다"""
    ids = [int(i) for i in ids if i]
    if not ids:
        return
    lst = ",".join(map(str, ids))
    mysql("UPDATE pms_sku_stock s JOIN oms_order_item i ON i.product_sku_id = s.id JOIN oms_order o ON o.id = i.order_id "
          f"SET s.lock_stock = GREATEST(0, IFNULL(s.lock_stock,0) - i.product_quantity) WHERE o.id IN ({lst}) AND o.status = 0")
    mysql(f"UPDATE oms_order SET status = 4 WHERE id IN ({lst}) AND status = 0")


def reset_purchase(mid: int, product_id: int) -> None:
    if table_exists("daon_purchase_counter"):
        mysql(f"DELETE FROM daon_purchase_counter WHERE member_id={int(mid)} AND product_id={int(product_id)}")


# ── 고객 API ──
def login(c: httpx.Client, user: str) -> str:
    pw = ACCOUNTS[user]["password"] if user in ACCOUNTS else PASSWORD
    try:
        r = c.post("/sso/login", data={"username": user, "password": pw})
    except httpx.TransportError as e:
        raise NotReady(f"mall-portal에 닿지 않습니다. make up STACK=spring 먼저({e.__class__.__name__})")
    body = r.json()
    if body.get("code") != 200:
        raise NotReady(f"{user} 로그인 실패: {body.get('message')}(make wipe 뒤 다시, 로그인 제한이면 잠시 뒤)")
    return body["data"]["tokenHead"] + body["data"]["token"]


def sku_of(c: httpx.Client, h: dict, product_id: int) -> dict:
    d = c.get(f"/product/detail/{product_id}", headers=h).json().get("data") or {}
    skus = d.get("skuStockList") or []
    if not skus:
        raise NotReady(f"상품 {product_id}이 없습니다. 시드를 확인하세요(make wipe).")
    return skus[0]


_SKU_PRICE = object()


def cart_line(c: httpx.Client, h: dict, product_id: int, qty: int = 1, price=_SKU_PRICE) -> int:
    """기본값은 실제 SKU 가격. price=None을 명시하면 10-8의 가격 없는 요청을 만든다."""
    old = [i["id"] for i in (c.get("/cart/list", headers=h).json().get("data") or []) if i.get("productId") == product_id]
    if old:
        c.post("/cart/delete?ids=" + ",".join(map(str, old)), headers=h)
    sku = sku_of(c, h, product_id)
    body = {"productId": product_id, "productSkuId": sku["id"], "quantity": qty}
    if price is not None:
        body["price"] = sku["price"] if price is _SKU_PRICE else price
    c.post("/cart/add", headers=h, json=body)
    mine = [i for i in (c.get("/cart/list", headers=h).json().get("data") or []) if i.get("productId") == product_id]
    if not mine:
        raise NotReady("장바구니에 담기지 않았습니다(make wipe 뒤 다시).")
    return mine[-1]["id"]


def place_order(c: httpx.Client, h: dict, user: str, cart_id: int, use_points: int | None = None) -> httpx.Response:
    body = {"cartIds": [cart_id], "memberReceiveAddressId": ADDRESS_ID.get(user, member_id(user)), "payType": 0}
    if use_points is not None:
        body["useIntegration"] = use_points
    return c.post("/order/generateOrder", headers=h, json=body)


def order_of(r: httpx.Response) -> dict | None:
    try:
        body = r.json()
    except ValueError:
        return None
    if body.get("code") != 200:
        return None
    return (body.get("data") or {}).get("order")


def new_order(user: str, product_id: int = 203, qty: int = 1) -> dict:
    """결제 대기 주문 하나(가짜 상품 203 「충전 케이블」 기본). {id, orderSn, payAmount}"""
    with portal() as c:
        h = {"Authorization": login(c, user), "X-Trace-Id": new_trace_id("s10order")}
        cid = cart_line(c, h, product_id, qty)
        o = order_of(place_order(c, h, user, cid))
    if not o:
        raise NotReady(f"{user}의 주문을 만들지 못했습니다(재고·배송지·로그인 확인)")
    return o


def concurrent(fn, n: int) -> list:
    """fn(i)를 n개 스레드에서 「거의 동시에」 부른다(장벽으로 맞춰 출발). n은 MAX_CONCURRENT를 넘을 수 없다. 반복 없음"""
    if n < 1 or n > MAX_CONCURRENT:
        raise ValueError(f"동시 요청 수는 1~{MAX_CONCURRENT}입니다")
    gate = threading.Barrier(n)

    def run(i):
        try:
            gate.wait(timeout=15)
        except threading.BrokenBarrierError:
            pass
        try:
            return fn(i)
        except httpx.HTTPError as e:
            return e
    with ThreadPoolExecutor(max_workers=n) as ex:
        return list(ex.map(run, range(n)))


# ── 가짜 결제사(9101)·가짜 문자(9102) ──
def pay_stats() -> dict:
    try:
        return httpx.get(PAY + "/_lab/stats", timeout=5).json()
    except httpx.HTTPError as e:
        raise NotReady(f"가짜 결제사({PAY})에 닿지 않습니다: {e.__class__.__name__}")


def pay_payments() -> list[dict]:
    return httpx.get(PAY + "/v1/payments", timeout=5).json()


def pay_webhooks() -> list[dict]:
    return httpx.get(PAY + "/v1/webhooks", timeout=5).json()


def pay_resend(payment_id: str) -> int:
    return httpx.post(f"{PAY}/v1/payments/{payment_id}/resend", timeout=5).status_code


def sms_stats() -> dict:
    try:
        return httpx.get(SMS + "/_lab/stats", timeout=5).json()
    except httpx.HTTPError as e:
        raise NotReady(f"가짜 문자({SMS})에 닿지 않습니다: {e.__class__.__name__}")


def sign(body: bytes) -> str:
    """가짜 결제사와 같은 서명(랩 전용 가짜 비밀). 시험이 「결제사가 보낸 것처럼」 웹훅을 만들 때만 쓴다"""
    return hmac.new(WEBHOOK_SECRET.encode(), body, hashlib.sha256).hexdigest()


def post_webhook(body: bytes, signature: str | None) -> httpx.Response:
    h = {"Content-Type": "application/json", "X-Trace-Id": new_trace_id("s10hook")}
    if signature is not None:
        h["X-Daon-Signature"] = signature
    return httpx.post(URLS["spring"]["portal"] + "/pay/webhook", content=body, headers=h, timeout=10)


def wait_status(order_id: int, want: int, timeout: float = 8.0) -> int | None:
    t0 = time.time()
    st = order_status(order_id)
    while st != want and time.time() - t0 < timeout:
        time.sleep(0.5)
        st = order_status(order_id)
    return st


def declined_tokens(n: int) -> list[str]:
    """끝자리가 2·4가 아닌 가짜 테스트 토큰(가짜 결제사가 거절). 카드 번호 모양이 아니다"""
    out, i = [], 0
    while len(out) < n:
        t = f"tok_test_{9000 + i:04d}"
        if t[-1] not in "24":
            out.append(t)
        i += 1
    return out
