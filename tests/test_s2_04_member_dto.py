"""2-4 회원정보 수정으로 등급과 포인트가 바뀐다(대량 할당) · T03."""
import pytest

from replay import _logs
from replay import t03_mass_assignment as t03
from replay._http import ACCOUNTS, portal, portal_login


def test_points_and_level_not_changed(stack):
    r = t03.run(stack)
    assert not r.vulnerable, "기대: 회원정보 수정 본문에 포인트·성장치·등급을 끼워 넣어도 바뀌지 않아야 합니다\n" + r.show()


def test_unexpected_fields_are_logged(stack):
    since = _logs.now()
    with portal() as c:
        token = portal_login(c, "alice")
        nick = t03.info(c, token).get("nickname") or "앨리스"
        t03.update(c, token, {"nickname": nick, "integration": t03.FAKE_POINTS})
        if t03.info(c, token).get("integration") == t03.FAKE_POINTS:       # 시작 지점이면 되돌린다
            t03.update(c, token, {"integration": 1000})
    e = _logs.wait_event("mall-portal", since, "input.unexpected_fields", "2-4", stack)
    assert e, "기대: 허용 밖 항목이 섞인 요청은 mall-portal 로그에 SECURITY input.unexpected_fields(2-4)로 남아야 합니다"
    assert "integration" in (e.get("reason") or "") and str(t03.FAKE_POINTS) not in str(e), \
        f"기대: 이벤트에는 항목 이름만 있고 보낸 값은 없어야 합니다. 실제: {e}"


@pytest.mark.normal
def test_alice_updates_nickname(stack):
    with portal() as c:
        token = portal_login(c, "alice")
        before = t03.info(c, token)
        body = t03.update(c, token, {"nickname": "앨리스-수정", "city": "서울"})
        after = t03.info(c, token)
        t03.update(c, token, {"nickname": before.get("nickname") or "앨리스", "city": before.get("city") or ""})
    assert body.get("code") == 200 and after.get("nickname") == "앨리스-수정" and after.get("city") == "서울", \
        f"기대: 닉네임·도시는 그대로 고칠 수 있어야 합니다. 실제: {body.get('message')!r} {after.get('nickname')!r}"
    assert after.get("id") == ACCOUNTS["alice"]["member_id"]
