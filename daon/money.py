"""섹션 10: 돈이 오가는 기능(랩 도구).

  make ledger-break [M=101]   원장(ums_integration_change_history) 없이 회원 포인트만 500 올린다(10-5 대사가 집어낼 불일치). 랩 전용
  make ledger-fix [M=101]     그 회원 포인트를 원장 합계로 되돌린다
  make reconcile              관리자 앱의 대사를 지금 실행(POST /daon/reconcile/run, 10-5 정답 뒤). 불일치 회원·주문 ID만 보인다
  make money-reset            재연이 바꾼 랩 값 되돌리기: alice·bob 포인트 1,000(원장 맞춤), 쿠폰 101·102 이력(alice·bob), 한정 상품 카운터, 랩 카운터(rl:*)

모두 127.0.0.1의 랩만 부른다. DB를 직접 고치는 일은 컨테이너 안 mysql(랩 전용 root)로만 한다.
"""
from __future__ import annotations

from replay import NotReady


def _mid(a) -> int:
    try:
        return int(a.member or 101)
    except ValueError:
        raise SystemExit("M은 회원 번호입니다(예: M=101)")


def cmd_ledger_break(a) -> None:
    from replay import _money as m
    mid = _mid(a)
    try:
        before = m.points(mid)
        m.ledger_break(mid)
        print(f"회원 {mid}: 포인트 {before} → {m.points(mid)} (원장 합계 {m.ledger_sum(mid)}, 원장 줄은 남기지 않음)")
    except NotReady as e:
        raise SystemExit(str(e))
    print(f"대사: make reconcile → member:{mid}가 나와야 합니다. 되돌리기: make ledger-fix M={mid}")


def cmd_ledger_fix(a) -> None:
    from replay import _money as m
    mid = _mid(a)
    try:
        m.ledger_fix(mid)
        print(f"회원 {mid}: 포인트를 원장 합계 {m.ledger_sum(mid)}로 되돌렸습니다")
    except NotReady as e:
        raise SystemExit(str(e))


def reconcile() -> dict:
    """관리자(admin) 토큰으로 대사 실행. 결과 data"""
    from replay._http import admin, admin_login
    with admin() as c:
        r = c.post("/daon/reconcile/run", headers={"Authorization": admin_login(c, "admin")})
    if r.status_code in (403, 404):
        raise NotReady(f"대사 API가 없습니다(HTTP {r.status_code}). 10-5 정답 뒤에 생깁니다(make goto L=10-5 AT=done)")
    body = r.json()
    if body.get("code") != 200:
        raise NotReady(f"대사 실행 실패: {body.get('message')}")
    return body.get("data") or {}


def cmd_reconcile(a) -> None:
    try:
        d = reconcile()
    except NotReady as e:
        raise SystemExit(str(e))
    mem, orders = d.get("members") or [], d.get("orders") or []
    print(f"대사 기준 시각 {d.get('cutoff')} · 포인트 불일치 {len(mem)}명 · 결제 불일치 {len(orders)}건")
    if mem:
        print("  회원: " + ", ".join(f"member:{i}" for i in mem[:30]) + (" …" if len(mem) > 30 else ""))
    if orders:
        print("  주문: " + ", ".join(f"order:{i}" for i in orders[:30]) + (" …" if len(orders) > 30 else ""))
    print("SECURITY 줄: make logs S=mall-admin | grep ledger.mismatch")


def cmd_money_reset(a) -> None:
    from replay import _money as m
    try:
        for user in ("alice", "bob"):
            m.set_points(user, 1000)
        for mid, coupon in ((101, 101), (102, 102)):
            m.reset_coupon(mid, coupon)
        for mid in (101, 102):
            m.reset_purchase(mid, 230)
        m.clear_counters()
    except NotReady as e:
        raise SystemExit(str(e))
    print("alice·bob 포인트 1,000(원장 맞춤), 쿠폰 101·102 이력, 한정 상품 230 카운터, 랩 카운터(rl:*)를 되돌렸습니다")


def register(add, stack_opt) -> None:
    add("ledger-break", cmd_ledger_break, (["member"], {"nargs": "?"}))
    add("ledger-fix", cmd_ledger_fix, (["member"], {"nargs": "?"}))
    add("reconcile", cmd_reconcile)
    add("money-reset", cmd_money_reset)
