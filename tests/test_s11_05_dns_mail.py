"""11-5 남은 DNS와 메일 서버 설정 · T31·T32(설정 점검, 실제 DNS·메일 서버에는 닿지 않음).

시작 지점: 영역 파일에 지운 행사 페이지 CNAME(event2025)·계약 끝난 업체 SPF include, 메일 서버 사본은 패치 전·bare LF 허용·
          DMARC p=none, CI 검사 작업에 DNS 점검 없음 → 레드.
완료 지점: ① make audit-dns 문제 0건 ② make audit-mail 통과 ③ CI 검사 작업(lint)이 영역 파일을 점검 ④ 레코드를 되살린 커밋은 CI가 막음.
정상 시험: 점검 도구가 문제를 찾으면 config.audit.failed(같은 키)를 남기고, 정상 레코드는 문제로 잡지 않는다.
"""
import re
from pathlib import Path

import pytest

from daon import boundary
from daon.config import ROOT
from replay import NotReady

ZONE = ROOT / "infra" / "dns" / "daon.example.zone"
WF = ROOT / ".gitea" / "workflows" / "build.yml"


def test_no_dangling_records():
    found = [f for f in boundary.dns_findings() if f.rule in ("dangling", "wildcard")]
    assert not found, "기대: 주인 없는 레코드 0건. 실제: " + " · ".join(f"{f.target}({f.reason})" for f in found)


def test_spf_includes_match_sender_list():
    found = [f for f in boundary.dns_findings() if f.rule == "spf"]
    assert not found, "기대: SPF include가 모두 발송 업체 목록의 active. 실제: " + " · ".join(f.target for f in found)


def test_mail_settings_pass():
    bad = boundary.mail_failed(boundary.mail_rows())
    assert not bad, "기대: 메일 설정 점검 통과. 고칠 것: " + " · ".join(f"{n}={v}" for n, v, _, _ in bad)


def test_ci_lint_runs_dns_audit():
    text = WF.read_text("utf-8") if WF.exists() else ""
    lint = text.split("\n  publish:", 1)[0]
    assert "tools/ci/audit_dns.py" in lint, "기대: CI 검사 작업(lint)이 tools/ci/audit_dns.py를 돌린다(실패하면 publish가 시작하지 않음)"


def test_ci_blocks_revived_record(monkeypatch, tmp_path):
    from daon import ci
    try:
        ci.require()
    except ci.CiNotReady as e:
        raise NotReady(str(e))
    monkeypatch.setenv("DAON_REPORTS_DIR", str(tmp_path))
    zone = ZONE.read_text("utf-8") + "event2025  IN CNAME daon-event2025.pages.example.   ; 시험: 지운 페이지 레코드를 되살림\n"
    run = ci.run_pipeline("ci: 지운 레코드 되살리기 시험", override={"infra/dns/daon.example.zone": zone})
    assert run.status == "failure" and run.jobs.get("lint") == "failure", f"기대: 검사 작업이 실패. 실제 {run.status} {run.jobs}"
    assert "publish" not in run.jobs, "기대: 점검이 실패하면 올리기 작업은 시작하지 않음"
    assert "config.audit.failed" in run.log, "기대: CI 로그에 config.audit.failed 줄"


@pytest.mark.normal
def test_audit_tool_reports_dangling_record_with_event(tmp_path, monkeypatch):
    monkeypatch.setenv("DAON_REPORTS_DIR", str(tmp_path))
    dns = boundary._dns()
    zone = tmp_path / "z.zone"
    zone.write_text("$ORIGIN daon.example.\n@ IN A 192.0.2.10\nold IN CNAME gone.pages.example.\n*.promo IN CNAME shop.daon.example.\n"
                    "shop IN A 192.0.2.10\n", "utf-8")
    found = dns.run(zone, ROOT / "infra" / "dns" / "inventory.yaml", echo=False)
    targets = {f.target for f in found}
    assert {"dns:old.daon.example", "dns:*.promo.daon.example"} <= targets, f"기대: 주인 없는 레코드·와일드카드를 찾음. 실제 {targets}"
    lines = (tmp_path / "security-events.jsonl").read_text("utf-8")
    assert '"event": "config.audit.failed"' in lines and '"target": "dns:old.daon.example"' in lines, "기대: config.audit.failed 한 줄(같은 키)"
    assert re.search(r'"actor": "(ci|tool):audit-dns"', lines), "기대: actor ci:audit-dns 또는 tool:audit-dns"


@pytest.mark.normal
def test_normal_records_are_not_flagged():
    dns = boundary._dns()
    origin, records, inv = dns.load()
    good = [r for r in records if r.name.split(".")[0] in ("www", "shop", "shop-lb", "api", "ns1")]
    found = dns.audit(origin, good, inv)
    assert good and not found, f"기대: 우리 주소·영역 안 이름을 가리키는 레코드는 문제 아님. 실제 {[f.target for f in found]}"
