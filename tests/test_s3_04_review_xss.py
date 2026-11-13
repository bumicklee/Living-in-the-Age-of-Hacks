"""3-4 상품 후기가 관리자 화면에서 실행된다(저장형 XSS) · T07. 관리자 화면은 진짜 브라우저(Playwright)로 연다."""
import json
import uuid

import pytest

from replay import _browser, _logs
from replay import t07_stored_xss as t07


def test_review_script_not_executed_in_admin(stack):
    r = t07.run(stack)
    assert not r.vulnerable, "기대: 관리자 화면에서 후기 속 스크립트가 실행되지 않아야 합니다(탭 이름 그대로)\n" + r.show()


def test_review_tags_shown_as_text(stack):
    ref = uuid.uuid4().hex[:8]
    res = t07.write_review(f"태그가 섞인 후기 <b>굵게</b> <img src=\"x\"> {ref}")
    assert res.get("code") == 200, f"후기 쓰기 실패: {res.get('message')}"
    rid = res["data"]["id"]
    with _browser.admin_page(t07.VIEWER) as page:
        _browser.open_reviews(page, rid, settle=0.5)
        c = _browser.cell(page, f"review-content-{rid}")
    assert c["elements"] == 0 and "<b>굵게</b>" in c["text"], \
        f"기대: 관리자 화면은 후기를 꺾쇠까지 글자로 보여 줍니다. 실제: 요소 {c['tags']} 글자 {c['text'][:60]!r}"


@pytest.mark.normal
def test_normal_review_shown_as_is(stack):
    ref = uuid.uuid4().hex[:8]
    content = f"포장이 꼼꼼해요 😀\n두 번째 줄: 가격 대비 1<2 만족 {ref}"
    res = t07.write_review(content)
    assert res.get("code") == 200, f"기대: 정상 후기(줄바꿈·이모지·꺾쇠 글자)는 저장됩니다. 실제: {res.get('message')}"
    rid = res["data"]["id"]
    with _browser.admin_page(t07.VIEWER) as page:
        _browser.open_reviews(page, rid, settle=0.3)
        c = _browser.cell(page, f"review-content-{rid}")
    assert "😀" in c["text"] and "1<2 만족" in c["text"] and "두 번째 줄" in c["text"], \
        f"기대: 정상 후기가 그대로 보입니다. 실제: {c['text']!r}"


def test_too_long_review_rejected(stack):
    res = t07.write_review("가" * 501)
    assert res.get("code") != 200, "기대: 길이 상한(500자)을 넘는 후기는 거절됩니다"


def test_review_tags_logged(stack):
    since = _logs.now()
    res = t07.write_review(t07.PAYLOAD.format(ref=uuid.uuid4().hex[:8]))
    assert res.get("code") == 200, f"후기 쓰기 실패: {res.get('message')}"
    rid = res["data"]["id"]
    e = _logs.wait_event("mall-portal", since, "input.sanitized.html", "3-4", stack, target=f"review:{rid}")
    assert e, f"기대: 태그가 섞인 후기는 mall-portal 로그에 SECURITY input.sanitized.html(3-4)로 남아야 합니다(review:{rid})"
    line = json.dumps(e, ensure_ascii=False)
    assert "img" in (e.get("reason") or "") and "onerror" not in line and "document.title" not in line, \
        f"기대: 태그 이름만 남기고 본문은 남기지 않습니다. 실제: {e}"
    assert e.get("actor") == "member:101", f"기대: actor member:101(alice). 실제: {e.get('actor')}"
