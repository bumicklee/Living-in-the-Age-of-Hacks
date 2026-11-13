"""14-4 모의 침해 재생: make drill D=14-4 (약 1~2분, 가짜 값만, 모든 요청에 X-Trace-Id: drill-14-4-<순번>).

앞 장들에서 이미 막아 둔 요청만 보낸다. 피해는 없다. 수강생이 답할 질문은 「무엇이 시도됐고, 어디까지 갔나」다.
  1) 가짜 고객 daon117(member:117)로 틀린 비밀번호 5번 → 6번째는 로그인 속도 제한(4-3, auth.login.rate_limited)
     → (랩 도구가 10분 기다리는 대신 카운터만 비움) → 맞는 비밀번호로 로그인
  2) 같은 계정으로 남의 주문 번호 6개를 연달아 조회(2-2, access.denied.not_owner, count 증가)
  3) 화면이 보내지 않는 필드(포인트·등급)를 섞어 회원정보 수정(2-4, input.unexpected_fields). 값은 바뀌지 않는다
  4) 가격을 1원으로 바꿔 장바구니에 담기(10-8, price.tampered). 담긴 줄은 끝에 지운다
  5) 같은 PC(같은 IP)에서 다른 가짜 고객 daon118·daon119로 로그인 → 남의 주문 3개씩 조회
정답 표(이벤트 순서·건수)는 expected()가 돌려준다. 실제 줄 수와 맞춰 보는 것은 python -m daon timeline과 make check L=14-4.
안전 기준: 127.0.0.1의 다온마켓만. 요청 수는 이 파일에 고정(인자로 바꿀 수 없다). 계정 잠금·로그인 카운터 정리는 랩 도구(컨테이너 안 mysql·redis-cli).
"""
from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone

import httpx

from daon import stack as st
from daon.config import DATA, URLS
from replay import NotReady
from replay._money import mysql, one
from replay.t09_login_guard import clear_counters

PW = "daon-pass-1!"                     # 가짜 시드 고객의 비밀번호(tools/gen_seed.py)
WRONG = "not-the-password-14!"
LEAD, OTHERS = 117, (118, 119)          # 모의 침해에 쓰는 가짜 고객(member id = 아이디 daon<id>)
SCAN_LEAD, SCAN_OTHER = 6, 3
FAILS = 5
PRODUCT = 202                           # 다온 보조배터리(T37과 같은 상품)
STATE = DATA / "drill-14-4.json"
PREFIX = "drill-14-4-"


def username(mid: int) -> str:
    return f"daon{mid}"


def reset_actors() -> None:
    """랩 도구: 모의 침해 계정을 처음 상태로(잠금 풀기, 14-4 격리 표시·회원 캐시·로그인 카운터 지우기)"""
    ids = (LEAD, *OTHERS)
    mysql(f"UPDATE ums_member SET status=1 WHERE id IN ({','.join(map(str, ids))})")
    for mid in ids:
        st.redis_cli("DEL", f"mall:ums:member:{username(mid)}", f"deny:sub:daon-portal:{username(mid)}")
    clear_counters()


def victims() -> list[int]:
    """모의 침해가 훑는 남의 주문 번호(가짜 시드에서 이 세 계정의 주문이 아닌 것, 앞에서부터 12개)"""
    rows = mysql(f"SELECT id FROM oms_order WHERE member_id NOT IN ({LEAD},{','.join(map(str, OTHERS))}) AND id BETWEEN 9001 AND 9300 "
                 f"ORDER BY id LIMIT {SCAN_LEAD + SCAN_OTHER * len(OTHERS)}")
    ids = [int(x) for x in rows.split()]
    if len(ids) < SCAN_LEAD + SCAN_OTHER * len(OTHERS):
        raise NotReady("가짜 시드 주문이 부족합니다(make wipe 후 make up)")
    return ids


def expected() -> dict:
    """정답 표: 이 재생이 남겨야 하는 SECURITY 줄(event·actor → 건수)과 영향 범위"""
    rows = {("auth.login.rate_limited", "anonymous"): 1,
            ("access.denied.not_owner", f"member:{LEAD}"): SCAN_LEAD,
            ("input.unexpected_fields", f"member:{LEAD}"): 1,
            ("price.tampered", f"member:{LEAD}"): 1}
    for mid in OTHERS:
        rows[("access.denied.not_owner", f"member:{mid}")] = SCAN_OTHER
    return {"rows": rows, "actors_by_ip": {f"member:{m}" for m in (LEAD, *OTHERS)},
            "orders_tried": SCAN_LEAD + SCAN_OTHER * len(OTHERS), "orders_opened": 0}


class _Seq:
    def __init__(self) -> None:
        self.n = 0
        self.traces: list[str] = []

    def next(self) -> dict:
        self.n += 1
        t = f"{PREFIX}{self.n:04d}"
        self.traces.append(t)
        return {"X-Trace-Id": t}


def _login(c: httpx.Client, seq: _Seq, user: str, pw: str) -> tuple[int, str | None]:
    r = c.post("/sso/login", data={"username": user, "password": pw}, headers=seq.next())
    try:
        body = r.json()
    except ValueError:
        return r.status_code, None
    if body.get("code") == 200:
        return r.status_code, body["data"]["tokenHead"] + body["data"]["token"]
    return r.status_code, None


def run(echo=print) -> dict:
    """모의 침해를 한 번 재생한다. 시작 시각·추적 번호·정답 표를 .daon/drill-14-4.json에 남긴다"""
    reset_actors()
    orders = victims()
    start = datetime.now(timezone.utc) - timedelta(seconds=2)
    seq = _Seq()
    log: list[str] = []
    with httpx.Client(base_url=URLS["spring"]["portal"], timeout=10) as c:
        lead = username(LEAD)
        echo(f"1) {lead}: 틀린 비밀번호 {FAILS}번 → 6번째 시도")
        codes = [_login(c, seq, lead, WRONG)[0] for _ in range(FAILS)]
        sixth, _ = _login(c, seq, lead, WRONG)
        log.append(f"로그인 실패 {FAILS}번(HTTP {sorted(set(codes))}) → 6번째 HTTP {sixth}(속도 제한이면 429)")
        clear_counters()                                   # 랩 도구: 10분 기다리는 대신
        code, token = _login(c, seq, lead, PW)
        if not token:
            raise NotReady(f"{lead} 로그인 실패(HTTP {code}). 계정이 잠겼으면 make drill D=14-4가 처음에 풀어 줍니다. 시드 확인(make wipe)")
        h = {"Authorization": token}
        echo(f"2) {lead}: 남의 주문 {SCAN_LEAD}개 조회")
        opened = 0
        for oid in orders[:SCAN_LEAD]:
            r = c.get(f"/order/detail/{oid}", headers=h | seq.next())
            opened += int(r.status_code == 200 and (r.json().get("code") == 200))
        echo(f"3) {lead}: 화면이 보내지 않는 필드를 섞어 회원정보 수정")
        info = c.get("/sso/info", headers=h | seq.next()).json().get("data") or {}
        c.post("/sso/updateInfo", headers=h | seq.next(),
               json={"nickname": info.get("nickname") or "가짜고객17", "integration": 999999, "memberLevelId": 1})
        echo(f"4) {lead}: 가격을 1원으로 바꿔 장바구니 담기(담은 줄은 지움)")
        d = c.get(f"/product/detail/{PRODUCT}", headers=h | seq.next()).json().get("data") or {}
        p, skus = d.get("product") or {}, d.get("skuStockList") or []
        if p and skus:
            c.post("/cart/add", headers=h | seq.next(), json={
                "productId": PRODUCT, "productSkuId": skus[0]["id"], "quantity": 1, "price": 1, "productName": p.get("name"),
                "productSn": p.get("productSn"), "productCategoryId": p.get("productCategoryId"), "productSkuCode": skus[0].get("skuCode")})
            cart = c.get("/cart/list", headers=h | seq.next()).json().get("data") or []
            for item in [i for i in cart if i.get("productId") == PRODUCT]:
                c.post(f"/cart/delete?ids={item['id']}", headers=h | seq.next())
        rest = orders[SCAN_LEAD:]
        for k, mid in enumerate(OTHERS):
            echo(f"5) 같은 PC에서 {username(mid)}: 로그인 → 남의 주문 {SCAN_OTHER}개")
            _, tok = _login(c, seq, username(mid), PW)
            if not tok:
                raise NotReady(f"{username(mid)} 로그인 실패(시드 확인)")
            for oid in rest[k * SCAN_OTHER:(k + 1) * SCAN_OTHER]:
                r = c.get(f"/order/detail/{oid}", headers={"Authorization": tok} | seq.next())
                opened += int(r.status_code == 200 and (r.json().get("code") == 200))
    # 회원정보 수정이 포인트·등급을 바꾸지 않았는지(2-4) — 바뀌었으면 되돌린다(랩 데이터 보존)
    if one(f"SELECT integration FROM ums_member WHERE id={LEAD}") == "999999":
        mysql(f"UPDATE ums_member SET integration=3000, member_level_id=4 WHERE id={LEAD}")
        log.append("주의: 회원정보 수정이 포인트를 바꿨습니다(2-4 스위치 확인). 랩 값을 되돌렸습니다")
    clear_counters()
    out = {"start": start.strftime("%Y-%m-%dT%H:%M:%SZ"), "requests": seq.n, "first": seq.traces[0], "last": seq.traces[-1],
           "orders": orders, "opened": opened, "notes": log}
    DATA.mkdir(exist_ok=True)
    STATE.write_text(json.dumps(out, ensure_ascii=False, indent=2), "utf-8")
    echo(f"요청 {seq.n}개 · 추적 번호 {out['first']} ~ {out['last']} · 열린 남의 주문 {opened}건")
    for line in log:
        echo("   " + line)
    return out


def last() -> dict | None:
    try:
        return json.loads(STATE.read_text("utf-8"))
    except (FileNotFoundError, ValueError):
        return None


if __name__ == "__main__":
    run()
