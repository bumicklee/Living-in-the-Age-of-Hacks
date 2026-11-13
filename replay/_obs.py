"""섹션 8 도우미: 감사 로그 파일(mall-admin), Loki(A5), Grafana, Mailpit(가짜 받은 편지함). 랩-설계.md 10절.

이 PC의 랩만 부른다(127.0.0.1). 값은 모두 가짜다. 화면에 찍을 때 토큰·전화번호 같은 값은 가린다(mask_preview).
"""
from __future__ import annotations

import json
import re
import time
from datetime import datetime, timezone

import httpx

from daon import stack as st
from daon.config import ENV, HOST, port
from replay import NotReady

AUDIT_FILE = "/var/logs/daon/audit.jsonl"
LOKI = f"http://{HOST}:{port('DAON_LOKI_PORT', 3100)}"
GRAFANA = f"http://{HOST}:{port('DAON_GRAFANA_PORT', 3030)}"
MAILPIT = f"http://{HOST}:{port('DAON_MAILPIT_PORT', 8025)}"
SUBJECT_PREFIX = "[다온 경보]"

# 로그 속 민감 값 모양(8-5 make log-scan). 앱의 LogMask·수집기와 같은 패턴
JWT = re.compile(r"eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]*")
PHONE = re.compile(r"(?<![0-9A-Za-z])01[016789]-?[0-9]{3,4}-?[0-9]{4}(?![0-9A-Za-z])")
PASSWORD = re.compile(r'(?i)"(?:password|newPassword|oldPassword|confirmPassword)"\s*:\s*"([^"*][^"]*)"|password=([^&\s,}\]*][^&\s,}\]]*)')
KINDS = {"password": PASSWORD, "jwt": JWT, "phone": PHONE}


def mask_preview(s: str, n: int = 160) -> str:
    """화면에 보일 때만 쓰는 가리기(값은 남기지 않는다)."""
    s = JWT.sub("eyJ***", s)
    s = PHONE.sub("010-****-****", s)
    s = re.sub(r'(?i)("(?:password|newPassword|oldPassword)"\s*:\s*")[^"]*(")', r"\1***\2", s)
    s = re.sub(r"(?i)(password=)[^&\s,}\]]+", r"\1***", s)
    return s[:n]


def a05_on() -> bool:
    return "a05" in st.arch_layers()


# ── 감사 로그 파일(8-3) ──
def audit_lines(stack: str = "spring", tail: int = 2000) -> list[dict]:
    r = st.compose(stack, "exec", "-T", "mall-admin", "sh", "-c", f"tail -n {tail} {AUDIT_FILE} 2>/dev/null || true",
                   check=False, capture=True)
    if r.returncode != 0 and "is not running" in (r.stderr or ""):
        raise NotReady("mall-admin이 떠 있지 않습니다(make up STACK=spring)")
    out = []
    for line in (r.stdout or "").splitlines():
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if isinstance(d, dict):
            out.append(d)
    return out


def audit_raw(stack: str = "spring") -> str:
    r = st.compose(stack, "exec", "-T", "mall-admin", "sh", "-c", f"cat {AUDIT_FILE} 2>/dev/null || true",
                   check=False, capture=True)
    return r.stdout or ""


def audit_write(text: str, stack: str = "spring") -> None:
    """파일 내용을 바꾼다(같은 파일·같은 inode에 덮어씀). 8-6 재연·시험이 지운 줄을 되돌릴 때 쓴다."""
    import subprocess
    files = []
    for f in st.compose_files():
        files += ["-f", str(f)]
    cmd = ["docker", "compose", *files, "--project-directory", str(st.INFRA), "--profile", stack,
           "exec", "-T", "mall-admin", "sh", "-c", f"cat > {AUDIT_FILE}"]
    subprocess.run(cmd, input=text, text=True, capture_output=True, check=True)


def wait_audit(trace_id: str, stack: str = "spring", timeout: float = 6.0) -> dict | None:
    t0 = time.time()
    while True:
        for d in reversed(audit_lines(stack, 300)):
            if d.get("trace_id") == trace_id:
                return d
        if time.time() - t0 > timeout:
            return None
        time.sleep(0.5)


# ── Loki(A5) ──
def loki_ready(timeout: float = 2.0) -> bool:
    try:
        return httpx.get(f"{LOKI}/ready", timeout=timeout).status_code == 200
    except httpx.HTTPError:
        return False


def loki_query(query: str, since_s: int = 3600, limit: int = 1000) -> list[tuple[dict, str]]:
    """로그 질의(범위). [(레이블, 줄)] 최신 순."""
    end = time.time_ns()
    try:
        r = httpx.get(f"{LOKI}/loki/api/v1/query_range", timeout=15,
                      params={"query": query, "start": end - since_s * 1_000_000_000, "end": end,
                              "limit": limit, "direction": "backward"})
    except httpx.HTTPError as e:
        raise NotReady(f"Loki({LOKI})에 닿지 않습니다. 8-4(A5)를 켰나요? make goto L=8-4 AT=done ({e.__class__.__name__})")
    if r.status_code != 200:
        raise NotReady(f"Loki 질의 실패 HTTP {r.status_code}: {r.text[:120]}")
    out = []
    for s in r.json().get("data", {}).get("result", []):
        for _, line in s.get("values", []):
            out.append((s.get("stream", {}), line))
    return out


def wait_loki(query: str, timeout: float = 60.0, since_s: int = 900, want: int = 1) -> list[tuple[dict, str]]:
    t0 = time.time()
    while True:
        rows = loki_query(query, since_s=since_s) if loki_ready() else []
        if len(rows) >= want or time.time() - t0 > timeout:
            return rows
        time.sleep(3)


# ── Grafana ──
def grafana_auth() -> tuple[str, str]:
    return ("admin", ENV.get("DAON_GRAFANA_ADMIN_PASSWORD", "lab-fake-grafana-0001"))


def grafana_ready(timeout: float = 2.0) -> bool:
    try:
        return httpx.get(f"{GRAFANA}/api/health", timeout=timeout).status_code == 200
    except httpx.HTTPError:
        return False


def grafana_rules() -> list[dict]:
    r = httpx.get(f"{GRAFANA}/api/v1/provisioning/alert-rules", auth=grafana_auth(), timeout=10)
    r.raise_for_status()
    return r.json()


def wait_grafana(timeout: float = 90.0) -> bool:
    t0 = time.time()
    while not grafana_ready():
        if time.time() - t0 > timeout:
            return False
        time.sleep(3)
    return True


# ── Mailpit(가짜 받은 편지함) ──
def _parse_time(v: str) -> float:
    try:
        return datetime.fromisoformat(v.replace("Z", "+00:00")).timestamp()
    except (ValueError, AttributeError):
        return 0.0


def mails(since: float = 0.0, contains: str = "", limit: int = 300) -> list[dict]:
    """since(유닉스 초) 뒤에 온 경보 메일. 최신 순. [{id, subject, to, at}]"""
    try:
        r = httpx.get(f"{MAILPIT}/api/v1/messages", params={"limit": limit}, timeout=10)
    except httpx.HTTPError as e:
        raise NotReady(f"Mailpit({MAILPIT})에 닿지 않습니다: {e.__class__.__name__}")
    out = []
    for m in r.json().get("messages", []):
        subj = m.get("Subject", "")
        at = _parse_time(m.get("Created", ""))
        if at < since or contains not in subj:
            continue
        out.append({"id": m.get("ID"), "subject": subj, "to": [t.get("Address") for t in m.get("To") or []], "at": at})
    return out


def mail_text(mid: str) -> str:
    r = httpx.get(f"{MAILPIT}/api/v1/message/{mid}", timeout=10)
    return r.json().get("Text", "") if r.status_code == 200 else ""


def firing(rule: str, actor_part: str = "") -> bool:
    """Grafana에서 그 규칙이 지금 울리는 중인가(actor 레이블에 actor_part가 든 경보가 있나).
    이미 울리는 중이면 새 메일은 반복 간격(8-4 4시간, 8-7 30분) 뒤에 온다. 그때는 이것으로 「경보가 났다」를 본다."""
    try:
        r = httpx.get(f"{GRAFANA}/api/prometheus/grafana/api/v1/rules", auth=grafana_auth(), timeout=10)
        for g in r.json()["data"]["groups"]:
            for rl in g.get("rules", []):
                if rl.get("name") == rule:
                    for a in rl.get("alerts", []):
                        if a.get("state", "").lower().startswith("alerting") and actor_part in str(a.get("labels", {}).get("actor", "")):
                            return True
    except (httpx.HTTPError, KeyError, ValueError):
        return False
    return False


def wait_alert(rule: str, since: float, actor_part: str = "", timeout: float = 150.0) -> str | None:
    """메일(since 뒤) 또는 Grafana의 「울리는 중」 상태를 기다린다. 돌려준 값: 찾은 것의 설명"""
    t0 = time.time()
    while True:
        found = [m for m in mails(since) if rule in m["subject"] and actor_part in m["subject"] and m["subject"].startswith(SUBJECT_PREFIX)]
        if found:
            return "메일 「" + found[0]["subject"] + "」"
        if time.time() - t0 > 20 and firing(rule, actor_part):
            return f"Grafana에서 {rule} 경보가 이미 울리는 중(같은 묶음의 새 메일은 반복 간격 뒤)"
        if time.time() - t0 > timeout:
            return None
        time.sleep(5)


def wait_mail(contains: str, since: float, timeout: float = 150.0) -> dict | None:
    """경보 평가 주기(30초)·묶음 대기(10초)·수집 지연 때문에 메일은 바로 오지 않는다. 기다린다."""
    t0 = time.time()
    while True:
        found = [m for m in mails(since) if contains in m["subject"] and m["subject"].startswith(SUBJECT_PREFIX)]
        if found:
            return found[0]
        if time.time() - t0 > timeout:
            return None
        time.sleep(5)


def now_utc() -> float:
    return datetime.now(timezone.utc).timestamp()
