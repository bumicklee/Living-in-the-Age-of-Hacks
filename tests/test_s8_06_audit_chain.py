"""8-6 지울 수 없는 로그: 해시 체인과 별도 보관 · 방어 확인 T20x(make replay T=20x).

시작 지점: 감사 로그 줄에 체인 정보가 없어, 한 줄을 지워도 알 수 없다 → 레드.
완료 지점: 줄마다 seq·prev·hash. 가운데 줄을 지우면 make audit-verify가 끊긴 seq를 찾고(종료 코드 1) audit.chain.broken을 남긴다.
          끝 줄을 지우면 Loki 사본(앱이 닿지 못하는 곳)과 머리 해시가 맞지 않아 찾는다. 시험은 지운 파일을 원래대로 되돌린다.
"""
import hashlib
import json
import subprocess
import sys
import time

import pytest

from daon import observe
from daon.config import ROOT
from replay import _obs
from replay import audit_tamper
from replay.t20_bulk_read import bulk_read

sys.path.insert(0, str(ROOT / "tools"))
import secevent  # noqa: E402


def test_audit_lines_carry_chain_fields():
    calls = bulk_read("s86", calls=2)
    line = _obs.wait_audit(calls[-1][0], timeout=6)
    assert line, "기대: 감사 로그 줄이 있음(8-3)"
    assert {"seq", "prev", "hash"} <= set(line), f"기대: seq·prev·hash. 실제 키 {sorted(line)}"
    body = {k: v for k, v in line.items() if k != "hash"}
    want = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()).hexdigest()
    assert line["hash"] == want, "기대: hash = 정렬된 JSON(hash 칸 뺀 것)의 SHA-256"
    prev = _obs.wait_audit(calls[0][0], timeout=2)
    assert prev and line["prev"] == prev["hash"] and line["seq"] == prev["seq"] + 1, "기대: 다음 줄의 prev = 앞 줄의 hash"


def test_deleting_a_middle_line_is_detected():
    before = len(secevent.read("audit.chain.broken"))
    r = audit_tamper.run("spring")
    assert not r.vulnerable, "기대: 가운데 줄을 지우면 audit-verify가 찾음\n" + r.show()
    after = secevent.read("audit.chain.broken")
    assert len(after) > before and after[-1]["target"].startswith("audit:mall-admin#seq="), "기대: 도구 이벤트 audit.chain.broken(target audit:mall-admin#seq=N)"


def test_deleting_the_last_line_is_detected_by_head_hash():
    assert _obs.a05_on() and _obs.loki_ready(timeout=5), "기대: 머리 해시를 맡을 Loki(8-4 A5)"
    calls = bulk_read("s86tail", calls=2)
    last = calls[-1][0]
    assert _obs.wait_audit(last, timeout=6), "기대: 감사 로그 줄"
    assert _obs.wait_loki(f'{{stream="audit"}} |= "{last}"', timeout=60), "기대: 마지막 줄이 Loki 사본에 들어감(수집 지연 몇 초)"
    original = _obs.audit_raw()
    cut, n = audit_tamper.remove_line(original, last)
    assert n, "기대: 지울 마지막 줄을 찾음"
    try:
        _obs.audit_write(cut)
        rep = observe.verify("spring", emit=True)
    finally:
        _obs.audit_write(original)
    assert rep.status == "broken" and rep.summary.startswith("머리 해시"), f"기대: 머리 해시 불일치. 실제 {rep.status} {rep.summary}"


def test_audit_verify_passes_when_untouched():
    bulk_read("s86ok", calls=1)
    rep = observe.verify("spring", emit=False)
    assert rep.status == "ok", f"기대: 손대지 않은 파일은 통과. 실제 {rep.status} {rep.summary} {rep.details}"
    r = subprocess.run([sys.executable, "-m", "daon", "audit-verify"], cwd=ROOT, capture_output=True, text=True)
    assert r.returncode == 0, f"기대: make audit-verify 종료 코드 0. 실제 {r.returncode}\n{r.stdout[-300:]}"


@pytest.mark.normal
def test_app_network_cannot_reach_log_store():
    """앱 자격증명(앱 컨테이너)으로는 머리 해시를 맡은 Loki에 닿지도 못한다(8-4부터 obs 망 분리)."""
    from daon import stack as st
    r = st.compose("spring", "exec", "-T", "mall-admin", "sh", "-c",
                   "curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://loki:3100/ready || true", check=False, capture=True)
    assert (r.stdout or "").strip() in ("", "000"), f"기대: mall-admin에서 loki:3100에 닿지 않음. 실제 {r.stdout!r}"


@pytest.mark.normal
def test_admin_read_still_writes_audit_line():
    calls = bulk_read("s86n", calls=1)
    assert calls[0][1] == 200 and _obs.wait_audit(calls[0][0], timeout=6), "기대: 관리자 조회와 감사 로그는 그대로"
