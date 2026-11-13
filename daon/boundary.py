"""섹션 11: 두 번 해석되는 요청. 설정 점검(공격 요청을 보내지 않는다)과 캐시 비우기(랩 도구).

  make audit-gateway        11-3 게이트웨이·앱 구간 설정 점검(실행 중 컨테이너의 설정·버전·이미지를 읽는다)
  make audit-dns            11-5 DNS 영역 파일 점검(tools/ci/audit_dns.py, CI에서도 같은 파일)
  make audit-mail           11-5 메일 서버 설정 사본·DMARC 점검(infra/mail/postfix-main.cf, 영역 파일의 _dmarc)
  make cache-purge          11-4 Varnish 캐시 비우기(재연을 연달아 돌릴 때)

모두 이 PC의 랩 파일과 컨테이너만 읽는다. 실제 DNS·메일 서버에는 닿지 않는다.
요청 경계 점검은 설정과 버전으로 한다. 모호한 요청을 직접 보내는 것은 시험(tests/test_s11_03_gateway.py)의
「방어 확인」 세 가지뿐이고, 그 요청은 두 해석이 같은 곳에서 끝나게 만들어 안에 다른 요청이 들어 있지 않다.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

from daon import stack as st
from daon.config import INFRA, ROOT

GATEWAY = "daon-gateway-1"
VARNISH = "daon-varnish-1"

# 점검 기준(공개 공지에서 옮김, 【출처 확인】 교재·대본에 쓰기 전에 원문으로 다시 확인)
#   nginx 1.21.1: Content-Length와 Transfer-Encoding이 함께 온 요청·헤더 이름의 공백/제어 문자를 늘 거절. 1.21.2: HTTP/1.0의 Transfer-Encoding 거절
#   nginx 1.29.4: 뒷단 HTTP/2(proxy_http_version 2). 1.29.7: 뒷단 연결 재사용이 기본값
#   랩 기준은 지금 안정판의 마지막 보안 수정(1.30.6, 2026-10-14)
MIN_NGINX = (1, 30, 6)
MIN_TOMCAT = (10, 1, 19)
# Postfix SMTP 밀반입(CVE-2023-51764) 대응이 들어간 판(계열별). 3.9부터는 smtpd_forbid_bare_newline 기본값이 normalize
MIN_POSTFIX = {(3, 5): (3, 5, 23), (3, 6): (3, 6, 13), (3, 7): (3, 7, 9), (3, 8): (3, 8, 4)}


# ── 공통 ──
def _ver(text: str) -> tuple[int, ...] | None:
    m = re.search(r"(\d+)\.(\d+)\.(\d+)", text or "")
    return tuple(int(x) for x in m.groups()) if m else None


def _show(v) -> str:
    return ".".join(map(str, v)) if v else "알 수 없음"


def _docker(*args: str, timeout: int = 30) -> tuple[int, str]:
    try:
        r = subprocess.run(["docker", *args], capture_output=True, text=True, timeout=timeout)
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return 1, ""
    return r.returncode, (r.stdout + r.stderr)


def _print_rows(title: str, rows: list[tuple[str, str, bool | None]]) -> int:
    print(title)
    w = max(len(r[0]) for r in rows)
    bad = 0
    for name, val, ok in rows:
        mark = "해당 없음" if ok is None else ("확인" if ok else "고칠 것")
        bad += ok is False
        print(f"  {name.ljust(w)}  {mark:<6}  {val}")
    return bad


# ── 11-3 게이트웨이 ──
def gateway_config() -> tuple[str, str]:
    """(설정 글자, 어디서 읽었나). 실행 중 게이트웨이의 실제 설정(nginx -T)을 먼저, 없으면 저장소 파일."""
    code, out = _docker("exec", GATEWAY, "nginx", "-T")
    if code == 0 and "server" in out:
        return out, "실행 중 게이트웨이(nginx -T)"
    conf = INFRA / "gateway" / "nginx.conf"
    return (conf.read_text("utf-8") if conf.exists() else ""), "저장소 파일(게이트웨이가 떠 있지 않음)"


def _image(container: str) -> str:
    code, out = _docker("inspect", container, "--format", "{{.Config.Image}}")
    return out.strip() if code == 0 else ""


def _shop_server(text: str) -> str:
    """8086 server 블록 글자(대략: listen 8086부터 다음 server 블록 전까지)."""
    m = re.search(r"listen\s+8086\b.*?(?=\nserver\s*\{|\Z)", text, re.S)
    return m.group(0) if m else ""


def h2_backend_supported() -> bool | None:
    """실측(작성자 결정 #15): 실행 중 nginx가 proxy_http_version 2 설정을 받아들이나. 임시 설정을 nginx -t로만 확인(띄우지 않음)."""
    conf = ("error_log /dev/stderr; pid /tmp/daon-h2.pid; events {} http { server { listen 127.0.0.1:65000; "
            "location / { proxy_http_version 2; proxy_pass http://127.0.0.1:1; } } }")
    code, out = _docker("exec", GATEWAY, "sh", "-c", f"printf '%s\\n' '{conf}' > /tmp/daon-h2.conf && nginx -t -c /tmp/daon-h2.conf")
    if "syntax is ok" in out:
        return True
    if "invalid value" in out or "unknown directive" in out or "emerg" in out:
        return False
    return None


def gateway_rows() -> list[tuple[str, str, bool | None]]:
    rows: list[tuple[str, str, bool | None]] = []
    text, where = gateway_config()
    shop = _shop_server(text)
    rows.append(("고객 API 입구가 게이트웨이 뒤에 있나(8086 → Varnish → 앱)", "있음" if shop else f"없음({where})", bool(shop)))
    two = "$daon_two_lengths" in text and re.search(r"if\s*\(\$daon_two_lengths\)\s*\{\s*return\s+400", text)
    te = "$daon_bad_te" in text and re.search(r"if\s*\(\$daon_bad_te\)\s*\{\s*return\s+400", text)
    rows.append(("모호한 요청 거절 규칙(길이 표시 둘·chunked가 아닌 Transfer-Encoding → 400)",
                 "있음" if (two and te) else "설정에 없음(nginx 기본 동작에만 기댐)", bool(two and te)))
    ev = "request.denied.malformed" in text and re.search(r"access_log\s+\S+\s+\S+\s+if=\$daon_gw_reject", text)
    rows.append(("거절을 보안 이벤트 한 줄로 남기나(request.denied.malformed)", "남김" if ev else "남기지 않음", bool(ev)))
    buf_off = re.search(r"proxy_request_buffering\s+off", text)
    rows.append(("앞단이 본문을 다 받은 뒤 넘기나(proxy_request_buffering off 없음)", "끈 곳 있음" if buf_off else "기본값(on)", not buf_off))
    if shop:
        reuse_off = re.search(r'proxy_set_header\s+Connection\s+"?close"?', shop)
        h2 = re.search(r"proxy_http_version\s+2\b", shop)
        rows.append(("뒷단 연결 재사용을 껐나(1.29.7부터 기본은 재사용)", "끔(Connection: close)" if reuse_off else ("HTTP/2 뒷단" if h2 else "재사용(기본값)"),
                     bool(reuse_off or h2)))
    else:
        rows.append(("뒷단 연결 재사용을 껐나(1.29.7부터 기본은 재사용)", "고객 API 입구 없음", None))
    code, out = _docker("exec", GATEWAY, "nginx", "-v")
    nginx = _ver(out) if code == 0 else None
    rows.append(("게이트웨이 nginx 버전", _show(nginx) + f" (기준 {_show(MIN_NGINX)} 이상)", None if not nginx else nginx >= MIN_NGINX))
    img = _image(GATEWAY)
    rows.append(("게이트웨이 이미지를 다이제스트로 고정했나", img or "게이트웨이가 떠 있지 않음", None if not img else "@sha256:" in img))
    vimg = _image(VARNISH)
    rows.append(("캐시(Varnish) 이미지를 다이제스트로 고정했나", vimg or "Varnish 없음(A7 전)", None if not vimg else "@sha256:" in vimg))
    h2 = h2_backend_supported() if nginx else None
    rows.append(("[실측] 게이트웨이가 뒷단 HTTP/2를 지원하나(결정 #15)",
                 {True: "지원함. 다만 Varnish 뒷단(앱 쪽)은 HTTP/1.1만 → 「거절 + 버전 고정」으로 간다",
                  False: "지원 안 함 → 「거절 + 버전 고정」", None: "확인 못 함"}[h2], None))
    r = st.compose("spring", "logs", "--no-log-prefix", "mall-portal", check=False, capture=True)
    tom = None
    for line in (r.stdout or "").splitlines():
        if "Starting Servlet engine" in line:
            tom = _ver(line)
    rows.append(("앱 서버(Tomcat) 버전(mall-portal 시작 로그)", _show(tom) + f" (기준 {_show(MIN_TOMCAT)} 이상)", None if not tom else tom >= MIN_TOMCAT))
    loose = [p.relative_to(ROOT).as_posix() for p in (ROOT / "spring").glob("mall-*/src/main/resources/application*.yml")
             if re.search(r"reject-illegal-header:\s*false|relaxed-(path|query)-chars", p.read_text("utf-8"))]
    rows.append(("앱 서버가 잘못된 헤더·글자를 받아 주는 설정이 없나", ", ".join(loose) or "없음(Tomcat 기본: 거절)", not loose))
    express = ROOT / "express" / "packages" / "evershop" / "src"
    insecure = []
    for p in [express / "bin" / "lib" / "startUp.js", ROOT / "express" / "package.json"]:
        if p.exists() and re.search(r"insecureHTTPParser\s*:\s*true|--insecure-http-parser", p.read_text("utf-8")):
            insecure.append(p.relative_to(ROOT).as_posix())
    rows.append(("Node(EverShop)가 느슨한 HTTP 파서를 켜지 않았나(insecureHTTPParser)", ", ".join(insecure) or "켜지 않음", not insecure))
    df = INFRA / "docker" / "evershop.Dockerfile"
    m = re.search(r"^FROM\s+node:(\S+)", df.read_text("utf-8"), re.M) if df.exists() else None
    pinned = bool(m and re.match(r"\d+\.\d+\.\d+", m.group(1)))
    rows.append(("Node 기반 이미지 버전을 정확히 고정했나(evershop.Dockerfile)", f"node:{m.group(1)}" if m else "모름", pinned if m else None))
    return rows


def gateway_failed(rows) -> list[str]:
    return [n for n, _, ok in rows if ok is False]


def cmd_audit_gateway(a) -> None:
    rows = gateway_rows()
    bad = _print_rows("게이트웨이·앱 구간 설정 점검(11-3) · 모호한 요청은 보내지 않습니다", rows)
    print("\n점검 항목 모두 확인" if not bad else f"\n고칠 것 {bad}개: A7(make goto L=11-3 AT=done) 뒤 다시 make audit-gateway")
    if a.json:
        print(json.dumps([{"item": n, "value": v, "ok": o} for n, v, o in rows], ensure_ascii=False))
    raise SystemExit(1 if bad else 0)


# ── 11-5 DNS ──
def _dns():
    sys.path.insert(0, str(ROOT / "tools" / "ci"))
    import audit_dns
    return audit_dns


def dns_findings(emit_events: bool = False):
    return _dns().run(emit_events=emit_events, echo=False)


def cmd_audit_dns(a) -> None:
    raise SystemExit(_dns().main([]))


# ── 11-5 메일 ──
POSTFIX = INFRA / "mail" / "postfix-main.cf"


def _postfix(text: str) -> tuple[tuple[int, ...] | None, dict[str, str]]:
    ver = None
    params: dict[str, str] = {}
    for line in text.splitlines():
        m = re.match(r"^#\s*mail_version:\s*(\S+)", line)
        if m:
            ver = _ver(m.group(1))
            continue
        if line.strip().startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        params[k.strip()] = v.strip()
    return ver, params


def dmarc_policy() -> str | None:
    zone = INFRA / "dns" / "daon.example.zone"
    if not zone.exists():
        return None
    for line in zone.read_text("utf-8").splitlines():
        if line.startswith("_dmarc") and "v=DMARC1" in line:
            m = re.search(r"\bp=(\w+)", line)
            return m.group(1).lower() if m else ""
    return ""


def mail_rows() -> list[tuple[str, str, bool | None, str]]:
    """(항목, 본 값, 통과, 이벤트 target)"""
    rows: list[tuple[str, str, bool | None, str]] = []
    text = POSTFIX.read_text("utf-8") if POSTFIX.exists() else ""
    ver, p = _postfix(text)
    if ver is None:
        rows.append(("메일 서버(Postfix) 버전", "설정 사본에 버전 줄이 없음", False, "mail:mail_version"))
    else:
        want = MIN_POSTFIX.get(ver[:2])
        ok = ver[:2] >= (3, 9) or (want is not None and ver >= want)
        rows.append(("메일 서버(Postfix) 버전", _show(ver) + (f" (이 계열 기준 {_show(want)} 이상)" if want else " (3.9 이상이면 통과)"), ok, "mail:mail_version"))
    fb = p.get("smtpd_forbid_bare_newline", "")
    default_normalize = bool(ver and ver[:2] >= (3, 9))
    ok = fb in ("yes", "normalize") or (not fb and default_normalize)
    shown = fb or ("설정 없음(이 버전 기본 normalize)" if default_normalize else "설정 없음(이 버전 기본 no = 줄 끝 LF 하나도 받음)")
    rows.append(("메일 끝 처리: 줄 끝이 LF 하나뿐인 줄을 거부·정규화(smtpd_forbid_bare_newline)", shown, ok, "mail:smtpd_forbid_bare_newline"))
    dr = p.get("smtpd_data_restrictions", "")
    rows.append(("응답 전에 몰아 보낸 명령 거부(smtpd_data_restrictions = reject_unauth_pipelining)", dr or "비어 있음",
                 "reject_unauth_pipelining" in dr, "mail:smtpd_data_restrictions"))
    pol = dmarc_policy()
    rows.append(("DMARC 정책(_dmarc.daon.example의 p=)", "레코드 없음" if not pol else f"p={pol}", pol in ("quarantine", "reject"),
                 "dns:_dmarc.daon.example"))
    spring_mail = any("JavaMailSender" in f.read_text("utf-8", errors="ignore")
                      for f in (ROOT / "spring" / "mall-portal" / "src").rglob("*.java"))
    rows.append(("[참고] 앱이 메일을 만드는 방법(Spring)", "JavaMailSender(Jakarta Mail이 줄 끝을 CRLF로 씀)" if spring_mail else "메일 발송 코드 없음", None, ""))
    return rows


def mail_failed(rows) -> list:
    return [r for r in rows if r[2] is False]


def cmd_audit_mail(a) -> None:
    rows = mail_rows()
    bad = _print_rows("메일 서버 설정 사본·DMARC 점검(11-5) · 실제 메일 서버에는 닿지 않습니다 · infra/mail/postfix-main.cf",
                      [(n, v, ok) for n, v, ok, _ in rows])
    if bad:
        sys.path.insert(0, str(ROOT / "tools"))
        from secevent import emit
        for n, v, ok, target in mail_failed(rows):
            emit("config.audit.failed", "11-5", "tool:audit-mail", target, n.split("(")[0].strip(), echo=False)
        print(f"\n고칠 것 {bad}개 → config.audit.failed {bad}줄(reports/security-events.jsonl)")
    else:
        print("\n점검 항목 모두 확인")
    raise SystemExit(1 if bad else 0)


# ── 11-4 캐시 ──
def purge_cache() -> str:
    """Varnish 캐시를 비운다(ban 하나). varnishadm이 안 되면 컨테이너를 다시 띄운다(메모리 캐시라 비워진다)."""
    code, out = _docker("exec", VARNISH, "varnishadm", "ban", "req.url", "~", ".")
    if code == 0:
        return "ban req.url ~ . (캐시 전체를 비움)"
    code2, _ = _docker("inspect", VARNISH, "--format", "{{.State.Running}}")
    if code2 != 0:
        return "Varnish가 없습니다(A7은 11-3 완료 지점부터)"
    st.compose("spring", "restart", "varnish", check=False)
    st.wait_healthy("spring", timeout=60)
    return "varnishadm이 안 되어 Varnish를 다시 띄움(캐시가 비워짐)"


def varnish_stat(field: str = "MAIN.cache_hit") -> int | None:
    code, out = _docker("exec", VARNISH, "varnishstat", "-1", "-f", field)
    m = re.search(rf"{re.escape(field)}\s+(\d+)", out) if code == 0 else None
    return int(m.group(1)) if m else None


def cmd_cache_purge(a) -> None:
    print("Varnish 캐시 비우기: " + purge_cache())


def register(add, stack_opt) -> None:
    add("audit-gateway", cmd_audit_gateway, (["--json"], {"action": "store_true"}))
    add("audit-dns", cmd_audit_dns)
    add("audit-mail", cmd_audit_mail)
    add("cache-purge", cmd_cache_purge)
