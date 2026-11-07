"""2-3 관리자 기능이 열려 있다: 역할 검사 다시 짜기 · T02. 권한별 호출 행렬."""
import pytest

from replay import _logs
from replay import t02_admin_role as t02


def test_goods_staff_cannot_list_customers(stack):
    r = t02.run(stack)
    assert not r.vulnerable, "기대: 상품 관리자는 슈퍼 관리자용 고객 목록을 받지 못해야 합니다\n" + r.show()


def test_order_staff_cannot_list_customers(stack):
    status, body = t02.call("staff_order")
    assert status == 403 or body.get("code") == 403, f"기대: 주문 관리자도 고객 목록에서 403. 실제: HTTP {status} {body.get('message')!r}"


def test_denial_is_logged(stack):
    since = _logs.now()
    t02.call("staff_goods")
    e = _logs.wait_event("mall-admin", since, "access.denied.no_permission", "2-3", stack)
    assert e, "기대: 거부하면 mall-admin 로그에 SECURITY access.denied.no_permission(2-3) 한 줄이 남아야 합니다"
    assert e.get("actor") == "admin:staff_goods" and e.get("target") == f"path:{t02.PATH}", f"기대: actor·target이 ID만. 실제: {e}"


@pytest.mark.normal
def test_super_admin_lists_customers(stack):
    status, body = t02.call("admin")
    assert body.get("code") == 200 and isinstance(body.get("data"), list), \
        f"기대: 슈퍼 관리자(admin)는 고객 목록을 볼 수 있어야 합니다. 실제: HTTP {status} {body.get('message')!r}"


@pytest.mark.normal
@pytest.mark.parametrize("user,path", [("staff_goods", "/product/list"), ("staff_order", "/order/list")])
def test_staff_uses_own_menu(stack, user, path):
    status, body = t02.call(user, path)
    assert body.get("code") == 200, f"기대: {user}는 자기 메뉴 {path}를 그대로 씁니다. 실제: HTTP {status} {body.get('message')!r}"
