"""6-2 설정 파일·git 이력·이미지 속 시크릿 찾기 · T15.

시작 지점: 작업 트리에 평문 자격증명이 있고, 규칙 파일(.gitleaks.toml)·커밋 훅이 없고, 미끼 토큰 거부가 없다 → 레드.
완료 지점: 보강 규칙이 yml 평문을 잡고 오탐은 안 잡으며, 작업 트리·이미지 0건, 훅이 시크릿 든 커밋을 거부, 미끼 토큰은 거부+이벤트.
값은 어디에도 찍지 않는다(건수만). 스캔은 이 랩 저장소와 이 PC의 이미지만 읽는다.
"""
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

import pytest
import yaml

from daon import secretscan as ss
from daon.config import ROOT
from replay import _logs
from replay._http import admin, portal

CANARY = "daon-canary-postman-do-not-use"
POSITIVE = """spring:
  datasource:
    password: sample-value-1
  rabbitmq:
    password: mall
minio:
  secretKey: sample-value-2
"""
NEGATIVE = """spring:
  datasource:
    password: ${DAON_DB_PASSWORD}
  data:
    redis:
      password: # 비어 있음
secure:
  note: "password 라는 낱말만 있다"
"""


def _scan_text(text: str, name: str) -> list[dict]:
    cfg = ROOT / ".gitleaks.toml"
    assert cfg.exists(), "기대: 저장소 맨 위에 .gitleaks.toml(보강 규칙)이 있어야 함"
    with tempfile.TemporaryDirectory() as d:
        (Path(d) / name).write_text(text, "utf-8")
        r = subprocess.run(["gitleaks", "dir", d, "-c", str(cfg), "--no-banner", "--redact", "--exit-code", "0", "-f", "json", "-r", d + "/out.json"],
                           capture_output=True, text=True)
        import json
        return json.loads((Path(d) / "out.json").read_text() or "[]")


def test_rules_catch_plain_yaml_credentials_and_skip_placeholders():
    pos = _scan_text(POSITIVE, "application-sample.yml")
    assert len(pos) >= 3, f"기대: 견본 yml의 평문 자격증명 3줄 이상을 잡아야 함. 실제 {len(pos)}건"
    neg = _scan_text(NEGATIVE, "application-sample.yml")
    assert len(neg) == 0, f"기대: 자리 표시·빈 값·낱말만 있는 줄은 오탐이 없어야 함. 실제 {len(neg)}건"


def test_default_rules_miss_what_strong_rules_catch():
    """기본 규칙은 모양 없는 평문 비밀번호를 놓친다(그래서 규칙을 보강한다)."""
    with tempfile.TemporaryDirectory() as d:
        (Path(d) / "application-sample.yml").write_text(POSITIVE, "utf-8")
        cfg = ss.config("default")
        r = subprocess.run(["gitleaks", "dir", d, "-c", str(cfg), "--no-banner", "--redact", "--exit-code", "0", "-f", "json", "-r", d + "/o.json"],
                           capture_output=True, text=True)
        import json
        assert len(json.loads((Path(d) / "o.json").read_text() or "[]")) == 0
    assert (ROOT / ".gitleaks.toml").exists(), "기대: 보강 규칙(.gitleaks.toml)이 있어야 함"


@pytest.mark.normal
def test_default_rules_on_tree_do_not_crash():
    found = ss.scan_tree("default")
    assert isinstance(found, list)


def test_working_tree_is_clean():
    found = ss.scan_tree("auto")
    assert (ROOT / ".gitleaks.toml").exists(), "기대: .gitleaks.toml(보강 규칙)이 있어야 함"
    assert not found, f"기대: 작업 트리(spring/) 점검 0건. 실제 {len(found)}건 → " + "; ".join(ss.summarize(found)[:4])


def test_precommit_hook_rejects_staged_secret():
    hook = ROOT / "tools" / "hooks" / "pre-commit"
    assert hook.exists(), "기대: tools/hooks/pre-commit 훅 파일이 있어야 함(make hooks로 설치)"
    with tempfile.TemporaryDirectory() as d:
        repo = Path(d)
        run = lambda *a: subprocess.run(a, cwd=repo, capture_output=True, text=True)
        run("git", "init", "-q")
        shutil.copy(ROOT / ".gitleaks.toml", repo / ".gitleaks.toml")
        (repo / "application.yml").write_text(POSITIVE, "utf-8")
        run("git", "add", "application.yml")
        bad = run("sh", str(hook))
        assert bad.returncode != 0, "기대: 시크릿이 든 변경은 훅이 거부(종료 코드 0 아님)"
        assert "sample-value" not in bad.stdout + bad.stderr, "기대: 훅 출력에 값이 보이면 안 됨"
        run("git", "rm", "-q", "--cached", "application.yml")
        (repo / "application.yml").write_text(NEGATIVE, "utf-8")
        run("git", "add", "application.yml")
        ok = run("sh", str(hook))
        assert ok.returncode == 0, f"기대: 자리 표시만 있는 변경은 통과. 실제 종료 코드 {ok.returncode}"


def test_image_layers_have_no_plain_credentials():
    for image in ("mall-portal", "mall-admin"):
        found = ss.scan_image(image, "auto")
        assert not found, f"기대: {image} 이미지 속 설정 파일 점검 0건. 실제 {len(found)}건(규칙 {', '.join(sorted({f['rule'] for f in found}))})"


def test_ignore_list_requires_reasons():
    ig = ROOT / ".gitleaksignore"
    assert ig.exists(), "기대: .gitleaksignore(허용 목록)가 있어야 함. 비어 있어도 형식 설명이 있어야 함"
    lines = ig.read_text("utf-8").splitlines()
    for i, ln in enumerate(lines):
        if ln.strip() and not ln.lstrip().startswith("#"):
            assert i > 0 and lines[i - 1].lstrip().startswith("# 이유"), f"기대: 지문 {i + 1}번째 줄 바로 위에 「# 이유: …」가 있어야 함"
    cfg = (ROOT / ".gitleaks.toml").read_text("utf-8")
    assert "description" in cfg, "기대: .gitleaks.toml의 허용 항목에 description(이유)이 있어야 함"


def test_rotation_list_has_no_values():
    p = ROOT / "infra" / "secrets" / "rotation-list.yaml"
    assert p.exists(), "기대: 교체 대상 목록(infra/secrets/rotation-list.yaml)이 있어야 함"
    rows = yaml.safe_load(p.read_text("utf-8"))
    assert rows and all({"id", "what", "where", "status"} <= set(r) for r in rows), "기대: 항목마다 id·what·where·status가 있어야 함"
    assert all(r["status"] in ("pending", "rotated") for r in rows), "기대: status는 pending 또는 rotated"


@pytest.mark.parametrize("which", ["portal", "admin"])
def test_canary_token_is_rejected_and_logged(stack, which):
    since = _logs.now()
    path = "/sso/info" if which == "portal" else "/admin/info"
    with (portal() if which == "portal" else admin()) as c:
        r = c.get(path, headers={"Authorization": "Bearer " + CANARY})
    assert r.status_code == 401, f"기대: 미끼 토큰은 401. 실제 {r.status_code}"
    svc = "mall-portal" if which == "portal" else "mall-admin"
    e = _logs.wait_event(svc, since, "secret.canary_used", "6-2", stack)
    assert e, f"기대: SECURITY secret.canary_used(6-2) 줄이 {svc} 로그에 남아야 함"
    assert e["target"] == "canary:" + CANARY and CANARY not in (e.get("reason") or ""), "기대: target은 canary:<이름>"


@pytest.mark.normal
def test_normal_login_still_works(stack):
    with portal() as c:
        from replay._http import portal_login
        assert portal_login(c, "alice").startswith("Bearer "), "기대: 정상 로그인은 그대로 된다"
