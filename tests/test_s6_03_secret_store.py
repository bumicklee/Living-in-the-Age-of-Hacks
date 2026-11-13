"""6-3 시크릿 저장소(OpenBao)로 옮기기, 기본값이면 기동 거부 · T15.

시작 지점: ①기본값을 넣어도 앱이 시작 시도를 그대로 한다(거부 줄 없음) ②앱이 고정 계정 reader를 쓴다 ③reader/123456 접속이 된다 → 레드.
완료 지점: ①거부 + SECURITY 줄(키 이름만) ②앱은 v-portal-…·v-admin-… 동적 계정 ③reader 접속 거부, 동적 계정은 SELECT·INSERT·UPDATE·DELETE만.
①은 앱 이미지를 네트워크 없이 따로 한 번 띄워 본다(이 랩의 포트·DB와 무관, 실행 중인 앱에 영향 없음).
"""
import re
import subprocess
import uuid

import pytest
import yaml

from daon import lab
from daon.config import ROOT
from replay import _logs
from replay._http import ACCOUNTS, portal, portal_login

MYSQL = ["docker", "exec", "-i", "daon-mysql-1", "mysql", "-N", "-B"]
DEFAULT_VALUE = "123456"            # 알려진 기본값 목록에 있는 값(원본 데모 설정)


def mysql_root(sql: str) -> str:
    r = subprocess.run(MYSQL + ["-uroot", "-proot", "-e", sql], capture_output=True, text=True)
    return r.stdout.strip()


def try_boot_with_default(image: str, key_env: str) -> tuple[int | None, str]:
    """앱 이미지를 네트워크 없이 한 번 띄워 기본값 시크릿을 넣어 본다. (종료 코드, 로그) — 시간 안에 안 끝나면 (None, 로그)."""
    name = "daon-startcheck-" + uuid.uuid4().hex[:8]
    env = ["-e", f"{key_env}={DEFAULT_VALUE}", "-e", "SPRING_DATASOURCE_USERNAME=lab"]
    sw = lab.switches()
    if not sw.get("startup-check", True):
        env += ["-e", "SECUREKIT_STARTUP_CHECK_ENABLED=false"]
    cmd = ["docker", "run", "--rm", "--name", name, "--network", "none", "--memory", "512m", *env, image]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=100)
        return r.returncode, r.stdout + r.stderr
    except subprocess.TimeoutExpired as e:
        subprocess.run(["docker", "rm", "-f", name], capture_output=True)
        out = (e.stdout or b"") + (e.stderr or b"")
        return None, out.decode("utf-8", "replace") if isinstance(out, bytes) else str(out)


@pytest.mark.parametrize("image", ["daon/mall-portal:dev", "daon/mall-admin:dev"])
def test_default_secret_refuses_startup(image):
    code, log = try_boot_with_default(image, "SPRING_DATASOURCE_PASSWORD")
    assert "config.startup.denied_default_secret" in log, "기대: 기본값 시크릿이면 SECURITY config.startup.denied_default_secret 줄을 남기고 시작을 멈춤"
    assert code not in (0, None), f"기대: 앱이 시작을 거부(종료 코드 0 아님). 실제 {code}"
    assert DEFAULT_VALUE not in "\n".join(l for l in log.splitlines() if "denied_default_secret" in l), "기대: 거부 줄에는 값이 없고 설정 키 이름만"
    assert "spring.datasource.password" in log, "기대: 거부 줄의 target은 설정 키 이름"


@pytest.mark.normal
def test_non_default_secret_is_not_refused():
    """기본값이 아니면 거부하지 않는다(거부 줄 없음). 네트워크가 없어 뒤에서 다른 이유로 멈춰도 된다."""
    name = "daon-startcheck-" + uuid.uuid4().hex[:8]
    cmd = ["docker", "run", "--rm", "--name", name, "--network", "none", "--memory", "512m", "-e", "SPRING_DATASOURCE_PASSWORD=lab-not-a-default-value",
           "-e", "SPRING_DATASOURCE_USERNAME=lab", "daon/mall-portal:dev"]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=40)
        log = r.stdout + r.stderr
    except subprocess.TimeoutExpired as e:
        subprocess.run(["docker", "rm", "-f", name], capture_output=True)
        log = ""
    assert "denied_default_secret" not in log, "기대: 기본값이 아닌 값은 기동 거부 대상이 아님"


def test_apps_use_dynamic_db_accounts():
    users = set(mysql_root("SELECT DISTINCT USER FROM information_schema.PROCESSLIST WHERE DB='mall'").split())
    app_users = {u for u in users if u not in ("root", "openbao", "event_scheduler")}
    assert app_users, "기대: 앱이 mall DB에 붙어 있어야 함(앱이 떠 있는지 확인)"
    assert "reader" not in app_users, "기대: 앱이 고정 계정 reader를 쓰지 않음"
    assert all(re.fullmatch(r"v-(portal|admin)-[A-Za-z0-9]+", u) for u in app_users), f"기대: 앱 계정은 v-portal-…·v-admin-… 동적 계정. 실제 {sorted(u[:10] + '…' for u in app_users)}"
    for u in app_users:
        grants = mysql_root(f"SHOW GRANTS FOR '{u}'@'%'").splitlines()
        schema = [g for g in grants if "ON `mall`.*" in g]
        assert schema and all("ALL PRIVILEGES" not in g and not re.search(r"\b(DROP|ALTER|CREATE|GRANT OPTION|FILE|SUPER)\b", g) for g in schema), \
            "기대: mall 전체에 대한 권한은 SELECT·INSERT·UPDATE·DELETE만(표 삭제·구조 변경·권한 부여 없음)"
        assert "SELECT" in schema[0] and "INSERT" in schema[0] and "UPDATE" in schema[0], "기대: 앱이 쓰는 권한(SELECT·INSERT·UPDATE)은 있어야 함"
        tables = [g for g in grants if "ON `mall`.`" in g]          # 시작 때 마이그레이션용 표 단위 권한만
        assert all(re.search(r"ON `mall`\.`(daon_data_key|ums_member|oms_order|ums_member_receive_address)`", g) and "DROP" not in g and "GRANT OPTION" not in g for g in tables), \
            "기대: 표 단위 권한은 마이그레이션 대상 네 표에만(DROP·권한 부여 없음)"


def test_fixed_reader_account_refused():
    r = subprocess.run(MYSQL + ["-ureader", "-p123456", "-e", "SELECT 1"], capture_output=True, text=True)
    assert r.returncode != 0, "기대: 고정 계정 reader/123456 접속은 거부(계정이 없음)"


@pytest.mark.normal
def test_orders_still_work(stack):
    with portal() as c:
        auth = portal_login(c, "alice")
        r = c.get("/order/list", params={"status": -1, "pageNum": 1, "pageSize": 5}, headers={"Authorization": auth})
        assert r.status_code == 200 and r.json().get("code") == 200, f"기대: 정상 주문 목록 조회. 실제 {r.status_code} {r.text[:80]}"


def test_rotation_list_marks_63_items_rotated():
    rows = yaml.safe_load((ROOT / "infra" / "secrets" / "rotation-list.yaml").read_text("utf-8"))
    todo = [r["id"] for r in rows if r.get("lesson") == "6-3" and r["status"] != "rotated"]
    assert not todo, f"기대: 6-3 몫 시크릿은 모두 교체 완료(rotated). 남은 것 {todo}"


@pytest.mark.normal          # 문서 시험: course/SECURITY_EVENTS.md는 수업 이동이 바꾸지 않아 시작 지점에서도 그린
def test_lease_renew_failure_event_is_documented():
    ev = (ROOT / "course" / "SECURITY_EVENTS.md").read_text("utf-8")
    for name in ("config.startup.denied_default_secret", "secret.lease.renew_failed"):
        assert name in ev, f"기대: SECURITY_EVENTS.md에 {name} 행이 있어야 함"
