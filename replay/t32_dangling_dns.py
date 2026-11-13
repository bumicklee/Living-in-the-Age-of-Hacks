"""T32 · 지운 행사 페이지를 아직 가리키는 DNS 레코드(서브도메인 탈취 조건)와 계약 끝난 업체의 SPF include(P6). 수업 11-5.

[교육용 추가] 저장소 안 영역 파일(infra/dns/daon.example.zone)과 자원 목록(infra/dns/inventory.yaml)만 읽는다.
실제 DNS는 조회하지 않고, 남의 자원을 확보하는 과정은 다루지 않는다.
  레드: 주인 없는 레코드 또는 목록 밖 SPF include가 있다
  그린: 0건
"""
from __future__ import annotations

from daon import boundary
from replay import Result

T = "T32"
TITLE = "지운 자원을 가리키는 DNS 레코드·계약 끝난 업체의 SPF include"
LESSON = "11-5"
SOURCE = "[교육용 추가] infra/dns/daon.example.zone의 event2025 CNAME(자원 목록에서 deleted), SPF include:_spf.mailer-old.example(ended)"
STACKS = ("spring", "express")


def run(stack: str = "spring") -> Result:
    found = boundary.dns_findings(emit_events=False)
    ev = [f"{f.line:>3}줄 {f.target}: {f.reason}" for f in found]
    if found:
        return Result(True, f"주인 없는 레코드 {sum(f.rule == 'dangling' for f in found)}건 · 목록 밖 SPF include "
                            f"{sum(f.rule == 'spf' for f in found)}건 · 와일드카드 {sum(f.rule == 'wildcard' for f in found)}건", ev)
    return Result(False, "영역 파일 문제 0건(실제 DNS는 조회하지 않음)", ev or ["make audit-dns 와 같은 점검"])
