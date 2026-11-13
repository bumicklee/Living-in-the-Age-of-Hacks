"""섹션 9 도우미: 스텁 장애 넣기, 느린 정규식 측정(9-1·9-3), 재시도 폭주 측정(9-4·9-5), RabbitMQ 큐 보기.

안전 기준(수업 상세 계획 9장): 127.0.0.1의 랩만 부른다. 입력 길이·요청 수·시간 상한은 이 파일에 박아 두고 인자로 바꿀 수 없다.
  - 정규식 측정: 한 번에 요청 하나, 요청마다 2초 제한, 길이 14~28자(15개 이하), 1초를 넘는 길이가 나오면 그 뒤는 보내지 않는다.
  - 재시도 측정: 결제 요청 10개(1초 간격), 60초 동안 센다, 끝나면 스텁을 늘 되돌린다.
"""
from __future__ import annotations

import subprocess
import time

import httpx

from daon.config import HOST, port
from replay import NotReady
from replay._http import new_trace_id, portal, portal_login

STUBS = {"sms": port("DAON_SMS_PORT", 9102), "pay": port("DAON_PAYMENT_PORT", 9101)}
MODES = ("ok", "500", "slow")

# 정규식(T23)
REGEX_MIN, REGEX_MAX = 14, 28          # 길이(글자 수). 28은 시작 코드의 랩 안전 장치와 같다
REGEX_TIMEOUT = 2.0
REGEX_STOP = 1.0                       # 이 시간을 넘으면 더 긴 입력은 보내지 않는다
REGEX_SLOW = 0.5                       # 판정 기준: 이보다 오래 걸린 길이가 있으면 「느린 정규식」

# 재시도(T24)
PAY_ORDER = 9001                       # alice의 주문(시드). 같은 주문에 ref만 바꿔 결제 요청을 보낸다
PAY_SLOW_MS = 3000
FULL = {"orders": 10, "interval": 1.0, "window": 60, "recover": 15}
QUICK = {"orders": 3, "interval": 0.5, "window": 15, "recover": 0}   # make score용(짧게)
RATIO_OK = {"full": 1.2, "quick": 3.0}   # 이 배수 이하면 그린. full 기준 1.2는 【실측 예정】(수업 계획 9-5)


# ── 스텁 장애 ──
def stub_url(svc: str) -> str:
    if svc not in STUBS:
        raise SystemExit(f"S는 {', '.join(STUBS)} 중 하나입니다")
    return f"http://{HOST}:{STUBS[svc]}"


def set_fault(svc: str, mode: str, ms: int = 0) -> dict:
    if mode not in MODES:
        raise SystemExit(f"MODE는 {', '.join(MODES)} 중 하나입니다")
    try:
        r = httpx.post(stub_url(svc) + "/_lab/fault", json={"mode": mode, "ms": max(0, min(int(ms), 5000))}, timeout=5)
    except httpx.TransportError as e:
        raise NotReady(f"{svc} 스텁({stub_url(svc)})에 닿지 않습니다. make up 먼저({e.__class__.__name__})")
    r.raise_for_status()
    return r.json()


def get_fault(svc: str) -> dict | None:
    try:
        return httpx.get(stub_url(svc) + "/_lab/fault", timeout=3).json()
    except (httpx.HTTPError, ValueError):
        return None


def faulted() -> list[str]:
    """장애 모드가 켜진 스텁(make check 앞에서 알림용)"""
    out = []
    for svc in STUBS:
        f = get_fault(svc)
        if f and f.get("mode") != "ok":
            out.append(f"{svc}={f.get('mode')}" + (f"({f.get('ms')}ms)" if f.get("mode") == "slow" else ""))
    return out


def pay_stats() -> dict:
    return httpx.get(stub_url("pay") + "/_lab/stats", timeout=5).json()


def pay_reset() -> None:
    httpx.post(stub_url("pay") + "/_lab/reset", json={}, timeout=5)


# ── 느린 정규식(T23) ──
def adversarial(n: int) -> str:
    """길이 n: 대문자 n-1개 + 맨 끝 한 글자가 규칙 밖(!). 맞지 않는 것을 알기까지 나누는 방법을 모두 시도한다"""
    return "A" * (n - 1) + "!"


def measure_regex(user: str = "alice") -> list[dict]:
    """길이별 쿠폰 코드 확인 시간. [{len, sec, status}] (시간 초과면 sec=None)"""
    rows: list[dict] = []
    with portal() as c:
        token = portal_login(c, user)
        for n in range(REGEX_MIN, REGEX_MAX + 1):
            h = {"Authorization": token, "X-Trace-Id": new_trace_id(f"t23-{n}")}
            t0 = time.perf_counter()
            try:
                r = c.get("/daon/coupon/check", params={"code": adversarial(n)}, headers=h, timeout=REGEX_TIMEOUT)
                sec, status = time.perf_counter() - t0, r.status_code
            except httpx.TimeoutException:
                sec, status = None, "시간 초과"
            rows.append({"len": n, "sec": sec, "status": status})
            if sec is None or sec >= REGEX_STOP:
                break
    return rows


def regex_table(rows: list[dict]) -> list[str]:
    out = ["길이  처리 시간   응답", "----  ----------  ----"]
    for r in rows:
        sec = "2초 넘음" if r["sec"] is None else f"{r['sec'] * 1000:8.1f}ms"
        bar = "#" * min(40, int((r["sec"] if r["sec"] is not None else REGEX_TIMEOUT) * 20))
        out.append(f"{r['len']:>4}  {sec:>10}  {r['status']}  {bar}")
    return out


def regex_slow(rows: list[dict]) -> bool:
    return any(r["sec"] is None or r["sec"] >= REGEX_SLOW for r in rows)


def cooldown(container: str = "daon-mall-portal-1", below: float = 20.0, limit: int = 60) -> list[str]:
    """측정 뒤 CPU가 내려갈 때까지(최대 limit초). 2초 제한으로 끊어도 서버 스레드는 계속 돌 수 있다"""
    out = []
    t0 = time.time()
    while time.time() - t0 < limit:
        r = subprocess.run(["docker", "stats", "--no-stream", "--format", "{{.CPUPerc}}", container], capture_output=True, text=True)
        try:
            cpu = float(r.stdout.strip().rstrip("%"))
        except ValueError:
            out.append(f"{container}의 CPU를 읽지 못했습니다(떠 있나요?)")
            return out
        out.append(f"{int(time.time() - t0):>3}초  CPU {cpu:5.1f}%")
        if cpu < below:
            out.append("내려갔습니다.")
            return out
        time.sleep(3)
    out.append(f"{limit}초가 지나도 CPU가 높습니다. make logs S=mall-portal로 확인하세요")
    return out


# ── 재시도 폭주(T24) ──
def _pay_once(c: httpx.Client, token: str, ref: str) -> tuple[int, str]:
    tid = new_trace_id("t24")
    r = c.post(f"/daon/order/{PAY_ORDER}/pay", params={"ref": ref}, headers={"Authorization": token, "X-Trace-Id": tid})
    return r.status_code, r.headers.get("X-Trace-Id", tid)


def measure_retry(mode: str = "full", progress=None) -> dict:
    """결제사 스텁을 3초씩 늦춘 채 결제 요청 N개를 보내고, 스텁이 받은 요청 수를 센다. 끝나면 스텁을 되돌린다"""
    p = FULL if mode == "full" else QUICK
    say = progress or (lambda s: None)
    tag = f"m{int(time.time()) % 100000}"
    statuses, traces = [], []
    with portal() as c:
        token = portal_login(c, "alice")
        try:
            set_fault("pay", "slow", PAY_SLOW_MS)
            pay_reset()
            t0 = time.time()
            for i in range(p["orders"]):
                st, tid = _pay_once(c, token, f"{tag}-{i}")
                statuses.append(st)
                traces.append(tid)
                time.sleep(p["interval"])
            while time.time() - t0 < p["window"]:
                left = int(p["window"] - (time.time() - t0))
                say(f"  … 결제사 스텁이 받은 요청 {pay_stats()['count']}건 (남은 {left}초)")
                time.sleep(min(5, max(0.5, left)))
            during = pay_stats()["items"]
            set_fault("pay", "ok")
            after_ok = []
            if p["recover"]:
                t_ok = time.time()
                time.sleep(p["recover"])
                after_ok = pay_stats()["items"][len(during):]
                say(f"  스텁을 되돌린 뒤 {int(time.time() - t_ok)}초 동안 받은 요청 {len(after_ok)}건")
        finally:
            try:
                set_fault("pay", "ok")
            except NotReady:
                pass
    n = len(during)
    ratio = n / p["orders"] if p["orders"] else 0.0
    return {"mode": mode, "orders": p["orders"], "window": p["window"], "stub_requests": n, "ratio": ratio,
            "accepted": sum(1 for s in statuses if s == 202), "statuses": statuses, "traces": traces,
            "histogram": _histogram(during), "after_ok": len(after_ok), "ok_max": RATIO_OK[mode]}


def _histogram(items: list[dict], step: int = 5) -> list[tuple[int, int]]:
    if not items:
        return []
    start = min(i["ts"] for i in items)
    buckets: dict[int, int] = {}
    for i in items:
        b = int((i["ts"] - start) // step) * step
        buckets[b] = buckets.get(b, 0) + 1
    return sorted(buckets.items())


def retry_table(m: dict) -> list[str]:
    out = [f"결제 요청 {m['orders']}건(202 결제 대기 {m['accepted']}건) → {m['window']}초 동안 결제사 스텁이 받은 요청 {m['stub_requests']}건 "
           f"= 증폭 {m['ratio']:.1f}배 (그린 기준 {m['ok_max']}배 이하)", "초(구간)  요청 수"]
    for b, n in m["histogram"]:
        out.append(f"{b:>4}~{b + 4:<4} {n:>4}  " + "#" * min(60, n))
    if m["mode"] == "full":
        out.append(f"스텁을 되돌린 직후 15초 동안 받은 요청: {m['after_ok']}건")
    return out


def wait_pay_success(trace_id: str, timeout: float = 10.0) -> bool:
    t0 = time.time()
    while time.time() - t0 < timeout:
        if any(i.get("trace_id") == trace_id and i.get("status") == 201 for i in pay_stats()["items"]):
            return True
        time.sleep(1)
    return False


# ── RabbitMQ(A6) ──
def rabbit_queues() -> dict[str, int]:
    """vhost /mall의 큐 이름 → 쌓인 메시지 수(컨테이너 안 rabbitmqctl, 읽기만)"""
    import os
    name = f"{os.environ.get('COMPOSE_PROJECT_NAME', 'daon')}-rabbitmq-1"
    r = subprocess.run(["docker", "exec", name, "rabbitmqctl", "-q", "list_queues", "-p", "/mall", "name", "messages"],
                       capture_output=True, text=True, timeout=30)
    if r.returncode != 0:
        raise NotReady(f"RabbitMQ 큐를 읽지 못했습니다({name}): {r.stderr.strip()[:120]}")
    out = {}
    for line in r.stdout.splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1].isdigit():
            out[parts[0]] = int(parts[1])
    return out
