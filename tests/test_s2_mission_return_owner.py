"""2-미션 배송지·반품 신청에도 같은 소유권 검사(수업 노트 lessons/s2-mission.md)."""
import pytest

from replay import _logs
from replay._http import ACCOUNTS, portal, portal_login

ALICE_ORDER = ACCOUNTS["alice"]["orders"][1]    # 9002, 배송 완료


def return_apply(user: str, order_id: int) -> dict:
    body = {"orderId": order_id, "productId": 27, "orderSn": "FAKE-SN", "memberUsername": user,
            "returnName": "테스트", "returnPhone": "010-0000-0000", "productCount": 1,
            "reason": "단순 변심", "description": "랩 시험(가짜 신청)"}
    with portal() as c:
        token = portal_login(c, user)
        return c.post("/returnApply/create", json=body, headers={"Authorization": token}).json()


def test_bob_cannot_request_return_for_alice_order(stack):
    body = return_apply("bob", ALICE_ORDER)
    assert body.get("code") != 200, f"기대: bob은 alice의 주문 {ALICE_ORDER}로 반품을 신청할 수 없어야 합니다. 실제: {body}"


def test_denied_return_is_logged(stack):
    since = _logs.now()
    return_apply("bob", ALICE_ORDER)
    e = _logs.wait_event("mall-portal", since, "access.denied.not_owner", "2-mission", stack)
    assert e and e.get("target") == f"order:{ALICE_ORDER}", \
        "기대: mall-portal 로그에 SECURITY access.denied.not_owner(2-mission), target=order:9002가 남아야 합니다"


@pytest.mark.normal
def test_alice_requests_return_for_own_order(stack):
    body = return_apply("alice", ALICE_ORDER)
    assert body.get("code") == 200, f"기대: alice는 자기 주문으로 반품을 신청할 수 있어야 합니다. 실제: {body}"


@pytest.mark.normal
def test_address_already_checks_owner(stack):
    """배송지는 원본이 이미 모든 조회·수정·삭제 조건에 회원 ID를 넣는다. 그대로 지켜지는지 확인만 한다."""
    addr = {"name": "김앨리스", "phoneNumber": "010-0000-0101", "defaultStatus": 0, "postCode": "00001",
            "province": "서울특별시", "city": "가상구", "region": "다온동", "detailAddress": "랩 시험 주소"}
    with portal() as a, portal() as b:
        ta, tb = portal_login(a, "alice"), portal_login(b, "bob")
        a.post("/member/address/add", json=addr, headers={"Authorization": ta})
        mine = [x for x in a.get("/member/address/list", headers={"Authorization": ta}).json()["data"]
                if x.get("detailAddress") == "랩 시험 주소"]
        assert mine, "alice가 배송지를 추가하지 못했습니다"
        aid = mine[-1]["id"]
        try:
            got = b.get(f"/member/address/{aid}", headers={"Authorization": tb}).json()
            upd = b.post(f"/member/address/update/{aid}", json=addr | {"detailAddress": "바뀜"}, headers={"Authorization": tb}).json()
            dele = b.post(f"/member/address/delete/{aid}", headers={"Authorization": tb}).json()
            assert not got.get("data"), "기대: bob은 alice의 배송지를 볼 수 없습니다"
            assert upd.get("code") != 200 and dele.get("code") != 200, "기대: bob은 alice의 배송지를 고치거나 지울 수 없습니다"
        finally:
            for x in a.get("/member/address/list", headers={"Authorization": ta}).json()["data"]:
                if x.get("detailAddress") in ("랩 시험 주소", "바뀜"):
                    a.post(f"/member/address/delete/{x['id']}", headers={"Authorization": ta})
