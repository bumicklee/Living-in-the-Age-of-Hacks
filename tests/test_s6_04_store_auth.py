"""6-4 저장소마다 인증·기본 계정 삭제·망 분리·게이트웨이·믿는 프록시 · T16.

시작 지점(6-3 끝): MongoDB는 인증 없이 열려 있고, RabbitMQ에 기본 계정(guest·mall)이 있고, MinIO 루트는 원본 기본값이고,
  앱이 접속 비밀을 환경 변수로 받고, 저장소가 앱과 같은 망에 있고, 게이트웨이가 없다 → 레드.
완료 지점: 모두 그린. 값(비밀번호·키)은 어떤 출력에도 없다(계정 이름·건수·상태만).
시험은 서버 안에서 읽기 전용 상태만 본다. 계정 값을 맞춰 보거나 대입하지 않는다.
"""
import json
import subprocess
import time
import uuid

import httpx
import pytest

from daon import stack as lab
from daon.config import ENV, HOST, INFRA, ROOT, port
from replay import _logs
from replay._http import ACCOUNTS, ADMINS, admin, portal, portal_login
from replay.t08_upload import PNG_1x1, accepted, public_url, staff_token, upload
from replay.t09_login_guard import clear_counters

KNOWN_DEFAULTS = {l.strip().lower() for l in (ROOT / "spring/mall-security/src/main/resources/daon/known-default-secrets.txt").read_text("utf-8").splitlines()
                  if l.strip() and not l.startswith("#")} | {"guest"}


def sh(*cmd: str, timeout: int = 60) -> subprocess.CompletedProcess:
    return subprocess.run(list(cmd), capture_output=True, text=True, timeout=timeout)


def dx(service: str, *cmd: str) -> subprocess.CompletedProcess:
    return sh("docker", "exec", f"daon-{service}-1", *cmd)


def env_file() -> dict[str, str]:
    out = {}
    for line in (INFRA / ".env").read_text("utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


# ── MongoDB ──
def test_mongo_requires_authentication():
    r = dx("mongo", "mongosh", "--quiet", "--eval", "db.adminCommand({listDatabases: 1}).ok")
    assert r.returncode != 0 and r.stdout.strip() != "1", "기대: 로그인 없이는 MongoDB가 DB 목록을 주지 않음(인증 켬)"


def test_mongo_app_account_is_limited_to_its_database():
    cmd = "mongosh --quiet -u daon-portal -p \"$DAON_MONGO_APP_PASSWORD\" --authenticationDatabase admin --eval '%s'"
    own = dx("mongo", "sh", "-c", cmd % "db.getSiblingDB(\"mall-port\").runCommand({ping: 1}).ok; db.getSiblingDB(\"mall-port\").getCollectionNames().length >= 0")
    assert own.returncode == 0, "기대: 앱 계정(daon-portal)은 자기 DB(mall-port)를 쓸 수 있음"
    other = dx("mongo", "sh", "-c", cmd % "db.getSiblingDB(\"admin\").getUsers()")
    assert other.returncode != 0, "기대: 앱 계정은 admin DB(계정 목록)를 볼 수 없음(권한은 mall-port 읽기·쓰기만)"


@pytest.mark.normal
def test_portal_still_reads_and_writes_mongo(stack):
    """MongoDB를 쓰는 기능(상품 열람 기록)이 앱 계정으로 정상 동작."""
    with portal() as c:
        h = {"Authorization": portal_login(c, "alice")}
        w = c.post("/member/readHistory/create", json={"productId": 204, "productName": "다온 블루투스 스피커", "productPic": "", "productSubTitle": "", "productPrice": "49000"}, headers=h)
        assert w.json().get("code") == 200, f"기대: 열람 기록 쓰기 성공(MongoDB). 실제 {w.status_code} {w.text[:120]}"
        r = c.get("/member/readHistory/list", headers=h)
        assert r.json().get("code") == 200, "기대: 열람 기록 읽기 성공"


# ── RabbitMQ ──
def rabbit_users() -> dict[str, str]:
    r = dx("rabbitmq", "rabbitmqctl", "-q", "list_users", "--silent")
    assert r.returncode == 0, "RabbitMQ에 닿지 않습니다"
    out = {}
    for line in r.stdout.splitlines():
        parts = line.split("\t")
        if parts and parts[0]:
            out[parts[0]] = parts[1] if len(parts) > 1 else ""
    return out


def test_rabbitmq_default_accounts_are_gone():
    names = set(rabbit_users())
    assert not ({"guest", "mall"} & names), f"기대: 기본 계정(guest·원본 데모 계정)이 없음. 남은 계정 수 {len(names)}"


def test_rabbitmq_app_account_has_no_admin_tag_and_only_its_vhost():
    users = rabbit_users()
    assert "daon-portal" in users, "기대: 앱 전용 계정 daon-portal이 있음"
    assert "administrator" not in users["daon-portal"] and "management" not in users["daon-portal"], "기대: 앱 계정에 관리자·관리 화면 태그가 없음"
    perms = dx("rabbitmq", "rabbitmqctl", "-q", "list_user_permissions", "daon-portal", "--silent").stdout.splitlines()
    assert [p.split("\t")[0] for p in perms if p] == ["/mall"], f"기대: 앱 계정은 /mall 한 곳에만 권한. 실제 {len([p for p in perms if p])}곳"


# normal 아님: 앱 계정 daon-portal은 이 수업에서 만든다(시작 지점은 guest·mall로 연결)
def test_portal_is_connected_to_rabbitmq_as_app_account(stack):
    with portal() as c:
        portal_login(c, "alice")
    r = dx("rabbitmq", "rabbitmqctl", "-q", "list_connections", "user", "--silent")
    users = {l.strip() for l in r.stdout.splitlines() if l.strip()}
    assert "daon-portal" in users, f"기대: 고객 앱이 앱 계정으로 메시지 큐에 연결됨. 연결된 계정 수 {len(users)}"
    assert not ({"guest", "mall"} & users), "기대: 기본 계정으로 붙은 연결이 없음"


# ── MinIO ──
def test_minio_root_account_is_not_the_demo_default():
    user = dx("minio", "printenv", "MINIO_ROOT_USER").stdout.strip().lower()
    pw = dx("minio", "printenv", "MINIO_ROOT_PASSWORD").stdout.strip().lower()
    assert user and pw, "MinIO에 닿지 않습니다"
    assert user not in KNOWN_DEFAULTS and pw not in KNOWN_DEFAULTS, "기대: MinIO 루트 계정이 원본 기본값(알려진 기본값 목록)이 아님(값은 출력하지 않음)"


def _mc(script: str, key: str, secret: str) -> subprocess.CompletedProcess:
    """앱 전용 접근 키로 mc를 한 번 돌린다(data 망 안에서만). 키·비밀은 환경 변수로 넘기고 출력하지 않는다."""
    img = "pgsty/minio:RELEASE.2026-08-04T00-00-00Z"
    return sh("docker", "run", "--rm", "--network", "daon_data", "-e", f"K={key}", "-e", f"S={secret}", "--entrypoint", "sh", img, "-c",
              'mc alias set app http://minio:9000 "$K" "$S" >/dev/null 2>&1 && ' + script, timeout=90)


def test_minio_app_key_is_limited_to_bucket_mall():
    e = env_file()
    k, s = e.get("DAON_MINIO_APP_ACCESS_KEY", ""), e.get("DAON_MINIO_APP_SECRET_KEY", "")
    assert k and s and k.lower() not in KNOWN_DEFAULTS, "기대: .env에 앱 전용 접근 키가 있음(기본값 아님)"
    ok = _mc("mc ls app/mall >/dev/null 2>&1", k, s)
    assert ok.returncode == 0, "기대: 앱 전용 키로 버킷 mall을 볼 수 있음(앱 키가 만들어져 있음)"
    other = f"daon-notallowed-{uuid.uuid4().hex[:8]}"
    bad = _mc(f"mc mb app/{other} >/dev/null 2>&1", k, s)
    assert bad.returncode != 0, "기대: 앱 전용 키로는 새 버킷을 만들 수 없음(버킷 mall 안의 읽기·쓰기·삭제만)"
    bad2 = _mc("mc admin user list app >/dev/null 2>&1", k, s)
    assert bad2.returncode != 0, "기대: 앱 전용 키로는 MinIO 관리(계정 목록)를 할 수 없음"


@pytest.mark.normal          # 준비 스크립트 멱등 시험: 시작 지점의 원본 스크립트도 다시 돌릴 수 있어야 함
def test_minio_init_can_run_again():
    """준비 스크립트는 몇 번을 돌려도 같은 결과(멱등)."""
    r = sh("docker", "start", "-a", "daon-minio-init-1", timeout=90)
    assert r.returncode == 0, f"기대: minio-init을 다시 돌려도 성공. 끝 줄: {r.stdout.strip().splitlines()[-1:]}"


@pytest.mark.normal
def test_product_photo_upload_and_public_read_still_work(stack):
    token = staff_token()
    status, body = upload(PNG_1x1, "lab.png", token=token)
    assert accepted(status, body), f"기대: 앱 전용 키로 상품 사진 올리기 성공. 실제 {status} {body.get('message')}"
    r = httpx.get(public_url(body["data"]["url"]), timeout=10)
    assert r.status_code == 200 and r.content[:4] == b"\x89PNG", "기대: PC의 파일 주소(9000, 게이트웨이)로 올린 사진을 받음"


# ── 앱은 접속 비밀을 환경 변수로 받지 않는다 ──
@pytest.mark.parametrize("service", ["mall-portal", "mall-admin"])
def test_app_container_env_has_no_store_secrets(service):
    r = sh("docker", "inspect", f"daon-{service}-1", "--format", "{{json .Config.Env}}")
    names = {e.split("=", 1)[0] for e in json.loads(r.stdout)}
    banned = {"DAON_RABBIT_PASSWORD", "DAON_MINIO_ACCESS_KEY", "DAON_MINIO_SECRET_KEY", "DAON_MONGO_APP_PASSWORD", "DAON_MONGO_OPS_PASSWORD",
              "DAON_RABBIT_APP_PASSWORD", "DAON_MINIO_APP_SECRET_KEY", "DAON_MINIO_ROOT_PASSWORD"}
    assert not (banned & names), f"기대: 앱 컨테이너 환경 변수에 저장소 접속 비밀이 없음(시크릿 저장소에서 받음). 있는 이름: {sorted(banned & names)}"


# ── 망 분리 ──
STORES = ["mysql", "redis", "mongo", "rabbitmq", "minio"]


def networks_of(service: str) -> dict[str, bool]:
    r = sh("docker", "inspect", f"daon-{service}-1", "--format", "{{json .NetworkSettings.Networks}}")
    out = {}
    for name in json.loads(r.stdout or "{}"):
        internal = json.loads(sh("docker", "network", "inspect", name, "--format", "{{json .Internal}}").stdout or "false")
        out[name] = internal
    return out


def test_data_network_is_internal_and_stores_live_there():
    r = sh("docker", "network", "inspect", "daon_data", "--format", "{{json .Internal}}")
    assert r.returncode == 0 and r.stdout.strip() == "true", "기대: data 망이 있고 internal(바깥 인터넷으로도 못 나감)"
    for s in STORES:
        nets = networks_of(s)
        assert "daon_data" in nets, f"기대: {s}는 data 망에 있음"
        assert "daon_app" not in nets and "daon_edge" not in nets, f"기대: {s}는 app·edge 망에 없음. 실제 {sorted(nets)}"


def test_stores_have_no_published_ports_and_apps_are_on_app_and_data():
    for s in STORES:
        r = sh("docker", "inspect", f"daon-{s}-1", "--format", "{{json .NetworkSettings.Ports}}")
        bound = [k for k, v in json.loads(r.stdout).items() if v]
        assert not bound, f"기대: {s}는 PC로 열린 포트가 없음. 열린 포트: {bound}"
    for a in ("mall-portal", "mall-admin"):
        nets = networks_of(a)
        assert "daon_app" in nets and "daon_data" in nets, f"기대: {a}는 app·data 망. 실제 {sorted(nets)}"


def test_gateway_is_the_only_one_on_edge():
    r = sh("docker", "network", "inspect", "daon_edge", "--format", "{{json .Containers}}")
    names = sorted(v["Name"] for v in json.loads(r.stdout or "{}").values())
    assert names == ["daon-gateway-1"], f"기대: edge 망에는 게이트웨이만. 실제 {names}"


# ── 게이트웨이 ──
def gw(path: str, **kw) -> httpx.Response:
    return httpx.get(f"http://{HOST}:{port('DAON_MINIO_PORT', 9000)}{path}", timeout=10, **kw)


def test_gateway_file_address_is_read_only_and_narrow():
    assert gw("/minio/health/live").status_code == 200, "기대: 상태 확인 주소는 통과"
    assert gw("/").status_code == 404 and gw("/minio/admin/v3/info").status_code == 404, "기대: 버킷 mall과 상태 확인 밖의 주소는 게이트웨이가 404로 거절"
    w = httpx.put(f"http://{HOST}:{port('DAON_MINIO_PORT', 9000)}/mall/lab-should-not-write.txt", content=b"x", timeout=10)
    assert w.status_code in (403, 405), f"기대: 파일 주소로는 올릴 수 없음(읽기 전용). 실제 {w.status_code}"


def test_gateway_logs_trace_id_per_request():
    r = gw("/minio/health/live")
    rid = r.headers.get("x-request-id")
    assert rid and len(rid) >= 16, "기대: 게이트웨이가 응답에 X-Request-Id(추적 번호)를 붙임"
    time.sleep(0.5)
    log = sh("docker", "logs", "--tail", "50", "daon-gateway-1")
    assert f'"trace_id":"{rid}"' in (log.stdout + log.stderr), "기대: 게이트웨이 접근 로그(JSON)에 같은 trace_id가 남음"


# normal 아님: 관리자 입구(게이트웨이 8443)는 이 수업에서 생긴다(시작 지점에는 게이트웨이가 없음)
def test_admin_entrance_serves_admin_screen():
    r = httpx.get(f"http://{HOST}:{port('DAON_GATEWAY_ADMIN_PORT', 8443)}/", timeout=10)
    assert r.status_code == 200 and "<html" in r.text.lower(), f"기대: 관리자 입구(8443)에서 관리자 화면이 열림. 실제 {r.status_code}"
    assert httpx.get(f"http://{HOST}:{port('DAON_GATEWAY_ADMIN_PORT', 8443)}/healthz", timeout=10).status_code == 200


def test_rabbit_management_ui_only_behind_admin_entrance():
    r = httpx.get(f"http://{HOST}:{port('DAON_GATEWAY_ADMIN_PORT', 8443)}/rabbitmq/", timeout=10, follow_redirects=True)
    assert r.status_code == 200, f"기대: RabbitMQ 관리 화면은 관리자 입구(/rabbitmq/) 뒤에서 열림(로그인 화면). 실제 {r.status_code}"


# ── E09 · 믿는 프록시만 X-Forwarded-For를 믿는다 ──
FORGED = "203.0.113.77"


def ip_keys() -> set[str]:
    return set(lab.redis_cli("--scan", "--pattern", "rl:login:ip:*").stdout.split())


def failed_login(c: httpx.Client, user: str, **kw) -> None:
    c.post("/sso/login", data={"username": user, "password": "wrong-password-0000"}, **kw)


@pytest.mark.normal          # 회귀 방지: 시작 지점은 소켓 주소만 써서 그린. 믿는 프록시를 더한 뒤에도 PC가 보낸 헤더는 믿지 않아야 함
def test_forged_forwarded_for_from_pc_is_ignored(stack):
    """PC에서 앱으로 바로 보낸 X-Forwarded-For는 믿지 않는다: 속도 제한 키에 꾸민 주소가 들어가지 않는다."""
    clear_counters()
    with portal() as c:
        failed_login(c, "alice", headers={"X-Forwarded-For": FORGED})
        failed_login(c, "alice", headers={"X-Forwarded-For": f"{FORGED}, 198.51.100.9"})
    keys = ip_keys()
    assert keys, "기대: 로그인 실패가 IP 키로 세어짐"
    assert not any(FORGED in k or "198.51.100.9" in k for k in keys), "기대: 속도 제한 키에 요청 헤더로 꾸민 주소가 없음(소켓 주소를 씀)"
    clear_counters()


def test_proxy_forwarded_for_is_used_when_via_trusted_proxy(stack):
    """고객 웹(믿는 프록시)을 거치면 앱이 보는 소켓 주소는 웹 컨테이너인데, 키는 웹이 본 요청자 주소(PC에서 바로 보냈을 때와 같은 주소)가 된다."""
    clear_counters()
    with portal() as c:
        failed_login(c, "alice")
    direct = ip_keys()
    assert len(direct) == 1, "기대: 앱으로 바로 보내면 IP 키가 하나(소켓 주소)"
    clear_counters()
    with httpx.Client(base_url=f"http://{HOST}:{port('DAON_WEB_PORT', 8088)}/api", timeout=10) as w:
        failed_login(w, "alice", headers={"X-Forwarded-For": FORGED})      # 웹 nginx가 이 헤더를 요청자 주소로 덮어쓴다
    via_web = ip_keys()
    assert via_web == direct, "기대: 고객 웹을 거쳐도 IP 키는 같은 주소(요청자) — 웹 컨테이너 주소 하나로 뭉치지 않고, 꾸민 주소도 아님"
    clear_counters()


def test_untrusted_source_cannot_choose_its_ip_in_security_events(stack):
    since = _logs.now()
    with portal() as c:
        failed_login(c, "alice", headers={"X-Forwarded-For": FORGED})
    time.sleep(1)
    out = sh("docker", "logs", "--since", since, "daon-mall-portal-1")
    assert FORGED not in (out.stdout + out.stderr), "기대: 보안 이벤트·로그의 ip 칸에 요청 헤더로 보낸 주소가 없음"
    clear_counters()
