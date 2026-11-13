"""7-6 이미지 서명과 배포 전 확인 · T19.

시작 지점: CI가 서명하지 않고, 배포 스크립트가 태그만 보고 그대로 띄운다 → CI 밖에서 올린 다른 이미지(라벨 unsigned-demo)도 배포된다 → 레드.
완료 지점: CI가 올린 이미지에 서명과 출처 증명(커밋·작업 번호·시각)을 붙이고, deploy.sh가 띄우기 전에 서명·증명·커밋(main에 있나)을 확인한다.
확인을 못 해도(레지스트리 꺼짐 등) 거부한다. 통과한 이미지는 @sha256 다이제스트로 뜬다.
서명 키는 도커 볼륨 ci-keys(랩이 만든 한 쌍, git에 없음)에 있고, 값은 어디에도 찍지 않는다. 인터넷·투명성 로그는 쓰지 않는다.
"""
import base64
import json
import os
import subprocess
import tempfile
import time
from pathlib import Path

import pytest

from daon import ci
from daon.config import ROOT
from replay import NotReady
from replay import t19_unsigned_image as t19


def need_ci():
    if not (ROOT / "tools/deploy/deploy.sh").exists():
        raise NotReady("tools/deploy/deploy.sh가 없습니다(7-5 완료 지점 이후에서 하세요)")
    try:
        ci.require()
    except ci.CiNotReady as e:
        raise NotReady(str(e))


def events(tmp: str) -> list[dict]:
    f = Path(tmp) / "security-events.jsonl"
    return [json.loads(l) for l in f.read_text("utf-8").splitlines()] if f.exists() else []


@pytest.fixture
def clean_staging():
    t19.remove_staging()
    yield
    t19.remove_staging()


def test_deploy_refuses_swapped_unsigned_image_and_logs_event(clean_staging, tmp_path):
    need_ci()
    digest = ci.image_swap()
    r = t19.deploy(str(tmp_path))
    assert r.returncode == 1, f"기대: 서명 없는 이미지는 배포 거부(종료 코드 1). 실제 {r.returncode}\n{r.stdout[-300:]}"
    assert t19.staging_label() is None, "기대: 거부했으면 스테이징 컨테이너가 뜨지 않음"
    e = [x for x in events(str(tmp_path)) if x["event"] == "deploy.denied.unsigned_image"]
    assert e, "기대: deploy.denied.unsigned_image 이벤트가 남음"
    assert e[0]["reason"] == "서명 없음" and e[0]["target"] == f"image:daon/mall-portal@{digest[:19]}", f"기대: reason 「서명 없음」, target image:daon/mall-portal@sha256:<앞 12자>. 실제 {e[0]}"
    assert e[0]["actor"] == "deploy:lab" and e[0]["lesson"] == "7-6", "기대: actor deploy:lab, lesson 7-6"


def test_signed_ci_image_is_deployed_by_digest(clean_staging, tmp_path, monkeypatch):
    need_ci()
    monkeypatch.setenv("DAON_REPORTS_DIR", str(tmp_path))
    if not (ci.IN_DIR / "mall-portal.tar").exists():
        ci.build_image("ci-lesson")
    run = ci.run_pipeline("ci: 서명 시험")
    assert run.status == "success", f"기대: 파이프라인 성공. 실제 {run.status} {run.jobs}"
    assert "서명했습니다" in run.log, "기대: CI가 서명하고 출처 증명을 붙임(로그에 「서명했습니다」)"
    r = t19.deploy(str(tmp_path))
    assert r.returncode == 0, f"기대: CI가 서명한 이미지는 배포됨. 실제 종료 코드 {r.returncode}\n{r.stdout[-300:]}"
    got = t19.staging_label()
    assert got and "@sha256:" in got, f"기대: 다이제스트(@sha256:)로 뜸. 실제 {got}"
    assert not got.startswith("unsigned-demo"), "기대: 바꿔치기한 이미지가 아님"
    assert not events(str(tmp_path)) or not [x for x in events(str(tmp_path)) if x["event"] == "deploy.denied.unsigned_image"], "기대: 정상 배포는 거부 이벤트 없음"


def test_deploy_fails_closed_when_registry_is_unreachable(clean_staging, tmp_path):
    need_ci()
    subprocess.run(["docker", "stop", "daon-registry-1"], capture_output=True, timeout=60)
    try:
        r = t19.deploy(str(tmp_path))
    finally:
        subprocess.run(["docker", "start", "daon-registry-1"], capture_output=True, timeout=60)
        time.sleep(3)
    assert r.returncode == 1, "기대: 확인 자체를 못 하면 배포하지 않음(종료 코드 1)"
    e = [x for x in events(str(tmp_path)) if x["event"] == "deploy.denied.unsigned_image"]
    assert e and e[0]["reason"] == "확인 불가", f"기대: reason 「확인 불가」. 실제 {e}"


def test_provenance_commit_must_be_on_main(clean_staging, tmp_path):
    """서명은 맞지만 출처 증명의 커밋이 main에 없는 이미지(= 키를 가진 쪽이 엉뚱한 커밋을 적었다)도 거부한다."""
    need_ci()
    # 서명 키는 러너(uid 10002)만 읽는다. 시험이 같은 방식으로 한 번 서명한다: 이미지는 CI 밖에서 올린 것, 커밋은 main에 없는 값
    ci.image_swap()
    d = ci.registry_digest()
    assert d, "레지스트리에 이미지가 있어야 함"
    cfg = tempfile.mkdtemp()
    admin = f"lab-admin:{ci._pw()}"
    Path(cfg, "config.json").write_text(json.dumps({"auths": {"registry.local:5000": {"auth": base64.b64encode(admin.encode()).decode()}}}))
    Path(cfg, "p.json").write_text(json.dumps({"commit": "0" * 40, "run": "0", "repo": "daon/market"}))
    os.chmod(cfg, 0o755)
    for f in ("config.json", "p.json"):
        os.chmod(Path(cfg, f), 0o644)
    ref = f"registry.local:5000/daon/mall-portal@{d}"
    base = ["docker", "run", "--rm", "--network", ci.NET, "--user", "10002", "-e", "DOCKER_CONFIG=/dc", "-e", "COSIGN_PASSWORD=" + ci.ENV.get("DAON_CI_SIGN_PASSWORD", "lab-fake-sign-0001"),
            "-v", f"{cfg}:/dc", "-v", "daon_ci-keys:/keys:ro", "--entrypoint", "cosign", ci.TOOLS_IMAGE]
    for args in (["sign", "--key", "/keys/cosign.key", "--tlog-upload=false", "-y", ref],
                 ["attest", "--key", "/keys/cosign.key", "--tlog-upload=false", "-y", "--type", "custom", "--predicate", "/dc/p.json", ref]):
        r = subprocess.run(base + args, capture_output=True, text=True, timeout=120)
        assert r.returncode == 0, "시험 준비(서명) 실패: " + r.stderr.strip().splitlines()[-1][:160]
    r = t19.deploy(str(tmp_path))
    assert r.returncode == 1, f"기대: 출처 커밋이 main에 없으면 거부. 실제 종료 코드 {r.returncode}\n{r.stdout[-300:]}"
    e = [x for x in events(str(tmp_path)) if x["event"] == "deploy.denied.unsigned_image"]
    assert e and e[0]["reason"] == "출처 증명의 커밋이 main에 없음", f"기대: reason 「출처 증명의 커밋이 main에 없음」. 실제 {e}"


def test_replay_t19_is_green():
    r = t19.run()
    assert not r.vulnerable, "기대: T19 그린. 실제:\n" + "\n".join(r.evidence)


@pytest.mark.normal
def test_normal_login_still_works(stack):
    from replay._http import portal, portal_login
    with portal() as c:
        assert portal_login(c, "alice").startswith("Bearer "), "기대: 정상 로그인은 그대로 된다"
