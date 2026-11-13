"""T33 · 파트너 토큰 범위 과다·수명 무제한·어디서든 사용(무기명), 대장·내보내기 경보 없음. 수업 12-1(사고)·12-2·12-3·12-4.

[교육용 추가] 원본 mall·EverShop에는 파트너 API가 없다. 다온마켓 파트너 API(DaonPartnerController)와 가짜 파트너 앱(stubs/partner).

PART(make replay T=33 PART=scope|reuse|export, 비우면 셋 다):
  scope : 배송 파트너(courier-01) 앱 스텁이 주문 목록 쪽을 끝까지 넘기고, 내보내기도 불러 본다.
          레드: 배정 주문(시드 81건)보다 많이 받음 또는 내보내기 200      그린: 배정 주문 수만큼만 + 내보내기 403(12-2)
  reuse : courier-01이 받은 토큰을 같은 PC 안의 **다른 클라이언트 설정**(인증서 없이, 그리고 다른 파트너의 인증서로)이 그대로 내민다.
          레드: 배정 주문이 똑같이 보임      그린: 401(12-3 인증서 바인딩)
  export: 분석 파트너(analytics-01, 내보내기 범위가 있는 정상 앱)가 내보내기를 20번(고정) 부른다(평소의 몇 배).
          레드: 내보내기 기록(partner.export)이 없거나 대장·경보 규칙이 없음   그린: 건수만 담긴 기록 + 대장 + 규칙(12-4). 경보 메일은 시험이 기다린다
안전 기준: 127.0.0.1의 랩만, 가짜 파트너·가짜 비밀값·랩 CA 인증서만. 토큰을 빼내는 방법은 다루지 않는다. 토큰·받는 사람 정보는 찍지 않는다.
"""
from __future__ import annotations

import os

from daon import partner as dp
from daon.config import ROOT
from replay import NotReady, Result
from replay import _logs
from replay import _partner as pt

T = "T33"
TITLE = "파트너 토큰 범위 과다·수명 무제한·어디서든 사용, 연동 앱 대장·내보내기 경보 없음"
LESSON = "12-2·12-3·12-4"
SOURCE = "[교육용 추가] DaonPartnerController(고정 키 X-Partner-Key, 범위 *, 만료 없음, 조회 조건에 파트너 없음) + 표 daon_partner_*"
STACKS = ("spring",)

EXPORT_CALLS = 20


def scope_scene() -> dict:
    a = pt.app(pt.COURIER)
    rows = a.all_orders()
    status, body = a.export()
    mine = pt.assigned(pt.COURIER)
    got = {int(r["id"]) for r in rows if "id" in r}
    return {"mode": a.mode, "got": len(got), "outside": len(got - mine), "assigned": len(mine),
            "export_status": status, "export_count": body.get("count"), "base": pt.base(), "calls": a.calls}


def reuse_scene() -> dict:
    tok = pt.token(pt.COURIER)
    legit = pt.get("/partner/orders?page=1&size=5", tok, cert_of=pt.COURIER).status_code
    no_cert = pt.get("/partner/orders?page=1&size=5", tok, cert_of=None).status_code
    other = pt.get("/partner/orders?page=1&size=5", tok, cert_of=pt.ANALYTICS).status_code
    return {"legit": legit, "no_cert": no_cert, "other_cert": other, "cnf": "cnf" in pt.payload(tok)}


def export_scene() -> dict:
    since = _logs.now()
    a = pt.app(pt.ANALYTICS)
    a.token("orders:read:assigned orders:export")
    rows, statuses = 0, []
    for _ in range(EXPORT_CALLS):
        status, body = a.export()
        statuses.append(status)
        rows += int(body.get("count") or 0)
    ev = _logs.wait_event("mall-portal", since, "partner.export", "12-4", timeout=8)
    ledger = dp.ledger()
    rule = (ROOT / "infra" / "observability" / "rules" / "partner.yaml").exists()
    return {"ok": statuses.count(200), "rows": rows, "event": ev, "ledger": ledger is not None, "rule": rule}


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s12-0*).")
    part = (os.environ.get("DAON_PART") or "").strip().lower()
    parts = [part] if part in ("scope", "reuse", "export") else ["scope", "reuse", "export"]
    ev, red = [], []
    if "scope" in parts:
        s = scope_scene()
        ev.append(f"scope: {s['base']} · 방식 {s['mode']} · 받은 주문 {s['got']}건(배정 {s['assigned']}건, 배정 밖 {s['outside']}건) · "
                  f"내보내기 HTTP {s['export_status']} {s['export_count'] if s['export_count'] is not None else ''}")
        if s["outside"] > 0 or s["export_status"] == 200:
            red.append("배정 주문만 필요한 배송 파트너가 전체 주문을 받고 내보내기까지 됨(범위 *, 조회 조건에 파트너 없음)")
    if "reuse" in parts:
        try:
            r = reuse_scene()
            ev.append(f"reuse: 정상 파트너 {r['legit']} · 같은 토큰을 인증서 없이 {r['no_cert']} · 다른 파트너 인증서로 {r['other_cert']} · 토큰 cnf {'있음' if r['cnf'] else '없음'}")
            if r["no_cert"] == 200 or r["other_cert"] == 200:
                red.append("토큰이 무기명: 같은 토큰을 다른 클라이언트 설정이 내밀어도 통함")
        except AssertionError as e:
            ev.append(f"reuse: {e}")
            red.append("토큰 발급이 없다(고정 키 = 누가 내밀든 통하는 무기명 키)")
    if "export" in parts:
        x = export_scene()
        ev.append(f"export: analytics-01 내보내기 {EXPORT_CALLS}번 중 {x['ok']}번 200, 합계 {x['rows']}행 · "
                  f"partner.export 기록 {'있음(count ' + str(x['event'].get('count')) + ')' if x['event'] else '없음'} · "
                  f"대장 {'있음' if x['ledger'] else '없음'} · 경보 규칙 {'있음' if x['rule'] else '없음'}")
        if not (x["event"] and x["ledger"] and x["rule"]):
            red.append("평소의 몇 배로 내보내도 기록·경보가 없고, 대장이 없어 담당자를 모름")
    if red:
        return Result(True, " / ".join(red), ev)
    return Result(False, "배정 주문만·인증서에 묶인 토큰·내보내기 기록과 대장이 있다", ev)
