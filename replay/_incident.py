"""섹션 14 도우미: 느린 결제사(14-3 회귀 재연)·축소 운영·사고 대응 격리(14-4). 재연·시험·랩 도구가 같이 쓴다.

안전 기준(수업 상세 계획 14장): 127.0.0.1의 다온마켓과 랩 스텁만 부른다. 계정·주문·금액은 모두 가짜다.
  - 느린 결제사는 가짜 결제사 스텁의 느림 모드(make stub-fault S=pay MODE=slow, 오류 없음, 최대 5초)로만 만든다. 끝나면 늘 되돌린다.
  - 동시 요청 수·지연·기준 시간은 이 파일에 박아 둔다(인자로 바꿀 수 없다). 랩 밖으로 부하를 보내지 않는다.
  - DB를 직접 고치는 일(가짜 결제 대기 주문, 잠금 풀기)은 랩 도구로만 한다(컨테이너 안 mysql, 랩 전용 root).
"""
from __future__ import annotations

import concurrent.futures as cf
import time

import httpx

from daon import stack as st
from daon.config import HOST, URLS, port
from replay import NotReady
from replay import _approval as ap
from replay import _faults
from replay._http import admin, new_trace_id, portal, portal_login
from replay._money import mysql, one

GROUP = "incident-response"
PAY = f"http://{HOST}:{port('DAON_PAYMENT_PORT', 9101)}"
PORTAL = URLS["spring"]["portal"]

# 14-3 회귀 재연 값(수업 카드와 같다). 랩 크기: 고객 앱 요청 처리 스레드 24개(compose.a11.yml)
SLOW_MS = 3000            # 결제사 느림(오류 아님)
CONCURRENT = 40           # 동시 결제 확인 요청(고객 웹이 결제 뒤 몇 초마다 부르는 것)
PRODUCT_LIMIT = 1.5       # 상품 목록 기준 시간(초)
STATUS_ORDER = 9001       # alice의 주문(시드)
PENDING_ORDER = 9891      # 랩 도구가 만드는 alice의 결제 대기(0) 가짜 주문(14-3)
PENDING_PAY = 12000


# ── 14-3 느린 결제사 ──
def slow_dependency(user: str = "alice") -> dict:
    """결제사를 느림 모드로 두고 결제 확인 CONCURRENT개를 동시에 보내는 동안 상품 목록 응답 시간을 잰다. 끝나면 스텁을 되돌린다"""
    with portal() as c:
        token = portal_login(c, user)
    out = {"concurrent": CONCURRENT, "slow_ms": SLOW_MS, "limit": PRODUCT_LIMIT}
    _faults.set_fault("pay", "slow", SLOW_MS)
    try:
        def status_call(i: int) -> tuple[int, str]:
            try:
                r = httpx.get(PORTAL + "/pay/status", params={"orderId": STATUS_ORDER}, timeout=15,
                              headers={"Authorization": token, "X-Trace-Id": f"replay-r14-3-{i:04d}"})
                return r.status_code, ((r.json().get("data") or {}).get("status") if r.status_code == 200 else "")
            except httpx.HTTPError as e:
                return 0, e.__class__.__name__
        with cf.ThreadPoolExecutor(max_workers=CONCURRENT) as pool:
            futs = [pool.submit(status_call, i) for i in range(CONCURRENT)]
            time.sleep(0.7)                                   # 결제 확인이 스레드를 붙잡을 때까지
            probes = []
            for _ in range(3):
                t0 = time.monotonic()
                try:
                    r = httpx.get(PORTAL + "/product/search", params={"pageNum": 1, "pageSize": 5}, timeout=10)
                    probes.append((time.monotonic() - t0, r.status_code))
                except httpx.HTTPError:
                    probes.append((10.0, 0))
                time.sleep(0.3)
            results = [f.result() for f in futs]
    finally:
        _faults.set_fault("pay", "ok", 0)
    out["product_seconds"] = [round(p[0], 2) for p in probes]
    out["product_codes"] = [p[1] for p in probes]
    out["worst"] = max(p[0] for p in probes)
    out["status_counts"] = {}
    for code, status in results:
        key = f"{code}:{status}"
        out["status_counts"][key] = out["status_counts"].get(key, 0) + 1
    return out


# ── 13-6 권한 상승(묶음 incident-response) ──
def elevate(user: str = "admin", minutes: int = 30) -> None:
    ap.require_a10()
    req = ap.elevation_request(user, minutes=minutes, group=GROUP)
    if not req.get("id"):
        raise NotReady(f"권한 상승 요청이 접수되지 않았습니다({req}). 14-3 완료 지점(승인 서비스 policy.yml에 incident-response)인지 확인")
    ok = ap.ap("POST", f"/elevations/{req['id']}/approve", "staff_lead")
    if ok.status_code != 200 or ap.elevation_activate(user, req["id"]) != 200:
        raise NotReady(f"권한 상승을 켜지 못했습니다(승인 HTTP {ok.status_code})")


def set_mode(mode: str, user: str = "admin", reason: str = "결제사 응답 지연(가짜 티켓 INC-14)", trace: str | None = None) -> httpx.Response:
    with admin() as c:
        return c.post("/daon/mode/payment", headers={"Authorization": ap.must_login(user), "X-Trace-Id": trace or new_trace_id("s14-3")},
                      json={"mode": mode, "reason": reason})


def reset_mode() -> None:
    """랩 도구: 축소 운영 끔·출금 브레이커 닫음·권한 상승·시험용 반품 신청 정리(시험 앞뒤)"""
    st.redis_cli("DEL", "mall:mode:payment")
    ap.reset()


def ensure_pending_order() -> int:
    """alice의 결제 대기(0) 가짜 주문 하나(멱등). 받는 사람 칸은 비워 둔다(개인정보 없음)"""
    if one(f"SELECT COUNT(*) FROM oms_order WHERE id={PENDING_ORDER}") != "1":
        mysql("INSERT INTO oms_order (id, member_id, order_sn, create_time, member_username, total_amount, pay_amount, freight_amount, "
              "promotion_amount, integration_amount, coupon_amount, discount_amount, pay_type, source_type, status, order_type, auto_confirm_day, "
              f"confirm_status, delete_status, note) VALUES ({PENDING_ORDER}, 101, 'DAON20261114{PENDING_ORDER}', NOW(), 'alice', {PENDING_PAY}, "
              f"{PENDING_PAY}, 0, 0, 0, 0, 0, 0, 1, 0, 0, 15, 0, 0, '랩 도구: 14-3 축소 운영 시험용 가짜 결제 대기 주문')")
    mysql(f"UPDATE oms_order SET status=0 WHERE id={PENDING_ORDER}")
    return PENDING_ORDER


def pay_seen(trace: str) -> int:
    """가짜 결제사가 받은 결제 요청 가운데 이 추적 번호를 단 것의 수"""
    try:
        rows = httpx.get(f"{PAY}/_lab/stats", timeout=5).json().get("items") or []
    except (httpx.HTTPError, ValueError) as e:
        raise NotReady(f"가짜 결제사({PAY})에 닿지 않습니다({e.__class__.__name__})")
    return sum(1 for r in rows if r.get("trace_id") == trace)


def rabbit_queue_depth(queue: str) -> int | None:
    """RabbitMQ 큐에 쌓인 메시지 수(랩 도구 계정, 컨테이너 안 rabbitmqctl)"""
    r = st.compose("spring", "exec", "-T", "rabbitmq", "rabbitmqctl", "-q", "list_queues", "-p", "/mall", "name", "messages", check=False, capture=True)
    for line in (r.stdout or "").splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[0] == queue:
            return int(parts[1])
    return None
