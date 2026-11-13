"""15-2 골라 복구: 사고 직전 백업에서 주문 몇 건의 「받는 사람」 칸만 되돌린다. 랩 전용(127.0.0.1, 가짜 값).

    python tools/restore_columns.py --actor admin:staff_night --since 2026-11-10T01:00:00Z          표만 보기(바꾸지 않음)
    python tools/restore_columns.py --orders 9204,9205 --before 2026-11-10T01:00:00Z               주문을 직접 고르기
    python tools/restore_columns.py --actor admin:staff_night --since … --apply --confirm 9204,9205  확인한 행만 되돌리기

순서(전체 되돌리기가 아니다)
  ① 대상: 감사 로그(8-3)의 change.order.receiver 가운데 그 actor가 사고 시각 뒤에 바꾼 주문(또는 --orders)
  ② 백업: 사고 시각보다 복구 시점이 앞선 잠금 버킷(A11) 백업 하나 → sha256 확인 → 망 없는 임시 컨테이너(daon/restore.py)
  ③ 표: 주문 · 백업 값 지문 · 지금 값 지문 · 사고 뒤 정상 변경(다른 사람이 그 뒤에 또 바꿨나, 감사 로그로) — 값 원문은 찍지 않는다
  ④ --apply: --confirm에 적은 주문만 관리자 API(POST /daon/incident/restore-columns, 권한 상승·사유 필수)로 쓴다.
     「사고 뒤 정상 변경」이 있는 주문을 --confirm에 넣으면 멈춘다(--force-row로만 넘긴다). 되돌린 쓰기도 감사 로그에 남는다
증거 보존(make evidence)을 먼저 했는지 묻는다: 복구는 데이터를 바꾸므로 바꾸기 전 상태를 먼저 남긴다(15-2 ②가 ⑥보다 앞).
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from daon import restore  # noqa: E402
from daon.config import DATA  # noqa: E402
from replay import NotReady  # noqa: E402
from replay import _approval as ap  # noqa: E402
from replay import _obs  # noqa: E402

ACTION = "change.order.receiver"


def _utc(ts: str) -> str:
    from daon.timeline import _utc as u
    return u(ts)


def targets(actor: str, since: str) -> tuple[list[int], dict[int, list[str]]]:
    """감사 로그에서 actor가 since 뒤에 바꾼 주문과, 그 뒤 다른 사람이 바꾼 기록(주문 → [actor …])"""
    mine: dict[int, str] = {}
    later: dict[int, list[str]] = {}
    for rec in _obs.audit_lines():
        if rec.get("action") != ACTION or rec.get("result") != "ok" or not str(rec.get("target", "")).startswith("order:"):
            continue
        if _utc(str(rec.get("ts", ""))) < _utc(since):
            continue
        oid = int(str(rec["target"]).split(":", 1)[1])
        if rec.get("actor") == actor:
            mine.setdefault(oid, str(rec.get("ts")))
        elif oid in mine and _utc(str(rec.get("ts"))) > _utc(mine[oid]):
            later.setdefault(oid, []).append(f"{rec.get('actor')}({rec.get('reason') or '사유 없음'})")
    return sorted(mine), later


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="골라 복구(15-2): 받는 사람 칸만, 확인한 행만")
    p.add_argument("--actor", help="침입 계정(예: admin:staff_night)")
    p.add_argument("--since", help="사고 시작 UTC(예: 2026-11-10T01:00:00Z). 백업은 이보다 앞선 것")
    p.add_argument("--orders", help="주문 번호를 직접(쉼표)")
    p.add_argument("--before", help="--orders와 함께: 이 시각 앞의 백업")
    p.add_argument("--apply", action="store_true", help="--confirm의 주문만 되돌린다")
    p.add_argument("--confirm", default="", help="사람이 확인한 주문 번호(쉼표)")
    p.add_argument("--force-row", default="", help="사고 뒤 정상 변경이 있어도 되돌릴 주문(쉼표, 드물게)")
    p.add_argument("--incident", default="capstone")
    p.add_argument("--reason", default="골라 복구(사고 직전 백업)")
    p.add_argument("--elevate", action="store_true", help="랩 편의: admin 권한 상승(incident-response)을 staff_lead 승인으로 켠다")
    a = p.parse_args(argv)
    try:
        if a.orders:
            ids = [int(x) for x in a.orders.split(",") if x.strip()]
            later: dict[int, list[str]] = {}
            since = a.before or a.since
        else:
            if not (a.actor and a.since):
                p.error("--actor와 --since(또는 --orders)")
            ids, later = targets(a.actor, a.since)
            since = a.since
        if not ids or not since:
            print("되돌릴 주문이 없습니다(감사 로그·시각 확인)")
            return 1
        b = restore.backup_before(since)
        print(f"① 대상 주문 {len(ids)}건 · ② 백업 {b['key']}(복구 시점 {b['snapshot_at']}, 사고 시작 {since} 앞)")
        old = restore.from_backup(b["key"], ids)
        now = restore.live(ids)
        print("③ 표(값 원문은 찍지 않는다. 지문 = sha256 앞 8자)")
        print(f"   {'주문':<6} {'백업 값':<9} {'지금 값':<9} {'바뀐 칸':<5} 사고 뒤 정상 변경")
        for oid in ids:
            diff = [c for c in restore.COLUMNS if (old.get(oid) or {}).get(c) != (now.get(oid) or {}).get(c)]
            print(f"   {oid:<6} {restore.fingerprint(old.get(oid)):<9} {restore.fingerprint(now.get(oid)):<9} {len(diff):<5} "
                  f"{', '.join(later.get(oid, [])) or '없음'}")
        if not a.apply:
            print("확인만 했습니다. 되돌리려면 --apply --confirm <주문,…> (사고 뒤 정상 변경이 있는 주문은 빼고)")
            return 0
        confirm = [int(x) for x in a.confirm.split(",") if x.strip()]
        force = {int(x) for x in a.force_row.split(",") if x.strip()}
        risky = [o for o in confirm if later.get(o) and o not in force]
        if risky:
            print(f"멈춤: 사고 뒤 정상 변경이 있는 주문 {risky}을 되돌리려 합니다. 빼거나 --force-row로 이유를 남기고 넘기세요")
            return 2
        if not (DATA / "incident" / a.incident / "evidence.sha256").exists():
            print(f"멈춤: 증거 보존이 먼저입니다(make evidence INCIDENT={a.incident}). 복구는 데이터를 바꿉니다")
            return 2
        if a.elevate:
            from replay import _incident
            _incident.elevate("admin")
        rows = [{"orderId": o, "values": old[o]} for o in confirm if o in old]
        from replay._http import admin
        with admin() as c:
            r = c.post("/daon/incident/restore-columns", headers={"Authorization": ap.must_login("admin")},
                       json={"incidentId": a.incident, "reason": a.reason, "rows": rows})
        if r.status_code != 200:
            print(f"④ 실패 HTTP {r.status_code}: {r.text[:200]} (권한 상승 incident-response가 켜져 있나요? 15-2 완료 지점인가요?)")
            return 1
        print(f"④ 되돌림: 주문 {r.json()['data']['count']}건(감사 로그 incident.restore.columns · incident.data.restored)")
        return 0
    except NotReady as e:
        print(f"[준비 안 됨] {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
