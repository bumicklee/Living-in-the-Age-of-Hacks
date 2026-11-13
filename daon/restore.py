"""15-2 골라 복구 도우미: 잠금 버킷(A11)의 백업 하나를 망 없는 임시 컨테이너에 띄워 주문 몇 건의 받는 사람 칸만 꺼낸다.

14-2 복구 훈련(daon/backup.py restore_drill)과 같은 길을 쓴다: backup-restorer(읽기만)로 받기 → sha256 대조 → mysql-restore(망 없음) →
SELECT → 컨테이너 지움. 원본 DB는 읽기만 한다. 값은 백업에 있던 그대로(5-3 암호문 포함)이고 화면에는 지문(sha256 앞 8자)만 보인다.
"""
from __future__ import annotations

import contextlib
import gzip
import hashlib
import json
import secrets
import subprocess
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

from daon import backup as bk
from replay import NotReady
from replay._money import mysql

COLUMNS = ("receiver_name", "receiver_phone", "receiver_post_code", "receiver_province", "receiver_city", "receiver_region",
           "receiver_detail_address")
CONTAINER = "daon-mysql-restore-cols"


def _select_sql(ids: list[int]) -> str:
    """HEX(JSON_OBJECT(...)): 탭·줄바꿈·백슬래시가 섞인 값(암호문)도 mysql -N 출력에서 깨지지 않게"""
    cols = ", ".join(f"'{c}', {c}" for c in COLUMNS)
    return f"SELECT HEX(JSON_OBJECT('id', id, {cols})) FROM oms_order WHERE id IN ({','.join(str(int(i)) for i in ids)}) ORDER BY id"


def _parse(out: str) -> dict[int, dict]:
    rows = {}
    for line in out.split():
        d = json.loads(bytes.fromhex(line.strip()).decode("utf-8"))
        rows[int(d.pop("id"))] = d
    return rows


def live(ids: list[int]) -> dict[int, dict]:
    """지금 DB의 받는 사람 칸(랩 도구: 컨테이너 안 mysql)"""
    return _parse(mysql(_select_sql(ids))) if ids else {}


def fingerprint(values: dict | None) -> str:
    if values is None:
        return "(없음)"
    return hashlib.sha256(json.dumps(values, sort_keys=True, ensure_ascii=False).encode()).hexdigest()[:8]


def backup_before(when_utc: str) -> dict:
    """복구 시점(덤프 시작 UTC)이 when_utc보다 앞선 백업 가운데 가장 늦은 것. 없으면 NotReady"""
    bk.require_vault()
    cut = datetime.strptime(when_utc, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    best = None
    for row in bk.list_backups():
        info = bk.stat(str(row["key"]))
        snap = info.get("snapshot_at")
        if not snap:
            continue
        at = datetime.strptime(snap, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        if at < cut and (best is None or at > best[0]):
            best = (at, str(row["key"]), info)
    if not best:
        raise NotReady(f"{when_utc}보다 앞선 백업이 잠금 버킷에 없습니다(make backup-status). 사고 전에 make backup-now가 돌았는지 확인")
    return {"key": best[1], "snapshot_at": best[0].strftime("%Y-%m-%dT%H:%M:%SZ"), "sha256": best[2].get("sha256")}


@contextlib.contextmanager
def restored(key: str, echo=print):
    """백업 key를 임시 컨테이너에 넣고 (실행 함수)를 준다. 끝나면 컨테이너를 지운다"""
    info = bk.stat(key)
    with tempfile.TemporaryDirectory(prefix="daon-restore-cols-") as d:
        r = bk.mc(f"mc cp --quiet vault/{bk.BUCKET}/mysql/{key} /out/dump.sql.gz >/dev/null", net="backup", creds="backup-restorer-creds",
                  mounts=[f"{d}:/out"])
        dump = Path(d) / "dump.sql.gz"
        if r.returncode or not dump.exists():
            raise NotReady(f"백업을 내려받지 못했습니다(backup-restorer): {(r.stderr or '').strip()[-160:]}")
        got = hashlib.sha256(dump.read_bytes()).hexdigest()
        if not info.get("sha256") or got != info["sha256"]:
            raise NotReady("백업 파일 해시가 메타데이터와 다릅니다. 이 백업으로 복구하지 않습니다")
        echo(f"   백업 {key} 해시 확인({got[:12]}…) → 임시 컨테이너(망 없음)에 넣는 중")
        pw = secrets.token_hex(16)
        subprocess.run(["docker", "rm", "-f", CONTAINER], capture_output=True)
        run = subprocess.run(["docker", "run", "-d", "--name", CONTAINER, "--network", "none", "--memory", "640m",
                              "-e", f"MYSQL_ROOT_PASSWORD={pw}", "-e", "MYSQL_DATABASE=mall", "-e", "TZ=Asia/Seoul", bk.MYSQL_IMAGE,
                              "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci"], capture_output=True, text=True)
        if run.returncode:
            raise NotReady(f"임시 컨테이너를 띄우지 못했습니다: {run.stderr.strip()[-160:]}")
        try:
            base = ["docker", "exec", "-i", "-e", f"MYSQL_PWD={pw}", CONTAINER, "mysql", "-h127.0.0.1", "--protocol=TCP", "-uroot",
                    "--default-character-set=utf8mb4"]
            for _ in range(120):
                if subprocess.run(base + ["-e", "SELECT 1"], capture_output=True).returncode == 0:
                    break
                time.sleep(1)
            imp = subprocess.run(base + ["mall"], input=gzip.decompress(dump.read_bytes()), capture_output=True)
            if imp.returncode:
                raise NotReady(f"덤프를 넣지 못했습니다: {imp.stderr.decode('utf-8', 'replace').strip()[-160:]}")

            def query(sql: str) -> str:
                q = subprocess.run(base + ["-N", "mall", "-e", sql], capture_output=True, text=True)
                if q.returncode:
                    raise NotReady(f"복구본 조회 실패: {q.stderr.strip()[-120:]}")
                return q.stdout.strip()
            yield query
        finally:
            subprocess.run(["docker", "rm", "-f", CONTAINER], capture_output=True)


def from_backup(key: str, ids: list[int], echo=print) -> dict[int, dict]:
    with restored(key, echo) as query:
        return _parse(query(_select_sql(ids)))
