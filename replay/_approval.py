"""섹션 13 도우미: 고액 환불 승인(A10 승인 서비스)과 운영 접근. 재연(T34·T35)·시험·랩 도구(make approval-reset)가 같이 쓴다.

안전 기준(수업 상세 계획 13장): 127.0.0.1의 다온마켓과 랩 스텁만 부른다. 계정·주문·금액은 모두 가짜다.
  - 직원 계정은 시드의 가짜 직원(staff_order·staff_finance·staff_lead)과 [회사 설정] 공용 계정 daon_ops, 원본 데모 계정(macro)만.
  - 환불은 가짜 결제사(payment-stub)에 기록만 남는다. 진짜 돈·카드·계좌는 없다.
  - 서명 공격·사회공학 재연은 하지 않는다. 「위조 토큰」은 이 PC에서 그때 만든 임시 키로 서명한 가짜 토큰이고(어디에도 저장하지 않는다),
    「금액을 바꾼 토큰」은 진짜 토큰의 내용 칸 글자만 바꾼 것이다. 둘 다 랩의 실행기가 거절하는지만 본다(방어 확인).
  - DB를 직접 고치는 일(시험용 반품 신청 만들기, 승인 기록 비우기, 가짜 고액 주문)은 랩 도구로만 한다(컨테이너 안 mysql, 랩 전용 root).
"""
from __future__ import annotations

import base64
import json
import re
import time

import httpx

from daon import stack as st
from daon.config import HOST, URLS, port
from replay import NotReady
from replay._http import ADMINS, admin, new_trace_id
from replay._money import clear_counters, mysql, one, table_exists

APPROVAL = URLS["spring"]["approval"]
PAY = f"http://{HOST}:{port('DAON_PAYMENT_PORT', 9101)}"
MAILPIT = f"http://{HOST}:{port('DAON_MAILPIT_PORT', 8025)}"
MARK = "랩 시험(13장)"                    # 시험·재연이 만든 반품 신청 표시(description). reset()이 이것만 지운다
ORDER = 9001                              # alice의 주문, 결제 39,000원(시드)
SMALL_ORDERS = (9001, 9002, 9003)         # 서킷 브레이커 시험이 나눠 쓰는 주문(같은 주문 하루 3번 한도)
HIGH_ORDER = 9890                         # 랩 도구가 만드는 가짜 고액 주문(결제 450,000원, 13-4)
HIGH_PAY = 450000
HIGH_VALUE = 300000                       # policy.yml refund.high_value(가짜 기준)


def a10_on() -> bool:
    return "a10" in st.arch_layers()


def require_a10() -> None:
    if not a10_on():
        raise NotReady("A10(승인 서비스)이 꺼져 있습니다. 13-3 완료 지점에서: make goto L=13-3 AT=done && make up STACK=spring")


# ── 랩 도구(DB) ──
def ensure_high_order() -> int:
    """가짜 고액 주문 하나(멱등). 받는 사람 칸은 비워 둔다(개인정보 없음)"""
    if one(f"SELECT COUNT(*) FROM oms_order WHERE id={HIGH_ORDER}") != "1":
        mysql("INSERT INTO oms_order (id, member_id, order_sn, create_time, member_username, total_amount, pay_amount, freight_amount, "
              "promotion_amount, integration_amount, coupon_amount, discount_amount, pay_type, source_type, status, order_type, auto_confirm_day, "
              f"confirm_status, delete_status, receiver_name, receiver_phone, note) VALUES ({HIGH_ORDER}, 101, 'DAON20261113{HIGH_ORDER}', NOW(), 'alice', {HIGH_PAY}, {HIGH_PAY}, 0, "
              "0, 0, 0, 0, 1, 1, 3, 0, 15, 1, 0, '', '', '랩 도구: 13-4 고액 환불 시험용 가짜 주문')")
    return HIGH_ORDER


def new_return(order_id: int = ORDER) -> int:
    """처리 대기(0) 반품 신청 하나를 만든다(랩 도구). 받는 사람 이름·전화는 넣지 않는다"""
    sn = one(f"SELECT order_sn FROM oms_order WHERE id={int(order_id)}")
    if not sn:
        raise NotReady(f"주문 {order_id}이 없습니다(시드: make wipe 후 make up)")
    mysql("INSERT INTO oms_order_return_apply (order_id, order_sn, create_time, member_username, return_name, status, product_name, "
          f"product_count, reason, description) VALUES ({int(order_id)}, '{sn}', NOW(), 'alice', '랩 시험', 0, '다온 가짜 상품', 1, '단순 변심', '{MARK}')")
    return int(one(f"SELECT MAX(id) FROM oms_order_return_apply WHERE description='{MARK}'"))


def return_row(rid: int) -> dict:
    out = mysql(f"SELECT status, IFNULL(handle_man,''), IFNULL(return_amount,'') FROM oms_order_return_apply WHERE id={int(rid)}").split("\t")
    return {"status": int(out[0]) if out and out[0] != "" else None, "handle_man": out[1] if len(out) > 1 else "",
            "return_amount": out[2] if len(out) > 2 else ""}


def reset() -> None:
    """랩 도구(make approval-reset): 승인 기록·브레이커·시험용 반품 신청·권한 상승을 처음 상태로. 시드 반품·주문은 건드리지 않는다"""
    if table_exists("daon_approval_request"):
        mysql("DELETE FROM daon_approval_request")
    if table_exists("daon_approval_breaker"):
        mysql("UPDATE daon_approval_breaker SET state='CLOSED', opened_at=NULL, reason=NULL, close_vote=NULL, close_vote_at=NULL WHERE id=1")
    mysql(f"DELETE FROM oms_order_return_apply WHERE description='{MARK}'")
    st.redis_cli("EVAL", "local n=0 for _,k in ipairs(redis.call('KEYS', ARGV[1])) do redis.call('DEL', k) n=n+1 end return n",
                 "0", "mall:ops:elevation:*")
    clear_counters()                      # 닫힌 계정 로그인 시도(13-6)가 4-3 로그인 실패 제한에 쌓이지 않게


# ── 가짜 결제사 환불 목록 ──
def refunds(rid: int | None = None) -> list[dict]:
    try:
        r = httpx.get(f"{PAY}/v1/refunds", params={"return_id": rid} if rid else None, timeout=5)
    except httpx.TransportError as e:
        raise NotReady(f"가짜 결제사(payment-stub, {PAY})에 닿지 않습니다({e.__class__.__name__}). make up STACK=spring")
    if r.status_code == 404:
        raise NotReady("가짜 결제사에 환불 목록(/v1/refunds)이 없습니다. 스텁을 다시 띄우세요(make up)")
    return r.json()


def wait_refunds(rid: int, n: int = 1, timeout: float = 25.0) -> list[dict]:
    t0 = time.time()
    while True:
        rows = refunds(rid)
        if len(rows) >= n or time.time() - t0 > timeout:
            return rows
        time.sleep(1)


# ── 관리자 앱 ──
def login(user: str) -> str:
    with admin() as c:
        try:
            r = c.post("/admin/login", json={"username": user, "password": ADMINS[user]["password"]})
        except httpx.TransportError as e:
            raise NotReady(f"mall-admin에 닿지 않습니다(make up STACK=spring): {e.__class__.__name__}")
    body = r.json() if r.headers.get("content-type", "").startswith("application/json") else {}
    if body.get("code") != 200:
        return ""
    return body["data"]["tokenHead"] + body["data"]["token"]


def login_message(user: str) -> tuple[bool, str]:
    """로그인 결과와 화면 문구(비밀번호는 찍지 않는다)"""
    with admin() as c:
        r = c.post("/admin/login", json={"username": user, "password": ADMINS[user]["password"]})
    try:
        body = r.json()
    except ValueError:
        return False, f"HTTP {r.status_code}"
    return body.get("code") == 200, str(body.get("message", ""))


def must_login(user: str) -> str:
    tok = login(user)
    if not tok:
        raise NotReady(f"{user} 관리자 로그인 실패(13-1 이후 직원 시드가 있어야 함: make goto L=13-3 && make up)")
    return tok


def confirm_return(user: str, rid: int, amount: int, handle_man: str = "admin", trace: str | None = None) -> httpx.Response:
    """관리자 화면의 「반품 확인」과 같은 요청. 금액·처리자 칸은 요청 본문이 정한다(F48)"""
    tok = must_login(user)
    with admin() as c:
        return c.post(f"/returnApply/update/status/{rid}", headers={"Authorization": tok, "X-Trace-Id": trace or new_trace_id("t34")},
                      json={"id": rid, "status": 1, "returnAmount": amount, "handleMan": handle_man, "handleNote": MARK, "companyAddressId": 1})


def execute(user: str, rid: int, token: str | None) -> httpx.Response:
    tok = must_login(user)
    with admin() as c:
        return c.post("/daon/refund/execute", headers={"Authorization": tok}, json={"returnId": rid, "token": token})


# ── 승인 서비스(A10) ──
def ap(method: str, path: str, user: str | None = None, **kw) -> httpx.Response:
    headers = kw.pop("headers", {})
    if user:
        headers["Authorization"] = must_login(user)
    try:
        return httpx.request(method, APPROVAL + path, headers=headers, timeout=10, **kw)
    except httpx.TransportError as e:
        raise NotReady(f"승인 서비스({APPROVAL})에 닿지 않습니다({e.__class__.__name__}). 13-3 완료 지점에서 make up")


def request_refund(user: str, rid: int, amount: int) -> dict:
    r = ap("POST", "/approvals/refund", user, json={"returnId": rid, "amount": amount})
    return r.json() if r.status_code < 500 else {"status": f"HTTP {r.status_code}"}


def latest_approval(rid: int) -> dict | None:
    if not table_exists("daon_approval_request"):
        return None
    row = mysql(f"SELECT id, status, IFNULL(approved_by,'') FROM daon_approval_request WHERE return_id={int(rid)} ORDER BY created_at DESC LIMIT 1")
    if not row:
        return None
    a = row.split("\t")
    return {"id": a[0], "status": a[1], "approved_by": a[2] if len(a) > 2 else ""}


def confirm_code(approval_id: str, timeout: float = 10.0) -> str | None:
    """Mailpit(가짜 메일)에서 이 승인 요청의 확인 번호를 읽는다(재무 담당이 메일함에서 읽는 일)"""
    t0 = time.time()
    with httpx.Client(base_url=MAILPIT, timeout=5) as m:
        while time.time() - t0 < timeout:
            rows = m.get("/api/v1/search", params={"query": approval_id, "limit": 5}).json().get("messages") or []
            for row in rows:
                text = m.get(f"/api/v1/message/{row['ID']}").json().get("Text", "")
                found = re.search(r"확인 번호: ([0-9]{6})", text)
                if found and approval_id in text:
                    return found.group(1)
            time.sleep(0.5)
    return None


# ── 토큰 다루기(방어 확인용) ──
def _b64d(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def _b64e(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def payload(token: str) -> dict:
    return json.loads(_b64d(token.split(".")[1]))


def tamper_amount(token: str, amount: int) -> str:
    """진짜 토큰의 내용 칸 금액 글자만 바꾼다(서명은 그대로) → 실행기가 서명 불일치로 거절해야 한다"""
    h, p, s = token.split(".")
    body = json.loads(_b64d(p))
    body["amount"] = str(amount)
    return ".".join([h, _b64e(json.dumps(body, separators=(",", ":")).encode()), s])


def forged(token: str) -> str:
    """같은 모양(헤더·내용)의 토큰을 이 PC에서 그때 만든 임시 키(P-256)로 서명한다. 키는 메모리에만 있고 저장하지 않는다.
    서명 계산은 표준 라이브러리로만(교과서 ECDSA, 랩 시험용. 실제 서비스에서 이런 코드를 쓰지 않는다)"""
    import hashlib
    import secrets
    h, p, _ = token.split(".")
    body = json.loads(_b64d(p))
    body["jti"] = "f" * 32                            # 처음 보는 1회용 번호(재사용 거절과 섞이지 않게)
    p2 = _b64e(json.dumps(body, separators=(",", ":")).encode())
    d = secrets.randbelow(_N - 1) + 1
    z = int.from_bytes(hashlib.sha256(f"{h}.{p2}".encode()).digest(), "big")
    while True:
        k = secrets.randbelow(_N - 1) + 1
        r = _mul(k, _G)[0] % _N
        sig = pow(k, -1, _N) * (z + r * d) % _N
        if r and sig:
            break
    return f"{h}.{p2}.{_b64e(r.to_bytes(32, 'big') + sig.to_bytes(32, 'big'))}"


# P-256 곡선 상수(FIPS 186-4)와 점 연산(아핀 좌표, 느려도 한 번이면 충분하다)
_P = 2 ** 256 - 2 ** 224 + 2 ** 192 + 2 ** 96 - 1
_N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
_G = (0x6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296,
      0x4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5)


def _add(a, b):
    if a is None:
        return b
    if b is None:
        return a
    if a[0] == b[0] and (a[1] + b[1]) % _P == 0:
        return None
    if a == b:
        m = (3 * a[0] * a[0] - 3) * pow(2 * a[1], -1, _P) % _P
    else:
        m = (b[1] - a[1]) * pow(b[0] - a[0], -1, _P) % _P
    x = (m * m - a[0] - b[0]) % _P
    return x, (m * (a[0] - x) - a[1]) % _P


def _mul(k, pt):
    out = None
    while k:
        if k & 1:
            out = _add(out, pt)
        pt = _add(pt, pt)
        k >>= 1
    return out


# ── 운영 접근(13-6) ──
def role_update_as(user: str, admin_id: int = 102, role_ids: str = "2") -> httpx.Response:
    """고위험 기능 한 번: 관리자 역할 다시 쓰기(같은 값으로. staff_order = 주문 관리자 2 → 2, 바뀌는 것 없음)"""
    tok = must_login(user)
    with admin() as c:
        return c.post("/admin/role/update", headers={"Authorization": tok}, params={"adminId": admin_id, "roleIds": role_ids})


def super_admin_count() -> int:
    """닫히지 않은 계정 중 슈퍼 관리자 역할(5)을 가진 수"""
    return int(one("SELECT COUNT(*) FROM ums_admin a JOIN ums_admin_role_relation r ON r.admin_id = a.id WHERE r.role_id = 5 AND a.status = 1") or 0)


def elevation_request(user: str, minutes: int = 30, group: str = "admin-accounts") -> dict:
    """관리자 화면의 「권한 상승 요청」(관리자 앱 → 승인 서비스). 사유는 가짜 티켓 번호"""
    tok = must_login(user)
    with admin() as c:
        r = c.post("/daon/elevation/request", headers={"Authorization": tok},
                   json={"reason": "역할 정리(가짜 티켓 OPS-13)", "group": group, "minutes": minutes})
    try:
        return {"http": r.status_code, **(r.json().get("data") or {})}
    except ValueError:
        return {"http": r.status_code}


def elevation_activate(user: str, approval_id: str) -> int:
    tok = must_login(user)
    with admin() as c:
        return c.post(f"/daon/elevation/{approval_id}/activate", headers={"Authorization": tok}).status_code


def skip_minutes(minutes: int) -> None:
    """랩 시계(LabClock, A13)를 민다. 관리자 앱이 보는 시각만 바뀐다(승인 서비스는 실제 시각). 끝나면 reset_clock()"""
    st.redis_cli("SET", "lab:clock:offset", str(minutes * 60 * 1000))
    time.sleep(1.5)


def reset_clock() -> None:
    st.redis_cli("DEL", "lab:clock:offset")
    time.sleep(1.5)
