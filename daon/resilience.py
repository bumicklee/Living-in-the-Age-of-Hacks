"""섹션 9: 예외와 장애. 스텁 장애 넣기·측정·설정 점검(랩 도구).

  make stub-fault S=sms|pay MODE=500|slow|ok [MS=3000]   가짜 문자·결제사 스텁에 장애를 넣고 빼기(9-2·9-4)
  make measure M=regex                                    느린 정규식: 길이별 처리 시간 표(9-1). 상한은 replay/_faults.py에 박아 둠
  make measure M=retry                                    재시도 폭주: 결제사를 3초씩 늦추고 결제 요청 10개 → 증폭 배수(9-4·9-5)
  make measure-cooldown                                   측정 뒤 mall-portal CPU가 내려갈 때까지 기다림
  make config-check C=http2                               HTTP/2 Rapid Reset·CONTINUATION 설정 점검(공격 트래픽은 보내지 않는다, 9-3)

모두 127.0.0.1의 랩만 부른다. 측정이 끝나면 스텁 장애는 늘 정상(ok)으로 되돌린다.
"""
from __future__ import annotations

import json
import re
import subprocess

from daon import stack as st
from daon.config import INFRA, ROOT
from replay import NotReady

# 점검 기준(공개 공지에서 옮김, 【출처 확인】 교재·대본에 쓰기 전에 원문으로 다시 확인)
#   Rapid Reset(CVE-2023-44487, 2023-10): nginx 1.25.3(완화 추가), Tomcat 10.1.14, Node 18.18.2·20.8.1
#   CONTINUATION 대량 전송(2024-04): Tomcat 10.1.19(CVE-2024-24549), Node 18.20.1·20.12.1(CVE-2024-27983)
MIN_TOMCAT = (10, 1, 19)
MIN_NODE = {18: (18, 20, 1), 20: (20, 12, 1), 22: (22, 0, 0)}
MIN_NGINX = (1, 25, 3)


def cmd_stub_fault(a) -> None:
    from replay import _faults
    try:
        f = _faults.set_fault(a.svc, a.mode, a.ms or (3000 if a.mode == "slow" else 0))
    except NotReady as e:
        raise SystemExit(str(e))
    name = {"sms": "가짜 문자(9102)", "pay": "가짜 결제사(9101)"}[a.svc]
    what = {"ok": "정상", "500": "모든 요청에 HTTP 500", "slow": f"{f.get('ms')}ms씩 늦게 답함"}[f["mode"]]
    print(f"{name} 스텁: {what}")
    if f["mode"] != "ok":
        print(f"되돌리기: make stub-fault S={a.svc} MODE=ok  (켠 채 다른 수업으로 가면 make check가 알려 줍니다)")


def warn_if_faulted() -> None:
    try:
        from replay import _faults
        on = _faults.faulted()
    except Exception:  # noqa: BLE001 - 스텁이 안 떠 있으면 조용히 넘어간다
        return
    if on:
        print(f"[알림] 스텁 장애가 켜져 있습니다: {', '.join(on)} → 정상 시험이 레드일 수 있습니다. 되돌리기: make stub-fault S=… MODE=ok")


def cmd_measure(a) -> None:
    from replay import _faults
    st.ensure_fresh("spring")
    try:
        if a.what == "regex":
            print("느린 정규식 측정(9-1): GET /daon/coupon/check, 길이 14자부터 한 글자씩, 요청마다 2초 제한, 1초를 넘으면 멈춤")
            rows = _faults.measure_regex()
            print("\n".join(_faults.regex_table(rows)))
            print("\n절대값은 PC마다 다릅니다. 몇 글자 늘 때 시간이 두 배씩 뛰는 「모양」을 보세요.")
            if _faults.regex_slow(rows):
                print("측정 뒤 서버 스레드가 아직 돌 수 있습니다: make measure-cooldown")
        elif a.what == "retry":
            print("재시도 폭주 측정(9-4·9-5): 결제사 스텁 3초 지연, 결제 요청 10건(1초 간격), 60초 동안 스텁이 받은 요청 수(약 75초)")
            m = _faults.measure_retry("full", progress=print)
            print("\n".join(_faults.retry_table(m)))
            print("결제사 스텁은 정상(ok)으로 되돌렸습니다.")
        else:
            raise SystemExit("M은 regex 또는 retry입니다")
    except NotReady as e:
        raise SystemExit(f"[준비 안 됨] {e}")


def cmd_measure_cooldown(a) -> None:
    from replay import _faults
    print("\n".join(_faults.cooldown()))


# ── 설정 점검(9-3, 옛 9-4) ──
def _ver(text: str) -> tuple[int, ...] | None:
    m = re.search(r"(\d+)\.(\d+)\.(\d+)", text or "")
    return tuple(int(x) for x in m.groups()) if m else None


def _show(v) -> str:
    return ".".join(map(str, v)) if v else "알 수 없음"


def _exec(container: str, *cmd: str) -> str:
    r = subprocess.run(["docker", "exec", container, *cmd], capture_output=True, text=True, timeout=30)
    return (r.stdout + r.stderr).strip() if r.returncode == 0 else ""


def http2_rows() -> list[tuple[str, str, bool | None]]:
    """(항목, 본 값, 통과 여부. None이면 해당 없음)"""
    rows: list[tuple[str, str, bool | None]] = []
    conf = (INFRA / "gateway" / "nginx.conf")
    text = conf.read_text("utf-8") if conf.exists() else ""
    h2 = bool(re.search(r"^\s*(listen\s+[^;]*\bhttp2\b|http2\s+on\s*;)", text, re.M))
    rows.append(("게이트웨이 nginx에서 HTTP/2를 켰나", "켬" if h2 else "끔(HTTP/1.1만)", None if not h2 else True))
    if h2:
        streams = re.search(r"http2_max_concurrent_streams\s+(\d+)", text)
        n = int(streams.group(1)) if streams else 128
        rows.append(("  동시 스트림 상한(http2_max_concurrent_streams)", f"{n}{'(기본값)' if not streams else ''}", n <= 128))
    nginx = _ver(_exec("daon-gateway-1", "nginx", "-v"))
    rows.append(("게이트웨이 nginx 버전", _show(nginx) + f" (기준 {_show(MIN_NGINX)} 이상)", None if not nginx else nginx >= MIN_NGINX))
    up = re.findall(r"proxy_http_version\s+([\d.]+)", text)
    rows.append(("게이트웨이 → 앱 구간이 HTTP/1.1인가", ", ".join(sorted(set(up))) or "지정 없음(nginx 기본 1.0)", not up or all(v in ("1.0", "1.1") for v in up)))
    r = st.compose("spring", "logs", "--no-log-prefix", "mall-portal", check=False, capture=True)
    tom = None
    for line in (r.stdout or "").splitlines():
        if "Starting Servlet engine" in line:
            tom = _ver(line)
    rows.append(("내장 Tomcat 버전(mall-portal 시작 로그)", _show(tom) + f" (기준 {_show(MIN_TOMCAT)} 이상)", None if not tom else tom >= MIN_TOMCAT))
    app_h2 = any("http2" in p.read_text("utf-8") for p in (ROOT / "spring").glob("mall-*/src/main/resources/application*.yml"))
    rows.append(("앱(Tomcat)이 HTTP/2를 직접 받나(server.http2.enabled)", "켬" if app_h2 else "끔", not app_h2))
    node = _ver(_exec("daon-evershop-1", "node", "-v"))
    if node:
        want = MIN_NODE.get(node[0])
        rows.append(("Node(EverShop) 버전", _show(node) + (f" (기준 {_show(want)} 이상)" if want else " (지원 끝난 계열)"), bool(want) and node >= want))
    else:
        rows.append(("Node(EverShop) 버전", "Express 스택이 꺼져 있음(make up STACK=express 뒤 다시)", None))
    return rows


def cmd_config_check(a) -> None:
    if a.what != "http2":
        raise SystemExit("지금은 C=http2만 있습니다")
    print("HTTP/2 Rapid Reset(2023)·CONTINUATION 대량 전송(2024) 설정 점검 · 공격 트래픽은 보내지 않습니다")
    rows = http2_rows()
    w = max(len(r[0]) for r in rows)
    bad = 0
    for name, val, ok in rows:
        mark = "해당 없음" if ok is None else ("확인" if ok else "고칠 것")
        bad += ok is False
        print(f"  {name.ljust(w)}  {mark:<6}  {val}")
    print("\n점검 항목 모두 확인" if not bad else f"\n고칠 것 {bad}개: 버전을 올리거나 설정을 바꾼 뒤 다시 make config-check C=http2")
    if a.json:
        print(json.dumps([{"item": n, "value": v, "ok": o} for n, v, o in rows], ensure_ascii=False))
    raise SystemExit(1 if bad else 0)


def register(add, stack_opt) -> None:
    add("stub-fault", cmd_stub_fault, (["svc"], {"choices": ["sms", "pay"]}), (["mode"], {"choices": ["ok", "500", "slow"]}),
        (["--ms"], {"type": int, "default": 0}))
    add("measure", cmd_measure, (["what"], {"choices": ["regex", "retry"]}))
    add("measure-cooldown", cmd_measure_cooldown)
    add("config-check", cmd_config_check, (["what"], {"choices": ["http2"]}), (["--json"], {"action": "store_true"}))
