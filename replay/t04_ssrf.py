"""T04 · 상품 이미지 URL 가져오기가 내부 주소로 요청(SSRF, A01). 수업 2-5(A2).

상품 담당 직원(staff_goods)이 「이미지 URL로 가져오기」에 랩 안의 내부 주소를 넣는다.
  1) http://127.0.0.1:8095/ops/config  관리자 앱 자신의 운영 도구(설정 값)
  2) http://mall-portal:8085/actuator/health  같은 망의 고객 앱
내부 응답이 하나라도 돌아오면 레드. 주소는 모두 이 PC의 랩 안이다.
"""
from __future__ import annotations

from replay import NotReady, Result
from replay._http import admin, admin_login

T = "T04"
TITLE = "이미지 URL 가져오기가 내부망을 연다(SSRF)"
LESSON = "2-5"
SOURCE = "[교육용 추가] 상품 이미지 URL 가져오기"
STACKS = ("spring",)

ATTACKER = "staff_goods"
INTERNAL = ["http://127.0.0.1:8095/ops/config", "http://mall-portal:8085/actuator/health"]
PARTNER_IMAGE = "http://img.partner-cdn.example/daon-sample.png"


def fetch(url: str, user: str = ATTACKER):
    with admin() as c:
        token = admin_login(c, user)
        return c.get("/product/image/fetch", params={"url": url}, headers={"Authorization": token})


def blocked_message(r) -> str | None:
    """막혔으면 CommonResult 실패 메시지, 내용이 왔으면 None."""
    if r.headers.get("content-type", "").startswith("application/json"):
        try:
            body = r.json()
        except ValueError:
            return None
        if isinstance(body, dict) and "code" in body and body.get("code") != 200:
            return body.get("message") or "실패"
    return None if r.content else "빈 응답"


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s2-05/express.diff로 확인).")
    ev, leaked = [], 0
    for url in INTERNAL:
        r = fetch(url)
        msg = blocked_message(r)
        if msg is None:
            leaked += 1
            shown = f"HTTP {r.status_code} · 본문 {len(r.content)}바이트(값은 출력하지 않음)"
            try:
                body = r.json()
                if isinstance(body, dict) and "jwt.secret" in body:
                    shown = f"운영 도구 설정 값 {len(body) - 1}개, 그중 관리자 토큰 서명값 포함(값은 출력하지 않음)"
            except ValueError:
                pass
            ev.append(f"요청: {url} → 내부 응답이 돌아옴: {shown}")
        else:
            ev.append(f"요청: {url} → 막힘: {msg}")
    if leaked:
        return Result(True, f"{ATTACKER}가 이미지 가져오기로 내부 주소 {leaked}곳의 응답을 받았다", ev)
    return Result(False, "이미지 가져오기는 내부 주소로 나가지 않는다", ev)
