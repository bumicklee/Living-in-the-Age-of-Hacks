"""섹션 13 랩 도구: 승인 서비스(A10)·고액 환불·운영 권한 상승.

    make approval-status     승인 서비스 상태·출금 서킷 브레이커·최근 승인 요청(상태별 건수)·가짜 결제사 환불 건수
    make approval-reset      랩 전용: 승인 기록·브레이커·시험용 반품 신청(표시 「랩 시험(13장)」)·권한 상승·로그인 실패 카운터를 처음 상태로

이 PC의 랩(127.0.0.1)만 부른다. 승인 토큰·확인 번호는 화면에 찍지 않는다.
"""
from __future__ import annotations

import httpx

from daon import stack as st
from daon.config import URLS


def cmd_approval_status(a) -> None:
    from replay import _approval as ap
    from replay._money import mysql, table_exists
    if not ap.a10_on():
        print("A10(승인 서비스)이 꺼져 있습니다(infra/arch.txt에 a10 없음). 13-3에서 켭니다.")
        return
    try:
        h = httpx.get(URLS["spring"]["approval"] + "/health", timeout=3).status_code
        b = httpx.get(URLS["spring"]["approval"] + "/breaker", timeout=3).json() if h == 200 else {}
    except httpx.TransportError:
        h, b = "닿지 않음", {}
    print(f"승인 서비스 {URLS['spring']['approval']} · health {h} · 출금 서킷 브레이커 {b.get('state', '?')} {b.get('reason') or ''}")
    if table_exists("daon_approval_request"):
        rows = mysql("SELECT kind, status, COUNT(*) FROM daon_approval_request GROUP BY kind, status ORDER BY kind, status")
        print("승인 요청(종류·상태·건수):")
        for line in rows.splitlines():
            print("  " + "  ".join(line.split("\t")))
    try:
        print(f"가짜 결제사 환불 {len(ap.refunds())}건 (목록: {ap.PAY}/_stub/refunds)")
    except Exception as e:  # noqa: BLE001
        print(f"가짜 결제사: {e}")


def cmd_approval_reset(a) -> None:
    from replay import _approval as ap
    ap.reset()
    print("승인 기록·브레이커·시험용 반품 신청·권한 상승을 처음 상태로 돌렸습니다(시드 주문·반품은 그대로).")


def register(add, stack_opt) -> None:
    add("approval-status", cmd_approval_status)
    add("approval-reset", cmd_approval_reset)
