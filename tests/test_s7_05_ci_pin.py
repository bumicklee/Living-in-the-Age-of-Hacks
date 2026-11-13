"""7-5 로컬 CI 만들기와 CI 도구 고정 · T18(CI 도구 부분).

시작 지점(A8 뼈대는 있음): 외부 단계가 @v1 태그로만 적혀 있고, 고정 검사가 없고, 러너는 root에 도커 소켓을 받았고,
레지스트리에 올리는 장기 키가 저장소 비밀값에 있다 → 레드.
완료 지점: 모든 외부 단계가 커밋 해시(ci/pins.lock과 같음), 고정 검사(tools/ci/check_pins.py)가 첫 작업, 러너 최소 권한,
장기 키 삭제 + 작업마다 받는 단기 계정, 태그를 옮겨도 바뀐 단계는 실행되지 않는다.
값(비밀번호·토큰)은 어디에도 찍지 않는다. 태그 옮기기는 로컬 Gitea 안에서만 일어나고 끝나면 되돌린다.
"""
import json
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import pytest

from daon import ci
from daon.config import ROOT
from replay import NotReady
from replay import t18_known_vuln_and_unpinned_ci as t18

WF = ROOT / ".gitea/workflows/build.yml"
CHECK = ROOT / "tools/ci/check_pins.py"
PIN = re.compile(r"^[0-9a-f]{40}$")


def need_ci():
    try:
        ci.require()
    except ci.CiNotReady as e:
        raise NotReady(str(e))


def run_check(workflow: str, lock: str, tmp: Path, env_extra: dict | None = None):
    (tmp / "ci").mkdir(exist_ok=True)
    (tmp / ".gitea/workflows").mkdir(parents=True, exist_ok=True)
    (tmp / ".gitea/workflows/build.yml").write_text(workflow, "utf-8")
    (tmp / "ci/pins.lock").write_text(lock, "utf-8")
    import os
    env = os.environ | {"DAON_REPORTS_DIR": str(tmp / "reports")} | (env_extra or {})
    r = subprocess.run([sys.executable, str(CHECK)], cwd=tmp, env=env, capture_output=True, text=True)
    ev = []
    f = tmp / "reports/security-events.jsonl"
    if f.exists():
        ev = [json.loads(l) for l in f.read_text("utf-8").splitlines()]
    return r, ev


H1, H2 = "a" * 40, "b" * 40
WFT = "jobs:\n  b:\n    steps:\n      - uses: daon-lab/scan-step@{ref}   # v1\n      - uses: ./local-step\n"


def test_all_external_steps_are_pinned_to_commit_hashes():
    un = t18.unpinned_steps()
    assert un is not None, "기대: .gitea/workflows/build.yml이 있어야 함"
    assert not un, "기대: 모든 uses:가 40자 커밋 해시. 아직 태그로만 적힌 곳: " + "; ".join(un)


def test_check_pins_denies_tag_and_logs_event(tmp_path):
    assert CHECK.exists(), "기대: tools/ci/check_pins.py(고정 검사)가 있어야 함"
    r, ev = run_check(WFT.format(ref="v1"), f"daon-lab/scan-step {H1}\n", tmp_path)
    assert r.returncode == 1, f"기대: 태그(@v1)는 종료 코드 1로 거부. 실제 {r.returncode}"
    e = [x for x in ev if x["event"] == "ci.denied.unpinned_step"]
    assert e and e[0]["target"] == "workflow:build.yml#4" and e[0]["lesson"] == "7-5", f"기대: ci.denied.unpinned_step(target workflow:build.yml#4). 실제 {ev}"
    assert e[0]["actor"] == "git:lab", "기대: actor는 git:lab"


def test_check_pins_denies_hash_that_differs_from_lock(tmp_path):
    assert CHECK.exists(), "기대: tools/ci/check_pins.py가 있어야 함"
    r, ev = run_check(WFT.format(ref=H2), f"daon-lab/scan-step {H1}\n", tmp_path)
    assert r.returncode == 1, "기대: pins.lock과 다른 해시는 거부"
    assert [x for x in ev if x["event"] == "ci.step.changed"], f"기대: ci.step.changed 이벤트. 실제 {[x['event'] for x in ev]}"


def test_check_pins_accepts_pinned_step_and_skips_local_steps(tmp_path):
    assert CHECK.exists(), "기대: tools/ci/check_pins.py가 있어야 함"
    r, ev = run_check(WFT.format(ref=H1), f"daon-lab/scan-step {H1}\n", tmp_path)
    assert r.returncode == 0 and not ev, f"기대: 고정된 단계·로컬 단계(./…)는 통과. 실제 종료 코드 {r.returncode}, 이벤트 {len(ev)}건"


def test_repo_workflow_passes_check_and_lock_matches_seed_commit():
    assert CHECK.exists(), "기대: tools/ci/check_pins.py가 있어야 함"
    r = subprocess.run([sys.executable, str(CHECK)], cwd=ROOT, capture_output=True, text=True,
                       env=__import__("os").environ | {"DAON_REPORTS_DIR": tempfile.mkdtemp()})
    assert r.returncode == 0, "기대: 내 워크플로가 고정 검사를 통과. 실제 출력:\n" + r.stdout[-400:]
    lock = (ROOT / "ci/pins.lock").read_text("utf-8")
    need_ci()
    assert ci.seed_sha() in lock, "기대: ci/pins.lock의 해시가 daon-lab/scan-step의 처음 커밋(확인한 v1)과 같음"


def test_runner_has_least_privilege():
    r = subprocess.run(["docker", "inspect", "daon-runner-1"], capture_output=True, text=True)
    assert r.returncode == 0, "기대: 러너 컨테이너(daon-runner-1)가 떠 있어야 함(make up)"
    d = json.loads(r.stdout)[0]
    hc, cfg = d["HostConfig"], d["Config"]
    binds = [m["Destination"] for m in d["Mounts"]]
    assert "/var/run/docker.sock" not in binds, "기대: 러너에 도커 소켓을 넘기지 않음(소켓이 있으면 작업이 PC의 도커 전체를 쓴다)"
    assert not hc["Privileged"], "기대: 특권 모드 아님"
    assert cfg["User"] not in ("", "0", "root", "0:0"), f"기대: 비루트로 실행. 실제 User={cfg['User']!r}"
    assert "ALL" in (hc.get("CapDrop") or []), "기대: capability 전부 제거(cap_drop: ALL)"
    assert hc["ReadonlyRootfs"], "기대: 읽기 전용 파일 시스템"
    assert set(d["NetworkSettings"]["Networks"]) == {"daon_ci-run"}, f"기대: 러너는 ci-run(internal) 망만. 실제 {list(d['NetworkSettings']['Networks'])}"


def test_long_lived_registry_key_is_gone():
    need_ci()
    r = ci.api("GET", "/repos/daon/market/actions/secrets")
    names = [x["name"] for x in r.json()] if r.status_code == 200 else []
    assert "REGISTRY_PASSWORD" not in names, "기대: 저장소 비밀값에서 장기 키(REGISTRY_PASSWORD)가 지워져 있음(이름만 확인, 값은 읽지 않음)"
    assert "secrets.REGISTRY_PASSWORD" not in WF.read_text("utf-8"), "기대: 워크플로가 장기 키를 쓰지 않음"


def test_ci_rejects_unpinned_step_before_running_it(monkeypatch, tmp_path):
    need_ci()
    monkeypatch.setenv("DAON_REPORTS_DIR", str(tmp_path))
    wf = re.sub(r"(daon-lab/scan-step)@[0-9a-f]{40}[^\n]*", r"\1@v1", WF.read_text("utf-8"))
    run = ci.run_pipeline("ci: 고정 안 된 단계 시험", override={".gitea/workflows/build.yml": wf})
    assert run.status == "failure" and run.jobs.get("lint") == "failure", f"기대: 고정 검사 작업이 실패해야 함. 실제 {run.status} {run.jobs}"
    assert "publish" not in run.jobs, "기대: 고정 검사가 실패하면 올리기 작업은 시작하지 않음"
    assert "ci.denied.unpinned_step" in run.log, "기대: CI 로그에 ci.denied.unpinned_step 줄"
    assert any(e["event"] == "ci.denied.unpinned_step" for e in __import__("daon.supply_extra", fromlist=["secevent"]).secevent.read()), "기대: reports/security-events.jsonl에도 모임"


def test_moved_tag_does_not_run_changed_step():
    need_ci()
    ran, ev = t18.dynamic()
    assert not ran, "기대: 태그를 옮겨도 바뀐 단계가 실행되지 않음. 실제:\n" + "\n".join(ev)


def test_pipeline_pushes_image_with_short_lived_account():
    need_ci()
    if not (ci.IN_DIR / "mall-portal.tar").exists():
        ci.build_image("ci-lesson")
    run = ci.run_pipeline("ci: 단기 계정 시험")
    assert run.status == "success", f"기대: 파이프라인 성공. 실제 {run.status} {run.jobs}"
    assert re.search(r"단기 계정 job-[0-9a-f]{8}", run.log), "기대: 작업이 레지스트리에 올릴 때 단기 계정(job-…)을 받음(비밀번호는 안 찍힘)"
    assert "REGISTRY_PASSWORD" not in run.log.replace("secrets.REGISTRY_PASSWORD", ""), "기대: 로그에 장기 키 이름이 없음"
    assert ci.registry_digest(), "기대: 레지스트리에 daon/mall-portal:lesson이 올라가 있음"
    t0 = time.time()
    active = None
    while time.time() - t0 < 30:
        r = subprocess.run(["docker", "exec", "daon-runner-1", "curl", "-s", "http://ci-broker:8000/status"], capture_output=True, text=True)
        active = json.loads(r.stdout)["active"] if r.stdout.strip().startswith("{") else None
        if active == 0:
            break
        time.sleep(2)
    assert active == 0, f"기대: 작업이 끝나면 단기 계정이 사라짐(발급기 active=0). 실제 {active}"


@pytest.mark.normal
def test_normal_login_still_works(stack):
    from replay._http import portal, portal_login
    with portal() as c:
        assert portal_login(c, "alice").startswith("Bearer "), "기대: 정상 로그인은 그대로 된다"
