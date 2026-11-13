"""T27 · 요청 하나는 정상인데 규모가 비정상인 공격: 카드 테스팅·사재기(10-6), 인증 문자 펌핑(10-7). 사용자별·전체 상한 없음.

PART(make replay T=27 PART=card|hoard|sms, 비우면 셋 다):
  card : bob이 결제 대기 주문 하나에 가짜 테스트 카드 토큰 30개를 차례로 POST /pay/start(30번 고정, 모두 거절되는 토큰).
         레드: 30번 모두 가짜 결제사까지 감   그린: 5번까지만 가고 나머지는 429(계정 축) + checkout.denied.rate_limit
         (/pay/start는 10-4에서 생긴다. 그 전이면 준비 안 됨)
  hoard: bob이 한정 상품(230 「다온 핸드드립 세트」, 1인 2개)을 한 주문에 20개.   레드: 주문됨   그린: 거절 + purchase.denied.per_member_limit
  sms  : 로그인 없이 GET /sso/getAuthCode에 가짜 번호 010-0000-0001~0050(50건 고정) + 국외 모양 번호 하나.
         레드: 가짜 문자 스텁에 21통 이상 쌓이거나 국외 번호가 스텁까지 감(스텁이 형식 거절)
         그린: 20통에서 멈춤(전체 시간당 예산) + 국외 번호는 앱이 400 + budget.tripped.global
안전 기준: 127.0.0.1의 랩만, 가짜 토큰·가짜 번호만, 요청 수는 이 파일에 고정. 시작 전에 랩 카운터(rl:*)를 비운다. 만든 주문은 닫는다.
"""
from __future__ import annotations

import os
import time

import httpx

from daon.config import URLS
from replay import NotReady, Result
from replay import _money as m
from replay._http import new_trace_id, portal

T = "T27"
TITLE = "결제 시도·한정 상품·인증 문자에 사용자별·전체 상한 없음(카드 테스팅·사재기·SMS 펌핑)"
LESSON = "10-6·10-7"
SOURCE = "[교육용 추가] /pay/start(10-4)·한정 상품 표시(설정 daon.checkout.limited-products) + [원본 확인됨] /sso/getAuthCode 발송 상한 없음"
STACKS = ("spring",)

CARD_TRIES = 30
CARD_OK_MAX = 5
HOARD_PRODUCT, HOARD_QTY = 230, 20
SMS_NUMBERS = [f"010-0000-{i:04d}" for i in range(1, 51)]        # 가짜 번호 50개(시드 고객 번호 0101~0140과 겹치지 않음)
SMS_FOREIGN = "+1-202-555-0100"                                   # 국외 모양(가짜, 555 국번)
SMS_BUDGET = 20


def card_scene(user: str = "bob") -> dict:
    m.clear_counters()
    o = m.new_order(user, 203)
    key = "pay-start-" + o["orderSn"]
    before = sum(1 for i in m.pay_stats()["items"] if i.get("key") == key)
    statuses = []
    with portal() as c:
        token = m.login(c, user)
        for t in m.declined_tokens(CARD_TRIES):
            r = c.post("/pay/start", params={"orderId": o["id"], "cardToken": t},
                       headers={"Authorization": token, "X-Trace-Id": new_trace_id("t27c")})
            statuses.append(r.status_code)
            time.sleep(0.05)
    reached = sum(1 for i in m.pay_stats()["items"] if i.get("key") == key) - before
    m.close_orders([o["id"]])
    if statuses and all(s == 404 for s in statuses) and reached == 0:
        raise NotReady("POST /pay/start가 없습니다(10-4 정답 뒤에 생긴다). PART=hoard·sms는 지금도 볼 수 있습니다")
    return {"order": o["id"], "reached": reached, "429": statuses.count(429), "402": statuses.count(402), "other": len(statuses) - statuses.count(429) - statuses.count(402)}


def hoard_scene(user: str = "bob") -> dict:
    m.clear_counters()
    m.reset_purchase(m.member_id(user), HOARD_PRODUCT)
    with portal() as c:
        h = {"Authorization": m.login(c, user), "X-Trace-Id": new_trace_id("t27h")}
        cid = m.cart_line(c, h, HOARD_PRODUCT, HOARD_QTY)
        r = m.place_order(c, h, user, cid)
        o = m.order_of(r)
        if not o:
            c.post(f"/cart/delete?ids={cid}", headers=h)
    if o:
        m.close_orders([o.get("id")])
    try:
        msg = r.json().get("message")
    except ValueError:
        msg = f"HTTP {r.status_code}"
    return {"ordered": bool(o), "message": msg}


def sms_scene() -> dict:
    m.clear_counters()
    s0 = m.sms_stats()
    codes = []
    with httpx.Client(base_url=URLS["spring"]["portal"], timeout=10) as c:
        for n in SMS_NUMBERS:
            codes.append(c.get("/sso/getAuthCode", params={"telephone": n}, headers={"X-Trace-Id": new_trace_id("t27s")}).status_code)
        foreign = c.get("/sso/getAuthCode", params={"telephone": SMS_FOREIGN}, headers={"X-Trace-Id": new_trace_id("t27f")}).status_code
    s1 = m.sms_stats()
    return {"sent": s1["messages"] - s0["messages"], "rejected_at_stub": s1["rejected_format"] - s0["rejected_format"],
            "200": codes.count(200), "429": codes.count(429), "503": codes.count(503), "foreign_http": foreign}


def run(stack: str = "spring", part: str | None = None) -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 Spring만(Express는 diffs/s10-06·s10-07).")
    part = (part or os.environ.get("DAON_PART") or "").strip().lower()
    parts = [part] if part else ["card", "hoard", "sms"]
    for p in parts:
        if p not in ("card", "hoard", "sms"):
            raise NotReady("PART는 card·hoard·sms 중 하나입니다")
    ev, bad = [], []
    if "card" in parts:
        try:
            cs = card_scene()
            ev.append(f"card: bob 주문 {cs['order']}에 가짜 토큰 {CARD_TRIES}개 → 가짜 결제사까지 간 시도 {cs['reached']}번 · 429 {cs['429']} · 카드 거절(402) {cs['402']} · 그 밖 {cs['other']}")
            if cs["reached"] > CARD_OK_MAX:
                bad.append(f"결제 시도 {cs['reached']}번이 모두 결제사까지 감(카드 테스팅)")
        except NotReady as e:
            if part == "card":
                raise
            ev.append(f"card: 준비 안 됨 — {e}")
    if "hoard" in parts:
        hs = hoard_scene()
        ev.append(f"hoard: bob이 한정 상품 {HOARD_PRODUCT} {HOARD_QTY}개 주문 → {'주문됨' if hs['ordered'] else '거절'} ({hs['message']})")
        if hs["ordered"]:
            bad.append(f"한정 상품 {HOARD_QTY}개를 한 계정이 삼(사재기)")
    if "sms" in parts:
        ss = sms_scene()
        ev.append(f"sms: 가짜 번호 {len(SMS_NUMBERS)}개 → 가짜 문자 스텁에 쌓인 문자 {ss['sent']}통 (200 {ss['200']} · 429 {ss['429']} · 503 {ss['503']})")
        ev.append(f"     국외 모양 번호 → 앱 응답 HTTP {ss['foreign_http']} · 스텁이 형식으로 거절한 요청 {ss['rejected_at_stub']}건")
        if ss["sent"] > SMS_BUDGET:
            bad.append(f"인증 문자 {ss['sent']}통이 모두 나감(SMS 펌핑)")
        if ss["rejected_at_stub"] > 0:
            bad.append("국외 번호를 앱이 거르지 않고 문자 사업자에 넘김")
    if bad:
        return Result(True, " · ".join(bad), ev)
    return Result(False, "흐름 단위 한도가 결제 시도·한정 상품·인증 문자를 멈췄다", ev)
