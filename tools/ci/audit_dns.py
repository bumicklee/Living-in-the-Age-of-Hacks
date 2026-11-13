#!/usr/bin/env python3
"""DNS 영역 파일 점검(11-5, T32): 저장소 안 영역 파일과 자원 목록만 읽는다. 실제 DNS는 조회하지 않는다.

    python tools/ci/audit_dns.py [영역 파일] [자원 목록]     (기본: infra/dns/daon.example.zone, infra/dns/inventory.yaml)
    make audit-dns                                            (같은 점검을 PC에서)

규칙
  ① 주인 없는 레코드: CNAME·MX·NS가 바깥 이름을 가리키는데 자원 목록에 「active」로 없다(지운 자원·모르는 자원)
     A·AAAA가 자원 목록의 우리 주소(addresses)가 아닌 곳을 가리킨다
     영역 안 이름을 가리키는데 그 이름이 영역에 없다
  ② SPF include: 자원 목록 mail_senders에 「active」로 없는 업체(계약 끝·모르는 업체)
  ③ 와일드카드 레코드(*.…) 금지: 하나가 모든 하위 이름을 가리켜 점검이 놓치기 쉽다
문제마다 config.audit.failed 한 줄(같은 키, tools/secevent.py)을 남긴다. CI 안이면 actor는 ci:audit-dns, PC면 tool:audit-dns.
종료 코드: 0 통과, 1 문제 있음, 2 파일을 읽지 못함.

이 파일은 CI 러너(파이썬만 있고 PyYAML 없음)에서도 돈다. 그래서 자원 목록은 아래의 작은 읽기 함수로 읽는다.
"""
from __future__ import annotations

import ipaddress
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from secevent import emit  # noqa: E402

ZONE = ROOT / "infra" / "dns" / "daon.example.zone"
INVENTORY = ROOT / "infra" / "dns" / "inventory.yaml"
TYPES = {"A", "AAAA", "CNAME", "MX", "NS", "TXT", "SOA", "CAA", "SRV"}


@dataclass
class Record:
    line: int
    name: str          # 끝에 점이 붙은 전체 이름(소문자)
    rtype: str
    data: str


@dataclass
class Finding:
    rule: str          # dangling | spf | wildcard
    target: str        # dns:<이름> 또는 spf:<include>
    reason: str
    line: int


# ── 읽기 ──
def _strip_comment(line: str) -> str:
    out, quoted = [], False
    for ch in line:
        if ch == '"':
            quoted = not quoted
        if ch == ";" and not quoted:
            break
        out.append(ch)
    return "".join(out).rstrip()


def _fqdn(name: str, origin: str) -> str:
    name = name.lower()
    if name == "@":
        return origin
    return name if name.endswith(".") else f"{name}.{origin}"


def parse_zone(text: str) -> tuple[str, list[Record]]:
    """영역 파일의 작은 부분집합: $ORIGIN, $TTL, 한 줄에 레코드 하나(괄호로 여러 줄 쓰기는 지원하지 않음)."""
    origin, last, out = "", "", []
    for no, raw in enumerate(text.splitlines(), 1):
        line = _strip_comment(raw)
        if not line.strip():
            continue
        if line.startswith("$ORIGIN"):
            origin = line.split()[1].lower()
            origin = origin if origin.endswith(".") else origin + "."
            continue
        if line.startswith("$"):
            continue
        parts = line.split()
        if raw[0] in " \t":                      # 이름 칸이 비면 앞 레코드와 같은 이름
            name = last
        else:
            name = _fqdn(parts.pop(0), origin)
        while parts and (parts[0].isdigit() or parts[0].upper() in ("IN", "CH", "HS")):
            parts.pop(0)
        if not parts or parts[0].upper() not in TYPES:
            continue
        rtype = parts.pop(0).upper()
        out.append(Record(no, name, rtype, " ".join(parts)))
        last = name
    return origin, out


def parse_inventory(text: str) -> dict:
    """자원 목록(YAML의 작은 부분집합): 맨 위 키, 글자 목록(- 값), 사전 목록(- 키: 값 …). 주석은 #."""
    data: dict = {}
    key, item = None, None
    for raw in text.splitlines():
        line = re.sub(r"\s+#.*$", "", raw) if not raw.lstrip().startswith("#") else ""
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip())
        s = line.strip()
        if indent == 0:
            k, _, v = s.partition(":")
            key, item = k.strip(), None
            data[key] = v.strip() if v.strip() else []
            continue
        if s.startswith("- "):
            body = s[2:].strip()
            if ":" in body and not re.match(r"^[\d.:a-fA-F]+$", body):
                k, _, v = body.partition(":")
                item = {k.strip(): v.strip()}
                data[key].append(item)
            else:
                item = None
                data[key].append(body)
            continue
        if item is not None and ":" in s:
            k, _, v = s.partition(":")
            item[k.strip()] = v.strip()
    return data


def load(zone: Path = ZONE, inventory: Path = INVENTORY) -> tuple[str, list[Record], dict]:
    origin, records = parse_zone(zone.read_text("utf-8"))
    return origin, records, parse_inventory(inventory.read_text("utf-8"))


# ── 점검 ──
def _active(items: list, field: str) -> set[str]:
    return {str(i.get(field, "")).lower() for i in items if isinstance(i, dict) and i.get("status") == "active"}


def _known(items: list, field: str) -> dict[str, str]:
    return {str(i.get(field, "")).lower(): str(i.get("status", "")) for i in items if isinstance(i, dict)}


def audit(origin: str, records: list[Record], inv: dict) -> list[Finding]:
    found: list[Finding] = []
    names = {r.name for r in records}
    targets_ok = _active(inv.get("resources") or [], "target")
    targets_all = _known(inv.get("resources") or [], "target")
    addrs = set()
    for a in inv.get("addresses") or []:
        try:
            addrs.add(ipaddress.ip_address(str(a)))
        except ValueError:
            pass
    senders_ok = _active(inv.get("mail_senders") or [], "spf_include")
    senders_all = _known(inv.get("mail_senders") or [], "spf_include")

    for r in records:
        short = r.name.rstrip(".")
        if r.name.startswith("*."):
            found.append(Finding("wildcard", f"dns:{short}", "와일드카드 레코드(금지 규칙)", r.line))
            continue
        if r.rtype in ("CNAME", "MX", "NS"):
            to = r.data.split()[-1].lower()
            to = to if to.endswith(".") else _fqdn(to, origin)
            if to.endswith(origin):
                if to not in names:
                    found.append(Finding("dangling", f"dns:{short}", "영역 안의 없는 이름을 가리킴", r.line))
            elif to not in targets_ok:
                state = targets_all.get(to)
                why = "가리키는 자원이 목록에서 지워짐(deleted)" if state == "deleted" else (
                    f"가리키는 자원이 사용 중이 아님({state})" if state else "가리키는 자원이 목록에 없음")
                found.append(Finding("dangling", f"dns:{short}", why, r.line))
        elif r.rtype in ("A", "AAAA"):
            try:
                ip = ipaddress.ip_address(r.data.split()[0])
            except ValueError:
                found.append(Finding("dangling", f"dns:{short}", "주소 모양이 아님", r.line))
                continue
            if ip not in addrs:
                found.append(Finding("dangling", f"dns:{short}", "우리 주소 목록에 없는 주소를 가리킴", r.line))
        elif r.rtype == "TXT" and "v=spf1" in r.data.lower():
            for inc in re.findall(r"include:([^\s\"]+)", r.data, flags=re.I):
                inc_l = inc.lower().rstrip(".")
                if inc_l not in senders_ok:
                    state = senders_all.get(inc_l)
                    why = "SPF include가 계약 끝난 발송 업체" if state == "ended" else "SPF include가 발송 업체 목록에 없음"
                    found.append(Finding("spf", f"spf:{inc_l}", why, r.line))
    return found


def actor() -> str:
    return "ci:audit-dns" if (os.environ.get("GITEA_ACTIONS") or os.environ.get("CI")) else "tool:audit-dns"


def run(zone: Path = ZONE, inventory: Path = INVENTORY, *, emit_events: bool = True, echo: bool = True) -> list[Finding]:
    origin, records, inv = load(zone, inventory)
    found = audit(origin, records, inv)
    if emit_events:
        who = actor()
        for f in found:
            emit("config.audit.failed", "11-5", who, f.target, f.reason, echo=echo)
    return found


def main(argv: list[str]) -> int:
    zone = Path(argv[0]) if argv else ZONE
    inv = Path(argv[1]) if len(argv) > 1 else INVENTORY
    try:
        found = run(zone, inv)
    except OSError as e:
        print(f"영역 파일·자원 목록을 읽지 못했습니다: {e}")
        return 2
    print(f"DNS 영역 파일 점검 · {zone.name} · 자원 목록 {inv.name} · 실제 DNS는 조회하지 않음")
    if not found:
        print("  문제 0건: 주인 없는 레코드·목록 밖 SPF include·와일드카드 없음")
        return 0
    for f in found:
        print(f"  고칠 것  {f.line:>3}줄  {f.target:<38} {f.reason}")
    n_d = sum(f.rule == "dangling" for f in found)
    n_s = sum(f.rule == "spf" for f in found)
    print(f"\n주인 없는 레코드 {n_d}건 · 목록 밖 SPF include {n_s}건 · 와일드카드 {sum(f.rule == 'wildcard' for f in found)}건")
    print("고친 뒤 다시: make audit-dns (자원을 지울 때는 레코드부터 지운다: infra/dns/inventory.yaml 런북)")
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
