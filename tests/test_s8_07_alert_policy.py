"""8-7 경보 대응 시간과 알림 피로.

시작 지점: 알림 정책이 한 길(등급 없음, 4시간마다 다시) → 메일 제목에 등급이 없고 런북 주소도 없다 → 레드.
완료 지점: 등급(P1·P2·P3)별 길, event·actor로 묶고 30분 안에는 다시 보내지 않음, 다시 알림(lead@) 길,
          메일 제목에 [P2] 같은 등급, 본문에 런북 주소(docs/runbooks/<event>.md).
"""
import os
import time

import pytest
import yaml

from daon import observe
from daon.config import ROOT
from replay import _obs
from replay import t01_order_idor as t01

NOTIFY = ROOT / "infra" / "observability" / "rules" / "notify.yaml"
WAIT = float(os.environ.get("DAON_ALERT_WAIT", "150"))


def policy() -> dict:
    assert NOTIFY.exists(), "기대: 알림 정책 파일 infra/observability/rules/notify.yaml(8-4)"
    return yaml.safe_load(NOTIFY.read_text("utf-8"))["policies"][0]


def test_grouping_and_repeat_interval():
    p = policy()
    assert set(p.get("group_by", [])) == {"event", "actor"}, f"기대: event·actor로 묶음(trace_id는 넣지 않음). 실제 {p.get('group_by')}"
    assert p.get("repeat_interval") == "30m", f"기대: 같은 묶음은 30분 안에 다시 보내지 않음. 실제 {p.get('repeat_interval')}"


def test_severity_routes_and_escalation_route():
    routes = policy().get("routes", [])
    matched = {tuple(m) for r in routes for m in r.get("object_matchers", [])}
    for sev in ("P1", "P2", "P3"):
        assert ("severity", "=", sev) in matched, f"기대: 등급 {sev} 길"
    assert ("event", "=", "alert.escalated.unacknowledged") in matched, "기대: 확인되지 않은 P1을 한 단계 위 담당에게 보내는 길"
    lead = [r for r in routes if ("event", "=", "alert.escalated.unacknowledged") in {tuple(m) for m in r.get("object_matchers", [])}]
    assert routes.index(lead[0]) == 0, "기대: 다시 알림 길이 P1 길보다 먼저(같은 P1이라도 lead@로)"


def test_mail_subject_has_severity_and_body_has_runbook():
    assert policy().get("repeat_interval") == "30m", "기대: 8-7 알림 정책(등급·묶기)이 들어가 있음"
    assert _obs.wait_grafana(timeout=90), "기대: Grafana가 떠 있음"
    start = _obs.now_utc()
    for _ in range(6):
        t01.run("spring")
    mail = _obs.wait_mail("[P2] access.denied.not_owner", since=start - 900, timeout=WAIT)
    assert mail, f"기대: 제목에 등급이 붙은 경보 메일 「[다온 경보][P2] access.denied.not_owner …」({int(WAIT)}초 안)"
    assert "docs/runbooks/access.denied.not_owner.md" in _obs.mail_text(mail["id"]), "기대: 본문에 런북 주소"


@pytest.mark.normal
def test_unacknowledged_p1_is_escalated_once():
    """make drill-report의 판단만 따로(메일·시계 없이): 15분이 지난 확인 안 된 P1 → 한 번만 다시 알림"""
    now = time.time()
    mails = [{"subject": "[다온 경보][P1] audit.chain.broken tool:audit-verify", "at": now - 20 * 60},
             {"subject": "[다온 경보][P1] audit.chain.broken tool:audit-verify", "at": now - 19 * 60},
             {"subject": "[다온 경보][P2] access.denied.not_owner member:102", "at": now - 30 * 60},
             {"subject": "[다온 경보][P1] egress.denied.internal_address admin:admin", "at": now - 20 * 60}]
    sent = []
    late = observe.escalate(mails, {"egress.denied.internal_address": now - 10 * 60}, 15, now,
                            emit=lambda *a, **k: sent.append(a))
    assert [observe.alert_name_of(m["subject"]) for m in late] == ["audit.chain.broken"], f"기대: 확인 안 된 P1 하나만. 실제 {late}"
    assert sent and sent[0][0] == "alert.escalated.unacknowledged" and sent[0][3] == "alert:audit.chain.broken"
