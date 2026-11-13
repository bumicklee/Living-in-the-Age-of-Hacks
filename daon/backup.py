"""섹션 14 랩 도구: 불변 백업(A11)·복구 훈련.

    make backup-now          백업 작업을 지금 한 번(backup-job 컨테이너 안 /backup/backup.sh)
    make backup-status       잠금 버킷의 최근 백업(이름·크기·보존 모드·보존 기한)과 backup-audit 상태. 읽기만 되는 backup-restorer로 본다
    make restore-drill       복구 훈련: 최신 백업 → 해시 확인 → 새 컨테이너 mysql-restore에 복구 → 확인 시험 → RTO·RPO 출력

모두 이 PC의 랩 안에서만 한다(도커 망 daon_backup·daon_data). 비밀 키·비밀번호는 화면에 찍지 않고 프로세스 인자로도 넘기지 않는다
(접속 정보는 전용 볼륨의 파일을 컨테이너 안에서만 읽는다). 복구 훈련은 원본 DB를 건드리지 않는다(읽기만).
결과: .daon/restore-drill/<시각>.json, 도구 이벤트 backup.restore.drill(INFO, reports/security-events.jsonl).
"""
from __future__ import annotations

import gzip
import hashlib
import json
import re
import secrets
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

from daon import stack as st
from daon.config import DATA, ENV, ROOT
from replay import NotReady

MINIO_IMAGE = "pgsty/minio:RELEASE.2026-08-04T00-00-00Z"
MYSQL_IMAGE = "mysql:8.4.13"
BUCKET = "daon-backup"
KEY_OK = re.compile(r"^[A-Za-z0-9._/-]{1,200}$")      # 쉘에 넣는 객체 이름은 이 모양만(우리가 만든 이름 mall-<UTC>.sql.gz)
RESTORE = "daon-mysql-restore"
MIN_ORDERS, MIN_MEMBERS = 300, 40                    # 확인 시험 기준(가짜 시드 크기: 주문 300건, 고객 40명)
DRILLS = DATA / "restore-drill"


def project() -> str:
    import os
    return os.environ.get("COMPOSE_PROJECT_NAME", "daon")


def container(service: str) -> str:
    return f"{project()}-{service}-1"


def running(service: str) -> bool:
    r = subprocess.run(["docker", "inspect", "-f", "{{.State.Running}}", container(service)], capture_output=True, text=True)
    return r.returncode == 0 and r.stdout.strip() == "true"


def vault_on() -> bool:
    return running("backup-vault")


def require_vault() -> None:
    if "a11" not in st.arch_layers():
        raise NotReady("A11이 꺼져 있습니다(infra/arch.txt에 a11 없음). 14-2 시작 지점: make goto L=14-2 && make up STACK=spring")
    if not vault_on():
        raise NotReady("잠금 버킷(backup-vault)이 없습니다. 14-2 완료 지점에서: make goto L=14-2 AT=done && make up STACK=spring")


def mc(script: str, *, net: str, creds: str | None = None, alias: str = "vault", host: str = "backup-vault:9000",
       env: dict[str, str] | None = None, mounts: list[str] | None = None, timeout: int = 120) -> subprocess.CompletedProcess:
    """랩 망 안에서 mc를 한 번 돌린다. creds=도커 볼륨 이름(access_key·secret_key 파일)이면 그 계정으로 alias를 만든다.
    env의 K·S가 있으면 그 값으로(값은 환경 변수로만 넘긴다). 결과는 표준 출력 글자."""
    cmd = ["docker", "run", "--rm", "--network", f"{project()}_{net}", "--user", "0", "-e", "MC_CONFIG_DIR=/tmp/.mc"]
    pre = ""
    if creds:
        cmd += ["-v", f"{project()}_{creds}:/c:ro"]
        pre = f'export MC_HOST_{alias}="http://$(cat /c/access_key):$(cat /c/secret_key)@{host}"; '
    for k, v in (env or {}).items():
        cmd += ["-e", f"{k}={v}"]
    if env and "K" in env:
        pre = f'export MC_HOST_{alias}="http://$K:$S@{host}"; '
    for m in mounts or []:
        cmd += ["-v", m]
    cmd += ["--entrypoint", "sh", MINIO_IMAGE, "-c", pre + script]
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def delete_probe_as_writer(key: str) -> int:
    """백업 작업 계정으로 실제 DELETE 요청을 보내고 HTTP 상태를 돌린다(랩의 가짜 백업만).

    mc rm은 삭제 전에 HeadObject부터 호출한다. 올리기만 가능한 계정은 그 조회가
    403이라 삭제 요청 자체가 저장소에 도착하지 않아 감사 이벤트를 확인할 수 없다.
    curl의 SigV4 인증 정보는 표준 입력으로 전달해 명령 인자에 남기지 않는다.
    """
    if not KEY_OK.match(key):
        raise ValueError(f"객체 이름 모양이 다릅니다: {key[:40]}")
    script = (
        "printf 'user = \"%s:%s\"\\n' \"$(cat /c/access_key)\" \"$(cat /c/secret_key)\""
        " | curl --config - --aws-sigv4 aws:amz:us-east-1:s3 -X DELETE"
        " -sS -o /dev/null -w '%{http_code}'"
        f" http://backup-vault:9000/{BUCKET}/mysql/{key}"
    )
    r = mc(script, net="backup", creds="backup-writer-creds")
    if r.returncode or not r.stdout.strip().isdigit():
        raise NotReady(f"백업 삭제 거부 확인에 실패했습니다: {r.stderr.strip()[-120:]}")
    return int(r.stdout.strip())


def _json_lines(text: str) -> list[dict]:
    out = []
    for line in (text or "").splitlines():
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if isinstance(d, dict):
            out.append(d)
    return out


def list_backups(prefix: str = "mysql/") -> list[dict]:
    """잠금 버킷의 백업(backup-restorer로 읽기). 최근 것이 뒤"""
    r = mc(f"mc ls --json vault/{BUCKET}/{prefix}", net="backup", creds="backup-restorer-creds")
    rows = [d for d in _json_lines(r.stdout) if d.get("type") == "file" and str(d.get("key", "")).endswith(".sql.gz")]
    return sorted(rows, key=lambda d: d.get("lastModified", ""))


def stat(key: str, prefix: str = "mysql/") -> dict:
    if not KEY_OK.match(key):
        raise ValueError(f"객체 이름 모양이 다릅니다: {key[:40]}")
    r = mc(f"mc stat --json vault/{BUCKET}/{prefix}{key}; mc retention info --json vault/{BUCKET}/{prefix}{key}",
           net="backup", creds="backup-restorer-creds")
    docs = _json_lines(r.stdout)
    meta = next((d for d in docs if "metadata" in d), {})
    ret = next((d for d in docs if "mode" in d or "until" in d or "Mode" in d), {})
    md = {k.lower(): v for k, v in (meta.get("metadata") or {}).items()}
    # 보존 정보는 retention info(--json)에서, 없으면 stat 메타데이터의 객체 잠금 헤더에서(mc 판에 따라 칸 이름이 다르다)
    mode = ret.get("mode") or ret.get("Mode") or md.get("x-amz-object-lock-mode") or ""
    until = ret.get("until") or ret.get("validity") or md.get("x-amz-object-lock-retain-until-date") or ""
    return {"key": key, "size": meta.get("size"), "sha256": md.get("x-amz-meta-sha256"), "snapshot_at": md.get("x-amz-meta-snapshot-at"),
            "mode": str(mode).upper(), "until": str(until)}


def backup_now() -> str:
    """백업 작업을 지금 한 번. 결과 줄(백업 이름·해시 앞 12자)"""
    if not running("backup-job"):
        raise NotReady("백업 작업(backup-job)이 떠 있지 않습니다. 14-2 시작 지점 이후 make up STACK=spring")
    r = subprocess.run(["docker", "exec", container("backup-job"), "bash", "/backup/backup.sh"], capture_output=True, text=True, timeout=180)
    if r.returncode:
        raise NotReady(f"백업이 실패했습니다(종료 코드 {r.returncode}): {(r.stderr or r.stdout).strip()[-200:]}")
    return r.stdout.strip().splitlines()[-1] if r.stdout.strip() else "백업 끝"


def audit_state() -> dict:
    if not running("backup-audit"):
        return {}
    r = subprocess.run(["docker", "exec", container("backup-audit"), "python", "-c",
                        "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8000/state',timeout=3).read().decode())"],
                       capture_output=True, text=True)
    try:
        return json.loads(r.stdout)
    except ValueError:
        return {}


# ── 복구 훈련 ──
def _mysql_restore(sql: str, pw: str) -> str:
    r = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={pw}", RESTORE, "mysql", "-h127.0.0.1", "--protocol=TCP", "-uroot",
                        "--default-character-set=utf8mb4", "-N", "mall", "-e", sql], capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError(r.stderr.strip()[-200:])
    return r.stdout.strip()


def _source(sql: str) -> str:
    from replay._money import mysql
    return mysql(sql)


def restore_drill(echo=print) -> dict:
    """잠금 버킷 최신 백업으로 복구 훈련. 결과 dict(ok·rto_seconds·rpo_seconds·checks …). 실패해도 결과를 돌려준다"""
    require_vault()
    t0 = time.monotonic()
    started = datetime.now(timezone.utc)
    rows = list_backups()
    if not rows:
        raise NotReady("잠금 버킷에 백업이 없습니다. make backup-now 뒤 다시")
    key = str(rows[-1]["key"])
    info = stat(key)
    echo(f"① 최신 백업 {key} · {info['size']}바이트 · 보존 {info['mode'] or '?'} {info['until'][:19]}")
    result: dict = {"key": key, "started": started.isoformat(timespec="seconds"), "checks": {}, "ok": False}
    with tempfile.TemporaryDirectory(prefix="daon-restore-") as d:
        r = mc(f"mc cp --quiet vault/{BUCKET}/mysql/{key} /out/dump.sql.gz >/dev/null", net="backup", creds="backup-restorer-creds",
               mounts=[f"{d}:/out"])
        dump = Path(d) / "dump.sql.gz"
        if r.returncode or not dump.exists():
            raise NotReady(f"백업을 내려받지 못했습니다(backup-restorer): {(r.stderr or '').strip()[-160:]}")
        got = hashlib.sha256(dump.read_bytes()).hexdigest()
        result["checks"]["해시 일치"] = bool(info["sha256"]) and got == info["sha256"]
        echo(f"② 해시 확인: 메타데이터 {str(info['sha256'])[:12]}… · 받은 파일 {got[:12]}… → {'같음' if result['checks']['해시 일치'] else '다름(복구 중단)'}")
        if not result["checks"]["해시 일치"]:
            return _finish(result, t0, info, echo)
        pw = secrets.token_hex(16)
        subprocess.run(["docker", "rm", "-f", RESTORE], capture_output=True)
        run = subprocess.run(["docker", "run", "-d", "--name", RESTORE, "--network", "none", "--memory", "640m", "-e", f"MYSQL_ROOT_PASSWORD={pw}",
                              "-e", "MYSQL_DATABASE=mall", "-e", "TZ=Asia/Seoul", MYSQL_IMAGE,
                              "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci"], capture_output=True, text=True)
        if run.returncode:
            raise NotReady(f"복구용 컨테이너를 띄우지 못했습니다: {run.stderr.strip()[-160:]}")
        try:
            echo("③ 새 컨테이너 mysql-restore에 복구(포트를 PC에 열지 않음, 망 없음)")
            for _ in range(120):
                ok = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={pw}", RESTORE, "mysql", "-h127.0.0.1", "--protocol=TCP", "-uroot",
                                     "-e", "SELECT 1"], capture_output=True)
                if ok.returncode == 0:
                    break
                time.sleep(1)
            sql = gzip.decompress(dump.read_bytes())
            imp = subprocess.run(["docker", "exec", "-i", "-e", f"MYSQL_PWD={pw}", RESTORE, "mysql", "-h127.0.0.1", "--protocol=TCP", "-uroot",
                                  "--default-character-set=utf8mb4", "mall"], input=sql, capture_output=True)
            result["checks"]["덤프 넣기"] = imp.returncode == 0
            if imp.returncode == 0:
                orders = int(_mysql_restore("SELECT COUNT(*) FROM oms_order", pw) or 0)
                members = int(_mysql_restore("SELECT COUNT(*) FROM ums_member", pw) or 0)
                alice = int(_mysql_restore("SELECT COUNT(*) FROM oms_order o JOIN ums_member m ON o.member_id = m.id "
                                           "WHERE m.username = 'alice' AND o.delete_status = 0", pw) or 0)
                result["counts"] = {"orders": orders, "members": members, "alice_orders": alice}
                result["checks"][f"주문 {MIN_ORDERS}건 이상"] = orders >= MIN_ORDERS
                result["checks"][f"회원 {MIN_MEMBERS}명 이상"] = members >= MIN_MEMBERS
                result["checks"]["alice 자기 주문 조회"] = alice >= 1
                echo(f"④ 확인 시험: 주문 {orders}건 · 회원 {members}명 · alice 주문 {alice}건 (개인정보 칸은 암호문 그대로, 키는 OpenBao에서 따로 복구)")
            else:
                echo(f"④ 덤프를 넣지 못했습니다: {imp.stderr.decode('utf-8', 'replace').strip()[-160:]}")
        finally:
            subprocess.run(["docker", "rm", "-f", RESTORE], capture_output=True)
    return _finish(result, t0, info, echo)


def _finish(result: dict, t0: float, info: dict, echo) -> dict:
    result["ok"] = bool(result["checks"]) and all(result["checks"].values())
    rto = time.monotonic() - t0
    result["rto_seconds"] = round(rto, 1)
    snap = info.get("snapshot_at")
    rpo = None
    lost = None
    if snap:
        try:
            at = datetime.strptime(snap, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
            rpo = (datetime.now(timezone.utc) - at).total_seconds()
            # 원본 DB에서 복구 시점 뒤에 생긴 주문 수(잃는 데이터의 크기). DB 시각은 Asia/Seoul
            kst = at.astimezone().strftime("%Y-%m-%d %H:%M:%S")
            lost = int(_source(f"SELECT COUNT(*) FROM oms_order WHERE create_time > CONVERT_TZ('{at.strftime('%Y-%m-%d %H:%M:%S')}', '+00:00', '+09:00')") or 0)
            result["recovery_point_local"] = kst
        except (ValueError, RuntimeError, NotReady):
            pass
    result["rpo_seconds"] = None if rpo is None else round(rpo)
    result["orders_after_recovery_point"] = lost
    head = "[그린] 복구 훈련 통과" if result["ok"] else "[레드] 복구 훈련 실패"
    rpo_txt = "알 수 없음(복구 시점 메타데이터 없음)" if rpo is None else f"{int(rpo // 60)}분 {int(rpo % 60):02d}초"
    lost_txt = "" if lost is None else f"(복구 시점 뒤 주문 {lost}건)"
    echo(f"⑤ {head} · RTO {int(rto // 60)}분 {int(rto % 60):02d}초 · RPO {rpo_txt}{lost_txt}")
    echo("   RTO = 훈련 시작부터 확인 시험 통과까지, RPO = 지금(사고 시각으로 가정) − 백업의 복구 시점(덤프 시작 시각)")
    DRILLS.mkdir(parents=True, exist_ok=True)
    out = DRILLS / f"{time.strftime('%Y%m%d-%H%M%S')}.json"
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), "utf-8")
    sys.path.insert(0, str(ROOT / "tools"))
    import secevent
    secevent.emit("backup.restore.drill", "14-2", "tool:restore-drill", f"backup:{BUCKET}/mysql/{result['key']}",
                  f"{'통과' if result['ok'] else '실패'} · RTO {result['rto_seconds']}초 · RPO {result['rpo_seconds']}초",
                  level="INFO", echo=False)
    echo(f"   기록: {out.relative_to(ROOT)} · 도구 이벤트 backup.restore.drill")
    return result


# ── 명령 ──
def cmd_backup_now(a) -> None:
    try:
        print(backup_now())
    except NotReady as e:
        raise SystemExit(str(e))


def cmd_backup_status(a) -> None:
    try:
        require_vault()
    except NotReady as e:
        print(str(e))
        legacy = mc("mc ls --recursive app/mall/backups/ 2>/dev/null | wc -l", net="data", alias="app", host="minio:9000",
                    env={"K": ENV.get("DAON_MINIO_APP_ACCESS_KEY", ""), "S": ENV.get("DAON_MINIO_APP_SECRET_KEY", "")})
        print(f"앱 버킷 backups/의 백업: {legacy.stdout.strip() or '?'}개 ([회사 설정] 앱 계정으로 닿음)")
        return
    rows = list_backups()
    print(f"잠금 버킷 {BUCKET} · 백업 {len(rows)}개(mysql/) · backup-restorer(읽기만)로 봄")
    for row in rows[-5:]:
        i = stat(str(row["key"]))
        print(f"  {i['key']}  {i['size']}바이트  보존 {i['mode'] or '?'} ~{i['until'][:19]}  복구 시점 {i['snapshot_at'] or '?'}")
    s = audit_state()
    if s:
        print(f"backup-audit: 마지막 백업 {s.get('last_put_age_seconds')}초 전(기준 {s.get('max_age_seconds')}초) · 거부된 삭제 시도 {s.get('denied_deletes')}건")


def cmd_restore_drill(a) -> None:
    try:
        r = restore_drill()
    except NotReady as e:
        raise SystemExit(f"[준비 안 됨] {e}")
    if not r["ok"]:
        sys.exit(1)


def register(add, stack_opt) -> None:
    add("backup-now", cmd_backup_now)
    add("backup-status", cmd_backup_status)
    add("restore-drill", cmd_restore_drill)
