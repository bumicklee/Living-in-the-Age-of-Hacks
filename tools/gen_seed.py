#!/usr/bin/env python3
"""다온마켓 가짜 시드 만들기(A5·A6). 같은 난수 씨드로 두 스택의 SQL을 함께 만든다.

    python tools/gen_seed.py          # seed/spring/30-daon-bulk.sql, seed/express/30-daon-bulk.sql

- 값은 전부 가짜다: 이름은 「성+가짜+번호」, 전화는 010-0000-xxxx, 메일은 *.example, 주소는 지어낸 동네.
- 고객 40명(alice 101·bob 102 + 103~140), 주문 300건(9001~9300), 상품 30개(201~230), 쿠폰 5종, 포인트.
- alice·bob 계정과 주문 9001~9003은 seed/spring/20-daon-accounts.sql이 넣는다. 이 파일은 그 번호를 건드리지 않는다.
  Express에는 20번 파일이 없으므로 alice·bob과 주문 9001~9003도 이 스크립트가 같은 값으로 넣는다.
- 같은 번호 체계: 회원 id = EverShop customer_id, 주문 id = order_id, 주문 번호(order_sn) = order_number,
  상품 id = product_id. 대응표는 seed/README.md.
"""
from __future__ import annotations

import json
import random
import uuid
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = 20261107                      # 시드 고정: 다시 만들어도 같은 파일
NS = uuid.UUID("6f0b6d1e-0000-4000-8000-da0da0da0da0")   # EverShop uuid용 이름공간(가짜)

# daon-pass-1! (bcrypt, cost 10). 20-daon-accounts.sql과 같은 값. EverShop(bcryptjs)도 $2a$를 읽는다
PW_HASH = "$2a$10$u0wE8GEVqNE2AP6u02Lrw.MzzCBfWTdG51t.vJLpjX0hwkbwj7izu"

SURNAMES = ["김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권"]
PLACES = [  # (시·도, 구, 동, 길) 모두 지어낸 이름
    ("서울특별시", "가상구", "다온동", "다온로"), ("서울특별시", "예시구", "샘플동", "샘플길"),
    ("부산광역시", "예시구", "샘플동", "바다길"), ("인천광역시", "가상구", "연습동", "연습로"),
    ("대구광역시", "모의구", "시험동", "시험길"), ("광주광역시", "가상구", "보기동", "보기로"),
    ("대전광역시", "예시구", "본보기동", "본보기길"), ("경기도", "가상시", "다온읍", "다온로"),
]
NOTES = [None, None, None, "문 앞에 놓아 주세요", "경비실에 맡겨 주세요", "배송 전에 연락 주세요"]

# (id, 이름, 분류 id, 가격). 분류: 101 생활 · 102 주방 · 103 디지털 · 104 패션 · 105 식품
CATEGORIES = [(101, "생활"), (102, "주방"), (103, "디지털"), (104, "패션"), (105, "식품")]
PRODUCTS = [
    (201, "다온 무선 이어폰", 103, 36000), (202, "다온 보조배터리 10000", 103, 24000),
    (203, "다온 충전 케이블 1m", 103, 8000), (204, "다온 블루투스 스피커", 103, 49000),
    (205, "다온 스마트워치 밴드", 103, 15000), (206, "다온 노트북 거치대", 103, 32000),
    (207, "다온 텀블러 500ml", 102, 12000), (208, "다온 스테인리스 냄비", 102, 45000),
    (209, "다온 실리콘 주걱", 102, 6000), (210, "다온 유리 밀폐용기 3종", 102, 21000),
    (211, "다온 원두 커피 200g", 105, 14000), (212, "다온 무농약 현미 2kg", 105, 11000),
    (213, "다온 견과 한 줌 30봉", 105, 27000), (214, "다온 제주 녹차 티백", 105, 9000),
    (215, "다온 탄산수 24병", 105, 16000), (216, "다온 우산", 101, 22000),
    (217, "다온 극세사 수건 5장", 101, 18000), (218, "다온 미니 가습기", 101, 29000),
    (219, "다온 수납 바구니", 101, 13000), (220, "다온 LED 무드등", 101, 19000),
    (221, "다온 면 양말 5켤레", 104, 10000), (222, "다온 기본 티셔츠", 104, 17000),
    (223, "다온 캔버스 에코백", 104, 15000), (224, "다온 방수 바람막이", 104, 69000),
    (225, "다온 털모자", 104, 21000), (226, "다온 가죽 카드지갑", 104, 33000),
    (227, "다온 요가 매트", 101, 35000), (228, "다온 손 세정제 3개", 101, 9900),
    (229, "다온 키보드 손목 받침", 103, 14000), (230, "다온 핸드드립 세트", 102, 39000),
]
# (id, 이름, 할인액, 최소 주문액, EverShop 쿠폰 코드)
COUPONS = [
    (101, "다온 첫 주문 3천원 할인", 3000, 20000, "DAON-WELCOME"),
    (102, "다온 5천원 할인", 5000, 40000, "DAON-5000"),
    (103, "다온 만원 할인", 10000, 80000, "DAON-10000"),
    (104, "다온 배송비 쿠폰", 3000, 0, "DAON-FREESHIP"),
    (105, "다온 가을 감사 2천원", 2000, 15000, "DAON-AUTUMN"),
]
COURIER = "다온택배"     # 택배 스텁(stubs/courier)이 이 송장 번호를 받는다
FREIGHT = 3000
FREE_FREIGHT_FROM = 30000

# 20-daon-accounts.sql에 이미 있는 값(바꾸지 않는다). Express 쪽에도 같은 값으로 넣는다.
FIXED_MEMBERS = [
    dict(id=101, username="alice", name="김앨리스", nickname="앨리스", phone="010-0000-0101", gender=2,
         created="2026-11-01 10:00:00", points=1000, place=("서울특별시", "가상구", "다온동", "다온로 101, 101동 1001호"), post="00001"),
    dict(id=102, username="bob", name="이밥", nickname="밥", phone="010-0000-0102", gender=1,
         created="2026-11-01 10:05:00", points=1000, place=("부산광역시", "예시구", "샘플동", "샘플길 22"), post="00002"),
]
FIXED_ORDERS = [  # (id, 회원, 주문 번호, 시각, 상태, 상품 줄[(item id, 상품 id, 이름, 값, 수량)], 배송비, 메모)
    (9001, 101, "DAON202611010001", "2026-11-01 11:00:00", 1, [(90011, 201, "다온 무선 이어폰", 36000, 1)], 3000, "문 앞에 놓아 주세요"),
    (9002, 101, "DAON202611020002", "2026-11-02 15:30:00", 3, [(90021, 207, "다온 텀블러", 12000, 1)], 0, None),
    (9003, 102, "DAON202611030003", "2026-11-03 09:10:00", 1, [(90031, 216, "다온 우산", 22000, 1)], 3000, None),
]
# Spring 상태 → EverShop (status, payment_status, shipment_status)
STATUS = {0: ("new", "pending", "pending"), 1: ("processing", "paid", "pending"), 2: ("processing", "paid", "shipped"),
          3: ("completed", "paid", "delivered"), 4: ("canceled", "canceled", "canceled")}


def q(v) -> str:
    """SQL 값. 두 DB 모두 표준 따옴표 이스케이프만 쓴다(값은 이 스크립트가 만든 가짜 글자뿐)."""
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def rows(values: list[tuple]) -> str:
    return ",\n".join("  (" + ", ".join(q(v) for v in r) + ")" for r in values)


def uid(kind: str, key) -> str:
    return str(uuid.uuid5(NS, f"{kind}:{key}"))


def build() -> dict:
    rnd = random.Random(SEED)
    members = [dict(m) for m in FIXED_MEMBERS]
    for mid in range(103, 141):
        sur = SURNAMES[(mid - 103) % len(SURNAMES)]
        n = mid - 100
        prov, gu, dong, road = rnd.choice(PLACES)
        members.append(dict(
            id=mid, username=f"daon{mid}", name=f"{sur}가짜{n:02d}", nickname=f"가짜고객{n:02d}",
            phone=f"010-0000-{mid:04d}", gender=rnd.choice([1, 2]),
            created=(datetime(2026, 6, 1, 9) + timedelta(days=rnd.randint(0, 60), minutes=rnd.randint(0, 600))).strftime("%Y-%m-%d %H:%M:%S"),
            points=rnd.choice([0, 500, 1000, 1500, 2000, 3000, 5000]),
            place=(prov, gu, dong, f"{road} {rnd.randint(1, 300)}, {rnd.randint(101, 120)}동 {rnd.randint(1, 20)}0{rnd.randint(1, 4)}호"),
            post=f"{n:05d}",
        ))
    by_id = {m["id"]: m for m in members}
    price = {p[0]: p[3] for p in PRODUCTS}
    pname = {p[0]: p[1] for p in PRODUCTS}

    orders = []
    for oid, mid, sn, ts, status, items, freight, note in FIXED_ORDERS:
        orders.append(dict(id=oid, member=mid, sn=sn, ts=ts, status=status, freight=freight, note=note, coupon=None,
                           points=0, items=[dict(id=i, product=p, name=nm, price=pr, qty=qty) for i, p, nm, pr, qty in items]))
    # 9004~9300: 103~140 고객에게 고루. 시각은 8/1~11/6에서 번호 순서대로 늘어난다
    start, span = datetime(2026, 8, 1, 9), 97 * 24 * 60
    stamps = sorted(rnd.randint(0, span) for _ in range(297))
    member_ids = [m["id"] for m in members[2:]]
    for k, minute in enumerate(stamps):
        oid = 9004 + k
        when = start + timedelta(minutes=minute)
        mid = member_ids[k % len(member_ids)] if k < len(member_ids) else rnd.choice(member_ids)
        picks = rnd.sample([p[0] for p in PRODUCTS], rnd.choice([1, 1, 1, 2, 2, 3]))
        items = [dict(id=oid * 10 + j + 1, product=p, name=pname[p], price=price[p], qty=rnd.choice([1, 1, 1, 2, 3]))
                 for j, p in enumerate(picks)]
        total = sum(i["price"] * i["qty"] for i in items)
        age = (datetime(2026, 11, 7) - when).days
        if age > 14:
            status = rnd.choices([3, 4], [9, 1])[0]
        elif age > 4:
            status = rnd.choices([2, 3, 4], [4, 5, 1])[0]
        else:
            status = rnd.choices([0, 1, 2], [3, 5, 2])[0]
        orders.append(dict(id=oid, member=mid, sn=f"DAON{when:%Y%m%d}{oid - 9000:04d}", ts=f"{when:%Y-%m-%d %H:%M:%S}",
                           status=status, freight=0 if total >= FREE_FREIGHT_FROM else FREIGHT,
                           note=rnd.choice(NOTES), coupon=None, points=0, items=items))
    # 쿠폰: 고객마다 1~2장. 쓴 쿠폰은 그 주문에 연결
    history = []
    hid = 1001
    for m in members:
        for c in rnd.sample(COUPONS, rnd.choice([1, 1, 2])):
            history.append(dict(id=hid, coupon=c, member=m["id"], used=None))
            hid += 1
    for h in history:
        if h["member"] in (101, 102):
            continue   # alice·bob 쿠폰은 쓰지 않은 채로 둔다(고객 웹에서 볼 수 있게)
        cands = [o for o in orders if o["member"] == h["member"] and o["coupon"] is None and o["status"] != 0
                 and sum(i["price"] * i["qty"] for i in o["items"]) >= h["coupon"][3]]
        if cands and rnd.random() < 0.5:
            o = rnd.choice(cands)
            o["coupon"] = h["coupon"]
            h["used"] = o
    # 포인트: 몇 건은 포인트로 일부 결제(1포인트 = 1원으로 단순화)
    for o in orders[3:]:
        m = by_id[o["member"]]
        if m["points"] >= 1000 and rnd.random() < 0.15:
            o["points"] = 1000
    for o in orders:
        o["total"] = sum(i["price"] * i["qty"] for i in o["items"])
        o["coupon_amount"] = o["coupon"][2] if o["coupon"] else 0
        o["pay"] = o["total"] + o["freight"] - o["coupon_amount"] - o["points"]
        if o["status"] >= 2 and o["status"] != 4:
            o["delivery_sn"] = f"DT{o['id']:010d}"
        else:
            o["delivery_sn"] = None
    stock = {p[0]: 200 + rnd.randint(0, 300) for p in PRODUCTS}
    return dict(members=members, orders=orders, history=history, stock=stock)


def dt(s: str, minutes: int = 0) -> str:
    return (datetime.strptime(s, "%Y-%m-%d %H:%M:%S") + timedelta(minutes=minutes)).strftime("%Y-%m-%d %H:%M:%S")


HEADER = """-- 자동 생성 파일: tools/gen_seed.py (시드 {seed}). 손으로 고치지 말고 스크립트를 고친 뒤 다시 만든다.
-- 모든 값은 가짜다: 이름 「성+가짜+번호」, 전화 010-0000-xxxx, 메일 *.example, 지어낸 주소. 비밀번호는 모두 daon-pass-1!
-- 고객 {members}명 · 주문 {orders}건 · 상품 {products}개 · 쿠폰 {coupons}종. 번호 대응표: seed/README.md
"""


def spring_sql(d: dict) -> str:
    out = [HEADER.format(seed=SEED, members=len(d["members"]), orders=len(d["orders"]), products=len(PRODUCTS), coupons=len(COUPONS)),
           "SET NAMES utf8mb4;\n"]
    new_members = [m for m in d["members"] if m["id"] >= 103]
    out.append("-- 고객 103~140(alice 101·bob 102는 20-daon-accounts.sql)")
    out.append("INSERT INTO ums_member (id, member_level_id, username, password, nickname, phone, status, create_time, gender, source_type, integration, growth, history_integration)\nVALUES\n"
               + rows([(m["id"], 4, m["username"], PW_HASH, m["nickname"], m["phone"], 1, m["created"], m["gender"], 1, m["points"], 100, m["points"]) for m in new_members]) + ";\n")
    out.append("-- 배송지: 고객마다 하나(id = 회원 id). alice·bob도 여기서 받는다(주문하기에 필요)")
    out.append("INSERT INTO ums_member_receive_address (id, member_id, name, phone_number, default_status, post_code, province, city, region, detail_address)\nVALUES\n"
               + rows([(m["id"], m["id"], m["name"], m["phone"], 1, m["post"], m["place"][0], m["place"][1], m["place"][2], m["place"][3]) for m in d["members"]]) + ";\n")
    out.append("-- 브랜드·분류·상품(201~230)·재고 단위(SKU 2001~2030)")
    out.append("INSERT INTO pms_brand (id, name, first_letter, sort, factory_status, show_status, product_count, product_comment_count, brand_story)\nVALUES\n"
               + rows([(101, "다온", "D", 0, 1, 1, len(PRODUCTS), 0, "다온마켓 자체 브랜드(가짜)")]) + ";")
    out.append("INSERT INTO pms_product_category (id, parent_id, name, level, product_count, product_unit, nav_status, show_status, sort, keywords, description)\nVALUES\n"
               + rows([(cid, 0, name, 0, sum(1 for p in PRODUCTS if p[2] == cid), "개", 1, 1, 0, name, f"다온마켓 {name}") for cid, name in CATEGORIES]) + ";")
    cname = dict(CATEGORIES)
    out.append("INSERT INTO pms_product (id, brand_id, product_category_id, product_attribute_category_id, name, product_sn, delete_status, publish_status, new_status, recommand_status, verify_status, sort, sale, price, original_price, gift_growth, gift_point, use_point_limit, sub_title, description, stock, low_stock, unit, promotion_type, brand_name, product_category_name, keywords)\nVALUES\n"
               + rows([(pid, 101, cid, 4, name, f"DAON-P{pid}", 0, 1, 1, 1, 1, 0, 0, pr, pr, 0, 0, 0, f"{cname[cid]} · 다온마켓 가짜 상품", f"{name}. 교육용 랩의 가짜 상품입니다.", d["stock"][pid], 10, "개", 0, "다온", cname[cid], name)
                       for pid, name, cid, pr in PRODUCTS]) + ";")
    out.append("INSERT INTO pms_sku_stock (id, product_id, sku_code, price, stock, low_stock, sale, lock_stock, sp_data)\nVALUES\n"
               + rows([(1800 + pid, pid, f"DAON-P{pid}-01", pr, d["stock"][pid], 10, 0, 0, '[{"key":"옵션","value":"기본"}]') for pid, _n, _c, pr in PRODUCTS]) + ";\n")
    out.append("-- 쿠폰 5종(id 101~105)과 받은 쿠폰(1001~). 기간은 2026~2027년")
    out.append("INSERT INTO sms_coupon (id, type, name, platform, count, amount, per_limit, min_point, start_time, end_time, use_type, note, publish_count, use_count, receive_count, enable_time, code, member_level)\nVALUES\n"
               + rows([(cid, 0, name, 0, 1000, amt, 1, minp, "2026-01-01 00:00:00", "2027-12-31 23:59:59", 0, f"교육용 가짜 쿠폰 {code}", 1000,
                        sum(1 for h in d["history"] if h["coupon"][0] == cid and h["used"]), sum(1 for h in d["history"] if h["coupon"][0] == cid),
                        "2026-01-01 00:00:00", code, 0) for cid, name, amt, minp, code in COUPONS]) + ";")
    out.append("INSERT INTO sms_coupon_history (id, coupon_id, member_id, coupon_code, member_nickname, get_type, create_time, use_status, use_time, order_id, order_sn)\nVALUES\n"
               + rows([(h["id"], h["coupon"][0], h["member"], h["coupon"][4], next(m["nickname"] for m in d["members"] if m["id"] == h["member"]), 1,
                        "2026-08-01 00:00:00", 1 if h["used"] else 0, h["used"]["ts"] if h["used"] else None,
                        h["used"]["id"] if h["used"] else None, h["used"]["sn"] if h["used"] else None) for h in d["history"]]) + ";\n")
    out.append("-- 주문 9004~9300과 주문 상품(주문 id×10 + 순번). 9001~9003은 20-daon-accounts.sql")
    by_id = {m["id"]: m for m in d["members"]}
    new_orders = [o for o in d["orders"] if o["id"] >= 9004]
    orows = []
    for o in new_orders:
        m = by_id[o["member"]]
        st = o["status"]
        orows.append((o["id"], o["member"], o["coupon"][0] if o["coupon"] else None, o["sn"], o["ts"], m["username"], o["total"], o["pay"], o["freight"],
                      0, o["points"], o["coupon_amount"], 0, 1 if st else 0, 1, st, 0, COURIER if o["delivery_sn"] else None, o["delivery_sn"], 15,
                      o["points"], m["name"], m["phone"], m["post"], m["place"][0], m["place"][1], m["place"][2], m["place"][3], o["note"],
                      1 if st == 3 else 0, 0, dt(o["ts"], 3) if st in (1, 2, 3) else None,
                      dt(o["ts"], 60 * 24) if st in (2, 3) else None, dt(o["ts"], 60 * 72) if st == 3 else None))
    for i in range(0, len(orows), 100):
        out.append("INSERT INTO oms_order (id, member_id, coupon_id, order_sn, create_time, member_username, total_amount, pay_amount, freight_amount, "
                   "promotion_amount, integration_amount, coupon_amount, discount_amount, pay_type, source_type, status, order_type, delivery_company, delivery_sn, "
                   "auto_confirm_day, use_integration, receiver_name, receiver_phone, receiver_post_code, receiver_province, receiver_city, receiver_region, "
                   "receiver_detail_address, note, confirm_status, delete_status, payment_time, delivery_time, receive_time)\nVALUES\n" + rows(orows[i:i + 100]) + ";")
    irows = [(i["id"], o["id"], o["sn"], i["product"], i["name"], "다온", f"DAON-P{i['product']}", i["price"], i["qty"], 1800 + i["product"],
              f"DAON-P{i['product']}-01", next(p[2] for p in PRODUCTS if p[0] == i["product"]), 0, 0, 0, i["price"] * i["qty"], '[{"key":"옵션","value":"기본"}]')
             for o in new_orders for i in o["items"]]
    for k in range(0, len(irows), 200):
        out.append("INSERT INTO oms_order_item (id, order_id, order_sn, product_id, product_name, product_brand, product_sn, product_price, product_quantity, "
                   "product_sku_id, product_sku_code, product_category_id, promotion_amount, coupon_amount, integration_amount, real_amount, product_attr)\nVALUES\n"
                   + rows(irows[k:k + 200]) + ";")
    out.append("\n-- 포인트 내역(가입 축하 + 주문에 쓴 포인트)")
    prows, pid = [], 1001
    for m in new_members:
        prows.append((pid, m["id"], m["created"], 0, m["points"] + sum(o["points"] for o in d["orders"] if o["member"] == m["id"]), "system", "가입 축하 포인트(가짜)", 0))
        pid += 1
    for o in new_orders:
        if o["points"]:
            prows.append((pid, o["member"], o["ts"], 1, o["points"], o["member"] and by_id[o["member"]]["username"], f"주문 {o['sn']}에 사용", 1))
            pid += 1
    out.append("INSERT INTO ums_integration_change_history (id, member_id, create_time, change_type, change_count, operate_man, operate_note, source_type)\nVALUES\n" + rows(prows) + ";")
    return "\n".join(out) + "\n"


def express_sql(d: dict) -> str:
    out = [HEADER.format(seed=SEED, members=len(d["members"]), orders=len(d["orders"]), products=len(PRODUCTS), coupons=len(COUPONS)),
           "-- EverShop(PostgreSQL). 표는 앱이 처음 뜰 때 만든다(migration). 그다음 daon/stack.py가 이 파일을 한 번 넣는다.",
           "-- 이벤트 트리거(주문 생성 메일 등)가 300번 돌지 않게 이 세션만 트리거를 끈다.",
           "BEGIN;", "SET LOCAL session_replication_role = replica;\n"]
    ms = d["members"]
    out.append("-- 고객: customer_id = Spring 회원 id, 메일 = <username>@daon.example, 포인트는 meta_data.points")
    out.append("INSERT INTO customer (customer_id, uuid, status, group_id, email, password, full_name, created_at, updated_at, meta_data) OVERRIDING SYSTEM VALUE\nVALUES\n"
               + rows([(m["id"], uid("customer", m["id"]), 1, 1, f"{m['username']}@daon.example", PW_HASH, m["name"], m["created"] + "+09", m["created"] + "+09",
                        json.dumps({"daon_member_id": m["id"], "username": m["username"], "points": m["points"], "phone": m["phone"]}, ensure_ascii=False)) for m in ms]) + ";")
    out.append("INSERT INTO customer_address (customer_address_id, uuid, customer_id, recipient, telephone, address_line_1, address_line_2, postal_code, locality, administrative_area, country, is_default) OVERRIDING SYSTEM VALUE\nVALUES\n"
               + rows([(m["id"], uid("caddr", m["id"]), m["id"], m["name"], m["phone"], f"{m['place'][2]} {m['place'][3]}", None, m["post"], m["place"][1], m["place"][0], "KR", True) for m in ms]) + ";\n")
    out.append("-- 상품: product_id = Spring 상품 id(201~230), sku = DAON-P<id>")
    out.append("INSERT INTO product (product_id, uuid, type, visibility, group_id, sku, price, weight, tax_class, status, no_shipping_required) OVERRIDING SYSTEM VALUE\nVALUES\n"
               + rows([(pid, uid("product", pid), "simple", True, 1, f"DAON-P{pid}", pr, 1, 1, True, False) for pid, _n, _c, pr in PRODUCTS]) + ";")
    out.append("INSERT INTO product_description (product_description_product_id, name, description, short_description, url_key, meta_title)\nVALUES\n"
               + rows([(pid, name, None, f"{name}. 교육용 랩의 가짜 상품입니다.", f"daon-p{pid}", name) for pid, name, _c, _p in PRODUCTS]) + ";")
    out.append("INSERT INTO product_inventory (product_inventory_product_id, qty, manage_stock, stock_availability)\nVALUES\n"
               + rows([(pid, d["stock"][pid], True, True) for pid, *_ in PRODUCTS]) + ";")
    out.append("-- 상품 주소(/daon-p201). 보통은 앱의 상품 생성 이벤트가 만들지만 이 세션은 트리거를 껐으므로 직접 넣는다")
    out.append("INSERT INTO url_rewrite (request_path, target_path, entity_uuid, entity_type)\nVALUES\n"
               + rows([(f"/daon-p{pid}", f"/product/{uid('product', pid)}", uid("product", pid), "product") for pid, *_ in PRODUCTS]) + ";\n")
    out.append("-- 쿠폰: Spring 쿠폰 id 101~105 = coupon_id, 코드는 sms_coupon.code와 같다")
    out.append("INSERT INTO coupon (coupon_id, uuid, status, description, discount_amount, free_shipping, discount_type, coupon, used_time, condition, user_condition, max_uses_time_per_coupon, max_uses_time_per_customer, start_date, end_date) OVERRIDING SYSTEM VALUE\nVALUES\n"
               + rows([(cid, uid("coupon", cid), True, name, amt, False, "fixed_discount_to_entire_order", code,
                        sum(1 for h in d["history"] if h["coupon"][0] == cid and h["used"]), json.dumps({"order_total": minp}), json.dumps({}), 1000, 1,
                        "2026-01-01 00:00:00+09", "2027-12-31 23:59:59+09") for cid, name, amt, minp, code in COUPONS]) + ";\n")
    out.append("-- 주문: order_id = Spring 주문 id(9001~9300), order_number = Spring order_sn. 배송지 order_address_id = 주문 id")
    by_id = {m["id"]: m for m in ms}
    arows, orows, irows = [], [], []
    for o in d["orders"]:
        m = by_id[o["member"]]
        st, pay, ship = STATUS[o["status"]]
        arows.append((o["id"], uid("oaddr", o["id"]), m["name"], m["post"], m["phone"], "KR", m["place"][0], m["place"][1], f"{m['place'][2]} {m['place'][3]}"))
        qty = sum(i["qty"] for i in o["items"])
        sub = o["total"]
        disc = o["coupon_amount"] + o["points"]
        orows.append((o["id"], uid("order", o["id"]), o["sn"], st, o["id"], "KRW", m["id"], f"{m['username']}@daon.example", m["name"],
                      o["coupon"][4] if o["coupon"] else None, o["freight"], o["freight"], disc, sub, sub, sub - disc, sub - disc, qty, qty,
                      0, 0, 0, o["note"], o["pay"], o["id"], "cod", "가짜 결제(교육용)", o["id"], ship, pay, o["ts"] + "+09", o["ts"] + "+09", 0,
                      json.dumps({"daon_points_used": o["points"], "delivery_sn": o["delivery_sn"]}), o["ts"] + "+09"))
        for i in o["items"]:
            line = i["price"] * i["qty"]
            irows.append((i["id"], uid("oitem", i["id"]), o["id"], i["product"], f"DAON-P{i['product']}", i["name"], 1, i["price"], i["price"], i["qty"],
                          i["price"], i["price"], 0, 0, 0, 0, line, line, line, line, False))
    out.append("INSERT INTO order_address (order_address_id, uuid, recipient, postal_code, telephone, country, administrative_area, locality, address_line_1) OVERRIDING SYSTEM VALUE\nVALUES\n" + rows(arows) + ";")
    for k in range(0, len(orows), 100):
        out.append('INSERT INTO "order" (order_id, uuid, order_number, status, cart_id, currency, customer_id, customer_email, customer_full_name, coupon, '
                   "shipping_fee_excl_tax, shipping_fee_incl_tax, discount_amount, sub_total, sub_total_incl_tax, sub_total_with_discount, sub_total_with_discount_incl_tax, "
                   "total_qty, total_weight, tax_amount, tax_amount_before_discount, shipping_tax_amount, shipping_note, grand_total, shipping_address_id, payment_method, "
                   "payment_method_name, billing_address_id, shipment_status, payment_status, created_at, updated_at, total_tax_amount, meta_data, placed_at) OVERRIDING SYSTEM VALUE\nVALUES\n"
                   + rows(orows[k:k + 100]) + ";")
    for k in range(0, len(irows), 200):
        out.append("INSERT INTO order_item (order_item_id, uuid, order_item_order_id, product_id, product_sku, product_name, product_weight, product_price, product_price_incl_tax, qty, "
                   "final_price, final_price_incl_tax, tax_percent, tax_amount, tax_amount_before_discount, discount_amount, line_total, line_total_with_discount, "
                   "line_total_incl_tax, line_total_with_discount_incl_tax, no_shipping_required) OVERRIDING SYSTEM VALUE\nVALUES\n" + rows(irows[k:k + 200]) + ";")
    out.append("\n-- 다음 번호가 시드 뒤에서 이어지게(앱이 새로 만드는 고객·주문)")
    for table, col in [("customer", "customer_id"), ("customer_address", "customer_address_id"), ("product", "product_id"), ("coupon", "coupon_id"),
                       ("order", "order_id"), ("order_address", "order_address_id"), ("order_item", "order_item_id")]:
        out.append(f"SELECT setval(pg_get_serial_sequence('\"{table}\"', '{col}'), (SELECT max({col}) FROM \"{table}\"));")
    out.append("COMMIT;")
    return "\n".join(out) + "\n"


def main() -> None:
    d = build()
    for stack, text in (("spring", spring_sql(d)), ("express", express_sql(d))):
        p = ROOT / "seed" / stack / "30-daon-bulk.sql"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, "utf-8")
        print(f"{p.relative_to(ROOT)}: {len(text.encode()) // 1024}KB")
    print(f"고객 {len(d['members'])}명 · 주문 {len(d['orders'])}건 · 상품 {len(PRODUCTS)}개 · 쿠폰 {len(COUPONS)}종 · 받은 쿠폰 {len(d['history'])}장")


if __name__ == "__main__":
    main()
