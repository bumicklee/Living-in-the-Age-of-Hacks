"""7-3 포크의 숙명: 원본 보안 패치를 72시간 안에(도구·절차 수업, 재연 번호 없음).

make upstream-sim이 가짜 원본(.daon/upstream.git)에 「의존성 한 줄 올림」 보안 패치 커밋 하나를 만든다(커밋 시각을 고정해 해시가 같다).
시작 지점: 패치가 pom.xml에 없고 반영 기록도 없다 → 72시간이 지나면 supply.upstream_patch.overdue(WARN). 완료 지점: 패치가 들어오고 UPSTREAM.md에 반영 기록이 한 줄 있고 72시간 안이다.
「충돌이 한 번 난다」는 7-2 완료 지점의 pom.xml에 실제로 cherry-pick해서 확인한다(임시 저장소, 내 저장소는 건드리지 않는다).
"""
import json
import os
import shutil
import subprocess
import tempfile
from datetime import datetime, timedelta
from pathlib import Path

import pytest

from daon import supply_extra as se
from daon.config import ROOT

SHA12 = "5035b48f552a"


@pytest.fixture(autouse=True)
def sim():
    se.upstream_sim()


def status(env_now: str | None, tmp: Path):
    env = os.environ | {"DAON_REPORTS_DIR": str(tmp)} | ({"DAON_FAKE_NOW": env_now} if env_now else {})
    env.pop("DAON_FAKE_NOW", None) if not env_now else None
    r = subprocess.run([__import__("sys").executable, "-m", "daon", "upstream-status"], cwd=ROOT, env=env, capture_output=True, text=True)
    f = tmp / "security-events.jsonl"
    ev = [json.loads(l) for l in f.read_text("utf-8").splitlines()] if f.exists() else []
    return r, ev


@pytest.mark.normal
def test_fake_upstream_has_stable_patch_commit():
    rec = json.loads(se.UP_REC.read_text("utf-8"))
    assert rec["sha"][:12] == SHA12, f"기대: 가짜 원본 패치 커밋 해시가 모든 PC에서 같음({SHA12}). 실제 {rec['sha'][:12]}"
    log = subprocess.run(["git", "--git-dir", str(se.UP_REPO), "log", "--format=%h %s", "main"], capture_output=True, text=True).stdout
    assert "Security: bump commons-lang3" in log, "기대: 가짜 원본에 보안 패치 커밋이 있음"


@pytest.mark.normal
def test_cherry_pick_onto_7_2_pom_conflicts_once():
    pom72 = ROOT / "solutions/s7-02/spring/pom.xml"
    with tempfile.TemporaryDirectory() as d:
        w = Path(d)
        run = lambda *a: subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@e.invalid", *a], cwd=w, capture_output=True, text=True)
        run("init", "-q", "-b", "main")
        (w / "spring").mkdir()
        shutil.copy(pom72, w / "spring/pom.xml")
        run("add", "-A"); run("commit", "-qm", "base")
        run("remote", "add", "upstream", str(se.UP_REPO)); run("fetch", "-q", "upstream")
        r = run("cherry-pick", "-x", "upstream/main")
        assert r.returncode != 0 and "CONFLICT" in (r.stdout + r.stderr), "기대: 7-2에서 바꾼 pom.xml 줄 옆에 패치가 닿아 충돌이 한 번 남(교재 7-3의 흐름)"
        txt = (w / "spring/pom.xml").read_text("utf-8")
        assert "<<<<<<<" in txt and "cyclonedx-maven-plugin.version" in txt, "기대: 충돌 표시 안에 7-2에서 더한 줄과 원본 패치 줄이 함께 보임"


def test_patch_is_in_pom():
    pom = (ROOT / "spring/pom.xml").read_text("utf-8")
    assert se.PATCH_NEW in pom, "기대: 보안 패치(commons-lang3 3.21.0)가 spring/pom.xml에 들어옴(git cherry-pick)"
    assert "cyclonedx-maven-plugin" in pom, "기대: 7-2에서 더한 SBOM 설정은 그대로(충돌을 풀 때 지우지 않음)"


def test_reflect_record_row_exists_and_within_72h():
    md = (ROOT / "spring/UPSTREAM.md").read_text("utf-8")
    row = next((l for l in md.splitlines() if l.startswith("|") and SHA12 in l), None)
    assert row, f"기대: spring/UPSTREAM.md에 원본 커밋 {SHA12} 반영 기록 한 줄"
    cells = [c.strip() for c in row.strip("|").split("|")]
    assert len(cells) == 5 and cells[4].startswith(("있음", "없음")), f"기대: 칸 5개(원본 커밋·공개 시각·반영 시각·걸린 시간·충돌). 실제 {cells}"
    hours = float(cells[3].replace("시간", "").strip())
    assert hours <= 72, f"기대: 걸린 시간 72시간 이내. 실제 {hours}"
    t0 = datetime.strptime(cells[1], "%Y-%m-%d %H:%M"); t1 = datetime.strptime(cells[2], "%Y-%m-%d %H:%M")
    assert abs((t1 - t0).total_seconds() / 3600 - hours) < 0.51, "기대: 걸린 시간이 공개 시각과 반영 시각의 차이와 맞음"


def test_status_is_reflected_and_stays_quiet_even_when_clock_is_turned(tmp_path):
    r, ev = status("+74h", tmp_path)
    assert r.returncode == 0 and "반영됨" in r.stdout, f"기대: 반영된 뒤에는 시계를 74시간 돌려도 「반영됨」. 실제 {r.stdout.strip()[:100]}"
    assert not [e for e in ev if e["event"] == "supply.upstream_patch.overdue"], "기대: 반영했으면 overdue 이벤트가 없음"


@pytest.mark.normal
def test_overdue_logic_on_unpatched_pom(tmp_path, monkeypatch):
    """시계를 돌려 보는 논리는 패치가 없는 pom으로 따로 확인한다(0시간·49시간·74시간)."""
    monkeypatch.setenv("DAON_REPORTS_DIR", str(tmp_path))
    pom = ROOT / "spring/pom.xml"
    saved = pom.read_text("utf-8")
    try:
        pom.write_text(saved.replace(se.PATCH_NEW, se.PATCH_OLD), "utf-8")
        now = datetime.fromisoformat(json.loads(se.UP_REC.read_text("utf-8"))["published_at"])
        assert se.upstream_state(now + timedelta(hours=1), emit=False)["state"] == "pending"
        s49 = se.upstream_state(now + timedelta(hours=49))
        s74 = se.upstream_state(now + timedelta(hours=74))
        assert s49["state"] == "notice" and s74["state"] == "overdue"
        ev = se.secevent.read("supply.upstream_patch.overdue")
        assert [e["reason"] for e in ev] == ["원본 보안 패치 미반영 48시간 초과(미리 알림)", "원본 보안 패치 미반영 72시간 초과"], f"기대: 48시간 INFO·72시간 WARN 이벤트. 실제 {[e['reason'] for e in ev]}"
        assert ev[0]["target"] == f"upstream:mall@{SHA12}" and ev[0]["actor"] == "tool:upstream-status", f"기대: target upstream:mall@<앞 12자>. 실제 {ev[0]}"
    finally:
        pom.write_text(saved, "utf-8")
