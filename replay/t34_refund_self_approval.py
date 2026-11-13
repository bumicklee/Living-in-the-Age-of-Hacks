"""T34 · 고액 환불을 관리자 앱이 혼자 승인한다(금액·처리자를 요청이 정함, 한 사람이 바로 실행). 수업 13-1(예고)·13-3·13-4.

[교육용 추가] 원본 mall은 반품을 확인한 뒤 돈을 돌려주는 코드가 없다. 다온마켓은 「확인 = 그 자리에서 가짜 결제사 환불」(DaonRefundExecutor)을 붙였다.
[원본 확인됨·정적] 반품 확인 updateStatus()가 환불 금액(returnAmount)·처리자(handleMan)를 요청 본문에서 받는다(점검표 F48).

PART(make replay T=34 PART=remaining|high-value, 비우면 둘 다):
  remaining  : 직원 staff_order가 결제 39,000원인 가짜 주문(9001)의 반품 신청을 확인하며 요청 본문에 returnAmount=3,000,000·handleMan=admin을 넣는다.
               레드: 가짜 결제사에 결제 금액보다 큰 환불이 나감 또는 처리자 칸이 admin    그린: 승인 서비스가 거부(남은 결제 금액 초과)·환불 0건·처리자 = staff_order(13-3)
  high-value : 같은 직원이 가짜 고액 주문(450,000원)에 400,000원 환불을 확인하고, 두 번째 승인까지 스스로 해 본다.
               레드: 한 사람만으로 고액 환불이 바로 나감    그린: 두 번째 승인 대기·스스로 승인 거부(approval.denied.self_approval)·환불 0건(13-4)
안전 기준: 127.0.0.1의 랩만, 가짜 직원·가짜 주문·가짜 금액. 가짜 결제사에는 기록만 남는다. 재연이 만든 반품 신청은 표시(랩 시험(13장))가 있고 make approval-reset이 지운다.
"""
from __future__ import annotations

import os
import time

from replay import NotReady, Result
from replay import _approval as ap
from replay._money import mysql

T = "T34"
TITLE = "고액 환불을 관리자 앱이 혼자 승인(금액·처리자를 요청이 정함, 한 사람이 바로 실행)"
LESSON = "13-3·13-4"
SOURCE = "[교육용 추가] DaonRefundExecutor(확인 즉시 가짜 결제사 환불) + [원본 확인됨·정적] updateStatus()가 returnAmount·handleMan을 요청에서 받음(F48)"
STACKS = ("spring",)

ASK = 3_000_000          # 결제 39,000원 주문에 요청하는 환불(가짜)
HIGH_ASK = 400_000       # 가짜 고액 주문(450,000원)에 요청하는 환불. 남은 결제 금액 안이라 13-3 정책은 통과한다


def _cleanup(rid: int) -> None:
    """재연이 남긴 승인 기록(잡아 둔 금액)을 지운다. 다음 재연·시험의 남은 결제 금액이 줄지 않게(랩 도구)"""
    if ap.a10_on():
        mysql(f"DELETE FROM daon_approval_request WHERE return_id={int(rid)}")


def remaining_scene() -> dict:
    rid = ap.new_return(ap.ORDER)
    r = ap.confirm_return("staff_order", rid, ASK, handle_man="admin")
    time.sleep(2)
    rows = ap.refunds(rid)
    row = ap.return_row(rid)
    a = ap.latest_approval(rid) if ap.a10_on() else None
    _cleanup(rid)
    return {"rid": rid, "http": r.status_code, "refunds": rows, "row": row, "approval": a}


def high_scene() -> dict:
    ap.ensure_high_order()
    rid = ap.new_return(ap.HIGH_ORDER)
    r = ap.confirm_return("staff_order", rid, HIGH_ASK, handle_man="staff_order")
    time.sleep(2)
    a = ap.latest_approval(rid) if ap.a10_on() else None
    self_status = None
    if a and a["status"] == "PENDING_SECOND":
        self_status = ap.ap("POST", f"/approvals/{a['id']}/approve", "staff_order", json={"code": "000000"}).status_code
        time.sleep(1)
    rows = ap.refunds(rid)
    a2 = ap.latest_approval(rid) if ap.a10_on() else None
    _cleanup(rid)
    return {"rid": rid, "http": r.status_code, "refunds": rows, "approval": a2, "self": self_status}


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s13-0*).")
    part = (os.environ.get("DAON_PART") or os.environ.get("CASE") or "").strip().lower()
    parts = [part] if part in ("remaining", "high-value") else ["remaining", "high-value"]
    ev, red = [], []
    if "remaining" in parts:
        s = remaining_scene()
        paid = sum(int(x["amount"]) for x in s["refunds"])
        ev.append(f"remaining: 반품 신청 return:{s['rid']}(주문 {ap.ORDER}, 결제 39,000원)에 요청 {ASK:,}원·처리자 admin → HTTP {s['http']} · "
                  f"가짜 결제사 환불 {len(s['refunds'])}건 {paid:,}원 · 저장된 처리자 {s['row']['handle_man'] or '(없음)'}"
                  + (f" · 승인 서비스 {s['approval']['status']}" if s["approval"] else " · 승인 서비스 없음(A10 전)"))
        if paid > 39000:
            red.append(f"결제 39,000원 주문에 {paid:,}원 환불이 실행됨(금액을 요청이 정함)")
        if s["row"]["handle_man"] == "admin":
            red.append("처리자 기록이 실제 로그인(staff_order)과 다름(handleMan을 요청이 정함)")
    if "high-value" in parts:
        h = high_scene()
        paid = sum(int(x["amount"]) for x in h["refunds"])
        ev.append(f"high-value: return:{h['rid']}(가짜 고액 주문 {ap.HIGH_ORDER}, 결제 {ap.HIGH_PAY:,}원)에 {HIGH_ASK:,}원 → HTTP {h['http']} · "
                  f"승인 서비스 {h['approval']['status'] if h['approval'] else '없음(A10 전)'}"
                  + (f" · 같은 사람이 두 번째 승인 시도 HTTP {h['self']}" if h["self"] is not None else "")
                  + f" · 가짜 결제사 환불 {len(h['refunds'])}건 {paid:,}원")
        if paid > 0:
            red.append(f"한 사람(staff_order)만으로 고액 {paid:,}원 환불이 바로 실행됨(두 번째 사람·다른 경로·지연 없음)")
    if red:
        return Result(True, " / ".join(red), ev)
    return Result(False, "승인 서비스가 원장을 읽고 판단·고액은 두 번째 사람과 지연 실행", ev)
