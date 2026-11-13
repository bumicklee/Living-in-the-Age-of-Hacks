"""섹션 12 랩 도구: 파트너 입구(A9)·파트너 인증서·연동 앱 대장 점검·일괄 폐기.

    make partner-app [APP=courier-01] [DO=orders|export|token]   가짜 파트너 앱(stubs/partner) 한 번 돌리기(건수만 찍음)
    make partner-certs        12-3 랩 내부 CA가 만든 파트너 인증서를 PC(.daon/partner-pki/, git에 넣지 않음)로 가져온다
    make partner-audit        12-4 연동 앱 대장 점검(관리자 앱 GET /daon/partner/apps): 오래 안 쓴 앱·내보내기 범위·담당자 없음·회전 90일
    make partner-revoke APP=  12-4 앱 단위 일괄 폐기(관리자 앱 POST /daon/partner/apps/<앱>/revoke)
    make partner-reset        랩 전용: 시드 파트너 셋을 처음 상태로(상태 active·범위·비밀값 v1·legacy-01 120일). 시험이 끝에 부른다

이 PC의 랩(127.0.0.1)만 부른다. 비밀값·토큰·인증서 개인키는 화면에 찍지 않는다.
"""
from __future__ import annotations

import hashlib
import importlib.util
import io
import os
import socket
import ssl
import subprocess
import sys
import tarfile
import time
from pathlib import Path

from daon import stack as st
from daon.config import DATA, HOST, ROOT, URLS, port

PKI = DATA / "partner-pki"
STUB = ROOT / "stubs" / "partner" / "partner_app.py"
GW_PORT = port("DAON_GATEWAY_PARTNER_PORT", 8087)
BAO_IMAGE = "openbao/openbao:2.4.1"
# 시드(DaonPartnerSchema와 같은 값): 범위, 마지막 사용(며칠 전), 비밀값 회전(며칠 전)
SEED = {
    "courier-01": {"scopes": "orders:read:assigned", "idle": 1, "rotated": 20},
    "analytics-01": {"scopes": "orders:read:assigned orders:export", "idle": 2, "rotated": 30},
    "legacy-01": {"scopes": "orders:read:assigned", "idle": 120, "rotated": 200},
}
STALE_DAYS = 90
ROTATE_DAYS = 90


def seed_secret(app: str) -> str:
    return f"lab-fake-partner-secret-{app}-v1"


# ── 파트너 입구 ──
def gateway_scheme(timeout: float = 2.0) -> str | None:
    """8087이 열려 있으면 'https'(12-3부터) 또는 'http'(12-2). 닫혀 있으면 None(A9 전 = 앱 직접 주소를 쓴다)."""
    try:
        sock = socket.create_connection((HOST, GW_PORT), timeout=timeout)
    except OSError:
        return None
    ctx = ssl._create_unverified_context()   # noqa: S323 (판(scheme)만 알아본다. 데이터는 보내지 않는다)
    try:
        with ctx.wrap_socket(sock, server_hostname=HOST) as s:
            s.do_handshake()
        return "https"
    except (ssl.SSLError, OSError):
        return "http"
    finally:
        try:
            sock.close()
        except OSError:
            pass


def base() -> str:
    """파트너가 부를 주소: 파트너 입구(A9)가 있으면 그곳, 없으면(12-2 시작) 앱 직접 주소."""
    scheme = gateway_scheme()
    return f"{scheme}://{HOST}:{GW_PORT}" if scheme else URLS["spring"]["portal"]


# ── 인증서(12-3) ──
def _volume(name: str) -> str:
    return f"{os.environ.get('COMPOSE_PROJECT_NAME', 'daon')}_{name}"


def ensure_certs(force: bool = False) -> Path | None:
    """partner-pki가 만든 클라이언트 인증서 볼륨을 .daon/partner-pki/로 가져온다. 볼륨이 없으면(12-3 전) None."""
    if not force and (PKI / "ca.crt").exists() and (PKI / "courier-01.key").exists() and time.time() - (PKI / "ca.crt").stat().st_mtime < 86400:
        return PKI
    vol = _volume("partner-pki-clients")
    if subprocess.run(["docker", "volume", "inspect", vol], capture_output=True).returncode != 0:
        return None
    r = subprocess.run(["docker", "run", "--rm", "-v", f"{vol}:/c:ro", "--entrypoint", "tar", BAO_IMAGE, "-C", "/c", "-cf", "-", "."],
                       capture_output=True, timeout=120)
    if r.returncode != 0 or not r.stdout:
        return None
    PKI.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(r.stdout)) as tar:
        for m in tar.getmembers():
            name = Path(m.name).name
            if not m.isfile() or not name.endswith((".crt", ".key")):
                continue
            data = tar.extractfile(m).read()
            out = PKI / name
            out.write_bytes(data)
            os.chmod(out, 0o600 if name.endswith(".key") else 0o644)
    return PKI


def cert_paths(app: str | None) -> dict:
    """PartnerApp에 넘길 cert·key·ca. https가 아니거나 app이 None이면 ca만(서버 확인용)."""
    if not base().startswith("https://"):
        return {}
    d = ensure_certs()
    if d is None:
        from replay import NotReady
        raise NotReady("파트너 입구가 https인데 인증서 볼륨이 없습니다. make up STACK=spring 뒤 make partner-certs")
    out = {"ca": str(d / "ca.crt")}
    if app:
        out |= {"cert": str(d / f"{app}.crt"), "key": str(d / f"{app}.key")}
    return out


def thumbprint(cert_file: str | Path) -> str:
    """인증서 지문 x5t#S256 = base64url(SHA-256(DER))."""
    import base64
    der = ssl.PEM_cert_to_DER_cert(Path(cert_file).read_text())
    return base64.urlsafe_b64encode(hashlib.sha256(der).digest()).decode().rstrip("=")


# ── 파트너 앱 스텁 ──
def stub_module():
    spec = importlib.util.spec_from_file_location("daon_partner_app", STUB)
    mod = importlib.util.module_from_spec(spec)
    sys.modules.setdefault("daon_partner_app", mod)
    spec.loader.exec_module(mod)
    return mod


def app(app_id: str = "courier-01", *, secret: str | None = None, cert_of: str | None = "self"):
    """가짜 파트너 앱. cert_of="self"면 자기 인증서, None이면 인증서 없이, 다른 앱 이름이면 그 앱의 인증서로."""
    mod = stub_module()
    who = app_id if cert_of == "self" else cert_of
    return mod.PartnerApp(base(), app_id, client_secret=secret or seed_secret(app_id), **cert_paths(who))


# ── DB(랩 전용 root, 컨테이너 안 mysql) ──
def mysql(sql: str) -> str:
    from replay import _money
    return _money.mysql(sql)


def assigned_ids(app_id: str) -> set[int]:
    out = mysql(f"SELECT order_id FROM daon_partner_order WHERE app_id = '{app_id}'")
    return {int(x) for x in out.split() if x.strip().isdigit()}


def reset() -> None:
    """시드 파트너 셋을 처음 상태로: 상태 active, 범위, 비밀값 v1 하나, legacy-01 120일. 발급 토큰 기록과 폐기 목록은 그대로 둔다."""
    now_ms = int(time.time() * 1000)
    for app_id, s in SEED.items():
        h = hashlib.sha256(seed_secret(app_id).encode()).hexdigest()
        mysql(f"UPDATE daon_partner_app SET status='active', scopes='{s['scopes']}', "
              f"last_used_at = NOW() - INTERVAL {s['idle']} DAY, secret_rotated_at = NOW() - INTERVAL {s['rotated']} DAY WHERE id='{app_id}'")
        mysql(f"DELETE FROM daon_partner_credential WHERE app_id='{app_id}'")
        mysql(f"INSERT INTO daon_partner_credential (app_id, secret_sha256, created_ms) VALUES ('{app_id}', '{h}', {now_ms})")


# ── 관리자 앱(12-4 대장) ──
def _admin_call(method: str, path: str, json_body: dict | None = None):
    from replay._http import admin, admin_login
    from replay.t09_login_guard import clear_counters
    clear_counters()
    with admin() as c:
        tok = admin_login(c, "admin")
        return c.request(method, path, headers={"Authorization": tok}, json=json_body)


def _body(r) -> dict:
    try:
        d = r.json()
        return d if isinstance(d, dict) else {}
    except ValueError:
        return {}


def ledger() -> list[dict] | None:
    """대장 행. 관리자 앱에 대장 API가 없으면(12-4 전: 권한표에 없어 403, 또는 404) None."""
    r = _admin_call("GET", "/daon/partner/apps")
    body = _body(r)
    if r.status_code != 200 or body.get("code") != 200:
        return None
    return body.get("data")


def audit(rows: list[dict]) -> dict[str, list[str]]:
    """대장 점검 규칙 → {규칙: [앱 ID…]}"""
    def num(v):
        try:
            return int(v)
        except (TypeError, ValueError):
            return 0
    live = [r for r in rows if r.get("status") != "revoked"]
    return {
        "stale": [r["id"] for r in live if num(r.get("idleDays")) > STALE_DAYS],
        "export_scope": [r["id"] for r in live if "orders:export" in str(r.get("scopes", "")).split()],
        "no_owner": [r["id"] for r in live if not (r.get("owner") or "").strip()],
        "old_secret": [r["id"] for r in live if num(r.get("secretAgeDays")) > ROTATE_DAYS],
        "contract_ended": [r["id"] for r in live if num(r.get("contractEnded")) == 1],
    }


LABELS = {
    "stale": f"{STALE_DAYS}일 넘게 안 쓴 앱(권한 회수 후보)",
    "export_scope": "내보내기 범위를 가진 앱(정말 필요한지 담당자 확인)",
    "no_owner": "담당자 없는 앱(사고 때 물을 사람이 없음)",
    "old_secret": f"비밀값 회전이 {ROTATE_DAYS}일 넘음",
    "contract_ended": "계약이 끝났는데 살아 있는 앱",
}


def revoke(app_id: str) -> dict | None:
    r = _admin_call("POST", f"/daon/partner/apps/{app_id}/revoke")
    body = _body(r)
    if r.status_code in (403, 404, 405) or body.get("code") in (403, 404):
        return None
    return body


def set_scopes(app_id: str, scopes: str) -> dict | None:
    r = _admin_call("POST", f"/daon/partner/apps/{app_id}/scopes", {"scopes": scopes})
    body = _body(r)
    if r.status_code in (403, 404, 405) or body.get("code") in (403, 404):
        return None
    return body


# ── 명령 ──
def cmd_partner_app(a) -> None:
    mod = stub_module()
    argv = ["--base", base(), "--app", a.app]
    for k, v in cert_paths(a.app).items():
        argv += [f"--{k}", v]
    sys.exit(mod.main(argv + [a.do]))


def cmd_partner_certs(a) -> None:
    d = ensure_certs(force=True)
    if d is None:
        raise SystemExit("파트너 인증서 볼륨이 없습니다. 12-3 완료 지점에서 make up STACK=spring 뒤 다시 실행하세요(make goto L=12-3 AT=done)")
    print(f"파트너 인증서를 {d.relative_to(ROOT)}/ 에 가져왔습니다(git에 넣지 않는 곳, 개인키 권한 600).")
    for f in sorted(d.glob("*.crt")):
        print(f"  {f.name:<20} x5t#S256 {thumbprint(f)[:16]}…")


def cmd_partner_audit(a) -> None:
    st.ensure_fresh("spring")
    rows = ledger()
    if rows is None:
        print("[레드] 연동 앱 대장이 없습니다: 관리자 앱에 GET /daon/partner/apps가 없습니다(12-4에서 만든다).")
        print("       지금은 어느 파트너가 무슨 범위로 언제 마지막으로 썼는지, 담당자가 누구인지 한곳에서 볼 수 없습니다.")
        sys.exit(1)
    print(f"{'앱':<14} {'상태':<8} {'담당자':<26} {'범위':<36} {'안 쓴 날':>8} {'회전':>6}")
    for r in rows:
        print(f"{r['id']:<14} {r.get('status', ''):<8} {(r.get('owner') or '(없음)')[:26]:<26} {r.get('scopes', ''):<36} "
              f"{r.get('idleDays', '-'):>8} {r.get('secretAgeDays', '-'):>6}")
    found = audit(rows)
    print("\n점검 결과")
    for k, ids in found.items():
        print(f"  {'●' if ids else '○'} {LABELS[k]}: {', '.join(ids) or '없음'}")
    print("\n다음: 담당자에게 확인 → 안 쓰는 앱은 make partner-revoke APP=<앱>, 범위는 관리자 앱에서 줄인다")


def cmd_partner_revoke(a) -> None:
    if not a.app:
        raise SystemExit("폐기할 앱을 넘기세요: make partner-revoke APP=courier-01")
    st.ensure_fresh("spring")
    body = revoke(a.app)
    if body is None:
        print("[레드] 앱 단위 일괄 폐기 기능이 없습니다(관리자 앱 POST /daon/partner/apps/<앱>/revoke, 12-4에서 만든다).")
        print("       지금 할 수 있는 것은 비밀값을 하나씩 바꾸는 것뿐이고, 이미 발급한 토큰은 수명 동안 계속 쓰입니다.")
        sys.exit(1)
    if body.get("code") != 200:
        raise SystemExit(f"폐기 실패: {body.get('message')}")
    d = body["data"]
    print(f"{a.app} 일괄 폐기: 발급 토큰 {d['revokedTokens']}개를 폐기 목록에, 비밀값 {d['retiredSecrets']}개 거부. 되돌리기(랩): make partner-reset")


def cmd_partner_reset(a) -> None:
    reset()
    print("시드 파트너 셋(courier-01·analytics-01·legacy-01)을 처음 상태로 되돌렸습니다(상태 active·범위·비밀값 v1).")


def register(add, stack_opt) -> None:
    add("partner-app", cmd_partner_app, (["--app"], {"default": "courier-01"}),
        (["--do"], {"default": "orders", "choices": ["orders", "export", "token"]}))
    add("partner-certs", cmd_partner_certs)
    add("partner-audit", cmd_partner_audit)
    add("partner-revoke", cmd_partner_revoke, (["app"], {"nargs": "?"}))
    add("partner-reset", cmd_partner_reset)
