"""3-미션 다른 목록·다른 글자 칸에도 같은 방어(수업 노트 lessons/s3-mission.md).

후기 화면의 「작성자」 칸(닉네임)도 고객이 정한 글이다. 2-4의 회원정보 수정으로 닉네임을 바꿀 수 있다.
"""
import contextlib
import json
import uuid

import pytest

from replay import _browser, _logs
from replay import t07_stored_xss as t07
from replay._http import portal, portal_login


@contextlib.contextmanager
def alice_nickname(nick: str):
    with portal() as c:
        token = portal_login(c, "alice")
        h = {"Authorization": token}
        before = c.get("/sso/info", headers=h).json()["data"].get("nickname") or "앨리스"
        r = c.post("/sso/updateInfo", json={"nickname": nick}, headers=h).json()
        assert r.get("code") == 200, f"닉네임을 바꾸지 못했습니다: {r.get('message')}"
        try:
            yield
        finally:
            c.post("/sso/updateInfo", json={"nickname": before}, headers=h)


def test_nickname_shown_as_text(stack):
    with alice_nickname("<b>앨리스</b>"):
        res = t07.write_review(f"닉네임 확인 후기 {uuid.uuid4().hex[:8]}")
    rid = res["data"]["id"]
    with _browser.admin_page(t07.VIEWER) as page:
        _browser.open_reviews(page, rid, settle=0.3)
        c = _browser.cell(page, f"review-nick-{rid}")
    assert "b" not in c["tags"] and "<b>앨리스</b>" in c["text"], \
        f"기대: 작성자 칸도 글자로만 출력(꺾쇠까지 보임). 실제: 요소 {c['tags']} 글자 {c['text']!r}"


def test_nickname_tags_logged(stack):
    since = _logs.now()
    with alice_nickname("<i>앨리스</i>"):
        res = t07.write_review(f"닉네임 확인 후기 {uuid.uuid4().hex[:8]}")
    rid = res["data"]["id"]
    e = _logs.wait_event("mall-portal", since, "input.sanitized.html", "3-mission", stack, target=f"review:{rid}")
    assert e, f"기대: 태그가 든 닉네임으로 쓴 후기는 SECURITY input.sanitized.html(3-mission)로 남습니다(review:{rid})"
    assert "i" in (e.get("reason") or "") and "앨리스" not in json.dumps(e, ensure_ascii=False), f"기대: 태그 이름만. 실제: {e}"


@pytest.mark.normal
def test_plain_nickname_shown(stack):
    res = t07.write_review(f"닉네임 확인 후기 {uuid.uuid4().hex[:8]}")
    rid = res["data"]["id"]
    with _browser.admin_page(t07.VIEWER) as page:
        _browser.open_reviews(page, rid, settle=0.3)
        c = _browser.cell(page, f"review-nick-{rid}")
    assert c["text"].strip(), f"기대: 작성자 칸에 닉네임이 보입니다. 실제: {c!r}"
