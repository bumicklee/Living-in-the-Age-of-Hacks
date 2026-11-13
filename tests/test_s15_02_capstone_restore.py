"""15-2 캡스톤 ②: 대응과 복구 · 종합(4-8·5-2·6-2·13-3·13-4·13-6·14-2·14-4). 가상 시나리오, 127.0.0.1의 랩, 가짜 값.

수강생 사고(시나리오 MAIN, staff_night)는 건드리지 않는다. 이 시험은 따로 쓰는 시나리오 CHECK(가짜 직원 staff_drill, 다른 주문 20건)로
「사고 → 정답 순서의 대응 → 확인 → 되돌리기」를 처음부터 끝까지 돈다. 수강생이 손으로 한 대응은 make capstone PART=status가 본다.

시작 지점(15-1 완료): 직원 계정 격리·비밀번호 초기화·골라 복구 API가 없고(회원만 격리), 받는 사람을 바꾼 뒤의 환불도 한 사람으로 승인되고,
                    배송지 변경 경보 규칙이 없다 → 레드.
완료 지점: ① 새어 나간 비밀번호로 로그인 불가, 사고 때 받은 토큰 401 ② 서명키 회전 뒤 옛 키로 서명된 다른 직원 토큰도 401, 다시 로그인하면 정상
          ③ 주문 19건의 받는 사람 칸이 사고 직전 백업 값, 사고 뒤 정상 변경 1건·다른 주문은 그대로 ④ 승인 정책·경보 규칙
          ⑤ 시나리오를 다시 돌리면 2단계(로그인)에서 멈춤
"""
import pytest
import yaml

from daon import capstone as cap
from daon.config import ROOT
from replay import _approval as ap
from replay._http import admin, admin_login
from replay._money import mysql, one

SC = cap.CHECK
POLICY_ORDER = 9892          # 랩 도구가 만드는 가짜 주문(alice, 결제 50,000원, 받는 사람 칸은 가짜 값). 승인 정책 시험만 쓴다


@pytest.fixture(scope="module")
def responded():
    cap.reset(SC, echo=lambda *_: None)
    with admin() as c:
        other = admin_login(c, "staff_order")                        # 대응 전에 받은 다른 직원 토큰(서명키 회전 확인용)
    ans = cap.run(SC, echo=lambda *_: None, fresh=True)
    out = {"answer": ans, "other_token": other}
    try:
        out["response"] = cap.respond(SC, echo=lambda *_: None)
        out["error"] = None
    except AssertionError as e:
        out["error"] = str(e)
    yield out
    cap.reset(SC, echo=lambda *_: None)


def _ok(responded):
    assert not responded["error"], f"대응이 중간에 멈췄습니다: {responded['error']}"


def test_leaked_password_and_old_tokens_are_dead(responded):
    _ok(responded)
    with cap._client() as c:
        assert not cap._login(c, SC.actor, cap.SEED_PASSWORD, "capchk-t-001"), "① 기대: 새어 나간 비밀번호로 로그인 불가"
    import json
    old = json.loads((SC.dir / "attack-token.json").read_text("utf-8"))["token"]
    with admin() as c:
        code = c.get("/admin/info", headers={"Authorization": old}).status_code
    assert code == 401, f"① 기대: 사고 때 받은 토큰 401. 실제 HTTP {code}"


def test_key_rotation_kills_other_old_tokens_and_relogin_works(responded):
    _ok(responded)
    with admin() as c:
        old = c.get("/admin/info", headers={"Authorization": responded["other_token"]}).status_code
        fresh = admin_login(c, "staff_order")
        new = c.get("/admin/info", headers={"Authorization": fresh}).status_code
    assert old == 401 and new == 200, f"② 기대: 회전 전 토큰 401, 다시 로그인하면 200. 실제 {old}/{new}"


def test_only_confirmed_rows_restored(responded):
    _ok(responded)
    import json
    ans = responded["answer"]
    pre = json.loads((SC.dir / "pre.json").read_text("utf-8"))
    now = cap.receivers(ans["victims"])
    legit = ans["legit_after"]["order"]
    back = [o for o in ans["changed"] if o != legit and now[o] == pre["receivers"][str(o)]]
    assert len(back) == len(ans["changed"]) - 1, f"③ 기대: 확인한 {len(ans['changed']) - 1}건이 백업 값. 실제 {len(back)}건"
    assert now[legit] != pre["receivers"][str(legit)], "③ 기대: 사고 뒤 정상 변경(고객 요청) 1건은 덮어쓰지 않는다"
    assert cap.others_fingerprint(ans["victims"]) == pre["others"], "③ 기대: 다른 주문의 받는 사람 칸은 그대로"
    from replay import _obs
    acts = [r for r in _obs.audit_lines() if r.get("action") == "incident.restore.columns" and str(r.get("reason", "")).startswith(SC.incident)]
    assert len({r["target"] for r in acts}) >= len(back), f"③ 기대: 되돌린 쓰기도 감사 로그에 주문마다. 실제 {len(acts)}줄"


def test_rerun_stops_at_login(responded):
    _ok(responded)
    out = cap.rerun(SC, echo=lambda *_: None)
    assert out["stopped_at"] == 2, "⑤ 기대: 대응 뒤 시나리오를 다시 돌리면 2단계(로그인)에서 멈춤"


def _policy_order() -> int:
    if one(f"SELECT COUNT(*) FROM oms_order WHERE id={POLICY_ORDER}") != "1":
        mysql("INSERT INTO oms_order (id, member_id, order_sn, create_time, member_username, total_amount, pay_amount, freight_amount, "
              "promotion_amount, integration_amount, coupon_amount, discount_amount, pay_type, source_type, status, order_type, auto_confirm_day, "
              f"confirm_status, delete_status, receiver_name, receiver_phone, note) VALUES ({POLICY_ORDER}, 101, 'DAON20261115{POLICY_ORDER}', NOW(), 'alice', 50000, 50000, 0, "
              "0, 0, 0, 0, 1, 1, 3, 0, 15, 1, 0, '', '', '랩 도구: 15-2 승인 정책 시험용 가짜 주문')")
    return POLICY_ORDER


def test_refund_after_receiver_change_needs_two_people():
    ap.require_a10()
    ap.reset()
    oid = _policy_order()
    mysql(f"DELETE FROM oms_order_operate_history WHERE order_id={oid}")
    try:
        with admin() as c:
            tok = admin_login(c, "admin")
            r = c.post("/order/update/receiverInfo", headers={"Authorization": tok},
                       json={"orderId": oid, "status": 3, "receiverName": "가짜", "receiverPhone": "010-0000-0892", "receiverPostCode": "00000",
                             "receiverProvince": "가짜도", "receiverCity": "가짜시", "receiverRegion": "가짜구", "receiverDetailAddress": "가짜로 1"})
        assert r.status_code == 200, f"준비: 받는 사람 수정 200. 실제 {r.status_code}"
        rid = ap.new_return(oid)
        got = ap.request_refund("staff_order", rid, 10000)
        assert got.get("status") == "PENDING_SECOND", f"④ 기대: 받는 사람을 바꾼 뒤 72시간 안 환불은 금액이 작아도 두 사람 승인. 실제 {got.get('status')}"
    finally:
        mysql(f"DELETE FROM oms_order_operate_history WHERE order_id={oid}")
        ap.reset()


def test_receiver_change_alert_rule():
    f = ROOT / "infra" / "observability" / "rules" / "change.yaml"
    assert f.exists(), "④ 기대: 배송지 변경 경보 규칙 infra/observability/rules/change.yaml(손으로 쓴 규칙, 15-1 회고의 할 일)"
    rules = [r for g in yaml.safe_load(f.read_text("utf-8"))["groups"] for r in g["rules"]]
    exprs = " ".join(d["model"].get("expr", "") for r in rules for d in r["data"] if d.get("model"))
    assert "change.order.receiver" in exprs and "by (actor)" in exprs, "④ 기대: 감사 로그 change.order.receiver를 actor별로 센다"


@pytest.mark.normal
def test_other_staff_work_as_usual():
    with admin() as c:
        tok = admin_login(c, "staff_order")
        r = c.get("/order/list", params={"pageNum": 1, "pageSize": 5}, headers={"Authorization": tok})
    assert r.status_code == 200 and (r.json() or {}).get("code") == 200, f"기대: 다른 직원(staff_order)은 평소처럼 주문 목록을 본다. 실제 {r.status_code}"
