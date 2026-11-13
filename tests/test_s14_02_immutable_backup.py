"""14-2 불변 백업과 복구 훈련 · T36 · A11.

시작 지점([회사 설정] 14장 시작 모습): 백업 작업이 앱 MinIO 계정으로 앱 버킷 backups/mysql/에 덤프를 쓴다. 앱 계정으로 백업을 지울 수 있고,
  관리자 앱의 「백업 목록 보기」가 앱 버킷 backups/를 직접 읽고, 잠금 버킷·복구 훈련이 없다 → 레드.
완료 지점(A11):
  ① make replay T=36 그린: 앱 계정으로 백업 저장소에 닿지 않고, backup-writer로도 지우기 거부
  ② 새 백업이 잠금 버킷에 생기고 보존(모드·기한)이 붙어 있다 · 해시·복구 시점 메타데이터
  ③ 지우기 시도가 backup.denied.delete(backup-audit)로 남는다 · 보존 기간 안에는 백업 저장소 루트도 잠긴 버전을 못 지운다(COMPLIANCE)
  ④ make restore-drill이 다른 컨테이너에 복구하고 확인 시험을 통과하며 RTO·RPO를 숫자로 남긴다(backup.restore.drill)
  ⑤ 관리자 앱은 백업에 닿지 않는다(GET /daon/backups → 410, 관리자 앱 컨테이너는 backup 망에 없다)
정상 시험: 앱 계정은 상품 사진 버킷(mall)을 그대로 읽고 쓴다.
비밀 키·비밀번호는 출력하지 않는다. 지우기 시험은 가짜 시드 덤프에만 하고, 잠긴 버전은 지워지지 않으므로 되돌릴 것이 없다.
"""
import json
import subprocess
import time

import pytest

from daon import backup as bk
from daon.config import ENV, ROOT
from replay import _logs
from replay import t36_backup_delete as t36
from replay._http import admin, admin_login


def _vault_or_fail():
    bk.require_vault()


def test_replay_t36_is_blocked(stack):
    r = t36.run(stack)
    assert not r.vulnerable, "기대: 앱 계정으로 백업 저장소에 닿지 않고 백업 작업 계정으로도 지우지 못함.\n" + r.show()
    assert bk.vault_on(), "기대: 백업 전용 저장소 backup-vault가 있음(A11)"


def test_app_containers_are_not_on_backup_network():
    _vault_or_fail()
    for svc in ("mall-admin", "mall-portal"):
        r = subprocess.run(["docker", "inspect", "-f", "{{json .NetworkSettings.Networks}}", bk.container(svc)], capture_output=True, text=True)
        nets = set(json.loads(r.stdout or "{}"))
        assert nets, f"{svc} 컨테이너를 찾지 못했습니다(make up)"
        assert f"{bk.project()}_backup" not in nets, f"기대: {svc}는 backup 망에 없음(다른 망). 실제 {sorted(nets)}"


def test_new_backup_is_locked_with_retention_and_hash():
    _vault_or_fail()
    line = bk.backup_now()
    assert "잠금 버킷" in line or "daon-backup" in line, f"기대: 백업이 잠금 버킷에 올라감. 실제 「{line}」"
    rows = bk.list_backups()
    assert rows, "기대: 잠금 버킷 mysql/에 백업이 있음"
    info = bk.stat(str(rows[-1]["key"]))
    assert info["mode"] in ("COMPLIANCE", "GOVERNANCE"), f"기대: 보존 모드가 붙음(랩 기본 COMPLIANCE). 실제 {info['mode']!r}"
    assert info["until"], "기대: 보존 기한이 있음"
    assert info["sha256"] and len(info["sha256"]) == 64, "기대: 해시(sha256)를 메타데이터로 남김"
    assert info["snapshot_at"], "기대: 복구 시점(덤프 시작 시각, UTC)을 메타데이터로 남김"


def test_writer_cannot_delete_and_attempt_is_logged():
    _vault_or_fail()
    rows = bk.list_backups() or (bk.backup_now() and bk.list_backups())
    key = str(rows[-1]["key"])
    since = _logs.now()
    status = bk.delete_probe_as_writer(key)
    assert status == 403, f"기대: backup-writer(올리기만)의 삭제 HTTP 403. 실제 {status}"
    assert key in [str(x["key"]) for x in bk.list_backups()], "기대: 백업이 그대로 있음"
    e = _logs.wait_event("backup-audit", since, "backup.denied.delete", "14-2", timeout=15)
    assert e and e.get("actor") == "minio:backup-writer", f"기대: backup.denied.delete(actor minio:backup-writer). 실제 {e}"
    assert str(e.get("target", "")).startswith(f"object:{bk.BUCKET}/"), f"기대: target object:{bk.BUCKET}/…. 실제 {e.get('target')}"


def test_locked_version_survives_vault_root_delete():
    """보존 기간 안의 버전은 백업 저장소 루트도 못 지운다(COMPLIANCE). 루트 값은 환경 변수로만 넘긴다"""
    _vault_or_fail()
    rows = bk.list_backups() or (bk.backup_now() and bk.list_backups())
    key = str(rows[-1]["key"])
    env = {"K": ENV.get("DAON_BACKUP_VAULT_ROOT_USER", "daon-vault-root"), "S": ENV.get("DAON_BACKUP_VAULT_ROOT_PASSWORD", "lab-fake-vault-root-0001")}
    versions = bk.mc(f"mc ls --versions --json vault/{bk.BUCKET}/mysql/{key}", net="backup", env=env)
    ids = [json.loads(line).get("versionId") for line in versions.stdout.splitlines() if line.startswith("{")]
    assert ids and ids[0], f"기대: 잠긴 백업의 버전 ID. 실제 {versions.stderr[-120:]}"
    r = bk.mc(f"mc rm --version-id {ids[0]} vault/{bk.BUCKET}/mysql/{key} >/dev/null 2>&1 && echo DELETED || echo DENIED",
              net="backup", env=env)
    out = r.stdout.strip()
    assert out.endswith("DENIED"), f"기대: 보존 기간 안의 버전은 루트도 못 지움(COMPLIANCE). 실제 {out[-40:]!r}"


def test_restore_drill_passes_and_reports_rto_rpo():
    _vault_or_fail()
    if not bk.list_backups():
        bk.backup_now()
    r = bk.restore_drill(echo=lambda *_: None)
    assert r["checks"].get("해시 일치"), f"기대: 받은 백업의 해시가 메타데이터와 같음. 실제 {r['checks']}"
    assert r["ok"], f"기대: 다른 컨테이너에 복구하고 확인 시험(주문·회원·alice 주문) 통과. 실제 {r['checks']} {r.get('counts')}"
    assert r["rto_seconds"] > 0 and r["rpo_seconds"] is not None, f"기대: RTO·RPO 숫자. 실제 RTO {r['rto_seconds']} RPO {r['rpo_seconds']}"
    import sys
    sys.path.insert(0, str(ROOT / "tools"))
    import secevent
    last = [e for e in secevent.read("backup.restore.drill") if e.get("lesson") == "14-2"]
    assert last and str(r["key"]) in str(last[-1].get("target")), "기대: 도구 이벤트 backup.restore.drill(reports/security-events.jsonl)"


def test_admin_app_no_longer_reads_backups():
    with admin() as c:
        r = c.get("/daon/backups", headers={"Authorization": admin_login(c, "admin")})
    assert r.status_code == 410, f"기대: 관리자 앱의 백업 목록 보기는 410(앱은 백업에 닿지 않는다). 실제 HTTP {r.status_code}"


@pytest.mark.normal
def test_app_key_still_works_for_product_photos():
    env = t36.app_env()
    probe = f"lab-s14-probe-{int(time.time())}.txt"
    r = bk.mc(f"echo daon > /tmp/p && mc cp --quiet /tmp/p app/mall/{probe} >/dev/null && mc cat app/mall/{probe} && mc rm app/mall/{probe} >/dev/null",
              net="data", alias="app", host="minio:9000", env=env)
    assert r.returncode == 0 and "daon" in r.stdout, "기대: 앱 계정은 상품 사진 버킷 mall을 그대로 읽고 쓴다"
