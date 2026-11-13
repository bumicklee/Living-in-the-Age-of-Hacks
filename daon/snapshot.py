"""빠른 초기화(A12): 시드를 막 넣은 DB를 스냅샷으로 떠 두고, `make wipe`는 그 스냅샷으로 몇 초 안에 되돌린다.

- 스냅샷을 뜨는 때: 볼륨을 지우고(느린 초기화) 다시 `make up`해 시드가 새로 들어간 직후. 그 전의 DB는 이미
  수업·시험으로 바뀌었을 수 있어 뜨지 않는다.
- 스냅샷 열쇠: 시드 파일(과 Express는 EverShop migration)의 지문. 시드를 다시 만들면 열쇠가 바뀌어 한 번은 느린 초기화로 간다.
- Spring: mall DB를 mysqldump(표 정의 + 값) → 되돌릴 때 그대로 다시 넣는다. Redis·MongoDB는 비우고, RabbitMQ 지연 큐는 비운다.
- Express: PostgreSQL 값만 pg_dump(-Fc --data-only) → 되돌릴 때 public 표를 모두 비우고(TRUNCATE) 값만 다시 넣는다.
  표 정의는 EverShop이 뜰 때 migration이 만든 것을 그대로 쓴다.
- 스냅샷은 .daon/snapshot/에 둔다(git에 넣지 않음). 강제로 느린 초기화: make wipe HARD=1
"""
from __future__ import annotations

import hashlib
import os
import subprocess
import time
from pathlib import Path

from daon import stack as st
from daon.config import DATA, ROOT

DIR = DATA / "snapshot"
KEY_SOURCES = {
    "spring": ["infra/mysql/init", "spring/document/sql/mall.sql", "seed/spring"],
    "express": ["seed/express", "express/packages/evershop/src/modules"],
}


def _key(stack: str) -> str:
    h = hashlib.sha256()
    for rel in KEY_SOURCES[stack]:
        base = ROOT / rel
        if not base.exists():
            continue
        files = [base] if base.is_file() else sorted(base.rglob("*"))
        for f in files:
            if not f.is_file():
                continue
            if stack == "express" and rel.endswith("modules") and "migration" not in f.parts:
                continue   # EverShop은 표 정의(migration)만 열쇠에 넣는다
            h.update(str(f.relative_to(ROOT)).encode())
            h.update(f.read_bytes())
    return h.hexdigest()[:16]


def _paths(stack: str) -> tuple[Path, Path]:
    ext = "sql" if stack == "spring" else "pgdump"
    return DIR / f"{stack}.{ext}", DIR / f"{stack}.key"


def _pending(stack: str) -> Path:
    return DIR / f"{stack}.pending"


def ready(stack: str) -> bool:
    data, key = _paths(stack)
    return data.exists() and key.exists() and key.read_text().strip() == _key(stack)


def mark_pending(stack: str) -> None:
    """볼륨을 지웠다: 다음 up에서 시드가 새로 들어가므로 그때 스냅샷을 뜬다."""
    DIR.mkdir(parents=True, exist_ok=True)
    _pending(stack).write_text(str(int(time.time())))


PROJECT = os.environ.get("COMPOSE_PROJECT_NAME", "daon")     # compose.yml의 name: daon(사본으로 시험할 때만 바꾼다)
VOLUMES = {"spring": f"{PROJECT}_mysql-data", "express": f"{PROJECT}_pg-data"}


def mark_if_new_volume(stack: str) -> None:
    """DB 볼륨이 아직 없으면(처음 up) 이번 up에서 시드가 새로 들어간다 → 끝나고 스냅샷을 뜨도록 표시."""
    r = subprocess.run(["docker", "volume", "inspect", VOLUMES[stack]], capture_output=True)
    if r.returncode != 0:
        mark_pending(stack)


def _container(service: str) -> str:
    return f"{PROJECT}-{service}-1"


def _running(stack: str, service: str) -> bool:
    r = subprocess.run(["docker", "inspect", "-f", "{{.State.Running}}", _container(service)], capture_output=True, text=True)
    return r.returncode == 0 and r.stdout.strip() == "true"


def _exec(stack: str, service: str, *cmd: str, stdin: Path | bytes | None = None, stdout: Path | None = None) -> None:
    """docker exec 한 번(compose를 거치지 않아 빠르다). 실패하면 마지막 오류 몇 줄을 보여 주고 멈춘다."""
    full = ["docker", "exec", "-i", _container(service), *cmd]
    fin = open(stdin, "rb") if isinstance(stdin, Path) else None
    fout = open(stdout, "wb") if stdout else None
    try:
        r = subprocess.run(full, stdin=fin, input=stdin if isinstance(stdin, bytes) else None,
                           stdout=fout if fout else subprocess.PIPE, stderr=subprocess.PIPE)
    finally:
        for f in (fin, fout):
            if f:
                f.close()
    if r.returncode != 0:
        err = r.stderr.decode("utf-8", "replace").strip().splitlines()
        err = [line for line in err if "Using a password" not in line]
        raise SystemExit(f"{service}에서 실패했습니다: {' '.join(cmd[:3])} …\n" + "\n".join(err[-5:]))


# 6-4: MongoDB에 인증을 켜면 랩 도구 계정(lab-ops)으로 붙는다. 인증이 없는 단계(A0~6-3)에서는 환경 변수가 없어 그냥 붙는다
_MONGO_DROP = ('if [ -n "$DAON_MONGO_OPS_PASSWORD" ]; then A="-u lab-ops -p $DAON_MONGO_OPS_PASSWORD --authenticationDatabase admin"; fi; '
               "mongosh --quiet $A --eval \"db.getSiblingDB('mall-port').dropDatabase()\"")


def _purge_rabbit() -> None:
    """이전 주문을 취소하려는 지연 메시지를 비운다. 6-4부터 관리 화면 포트를 PC에 열지 않으므로 컨테이너 안에서 rabbitmqctl로 비운다."""
    import os
    name = f"{os.environ.get('COMPOSE_PROJECT_NAME', 'daon')}-rabbitmq-1"
    for q in ("mall.order.cancel", "mall.order.cancel.ttl"):
        subprocess.run(["docker", "exec", name, "rabbitmqctl", "purge_queue", "-p", "/mall", q], capture_output=True, text=True)   # 큐가 없으면 실패해도 된다


MYSQL = ["mysql", "-uroot", "-proot"]          # [원본 기본값] compose.yml의 root/root
PG = ["-U", "postgres", "-d", "postgres"]


def take_if_pending(stack: str) -> None:
    """up이 끝난 뒤 부른다. 볼륨을 막 지운 뒤라면 스냅샷을 뜬다."""
    if not _pending(stack).exists():
        return
    take(stack)
    _pending(stack).unlink(missing_ok=True)


def _wait_mysql_seeded(stack: str, timeout: int = 180) -> None:
    """MySQL 첫 기동은 초기화용 임시 서버(소켓만) → 진짜 서버 순서다. TCP로 붙고 시드 마지막 표가 찼을 때까지 기다린다."""
    t0 = time.time()
    probe = ["mysql", "-h127.0.0.1", "-uroot", "-proot", "-N", "-e",
             "SELECT 1 FROM mall.ums_integration_change_history LIMIT 1"]   # 30-daon-bulk.sql의 마지막 표
    while time.time() - t0 < timeout:
        r = st.compose(stack, "exec", "-T", "mysql", *probe, check=False, capture=True)
        if r.returncode == 0 and (r.stdout or "").strip():
            return
        time.sleep(2)
    raise SystemExit("MySQL 시드가 다 들어가지 않았습니다(make logs S=mysql).")


def take(stack: str) -> None:
    DIR.mkdir(parents=True, exist_ok=True)
    data, key = _paths(stack)
    t0 = time.time()
    tmp = data.with_suffix(data.suffix + ".tmp")
    if stack == "spring":
        _wait_mysql_seeded(stack)
        _exec(stack, "mysql", "mysqldump", "-uroot", "-proot", "--single-transaction", "--routines", "--triggers",
              "--set-gtid-purged=OFF", "--databases", "mall", stdout=tmp)
    else:
        _exec(stack, "postgres", "pg_dump", *PG, "-Fc", "--data-only", "--schema=public", "--exclude-table=public.migration", stdout=tmp)
    tmp.replace(data)
    key.write_text(_key(stack))
    print(f"[{stack}] 초기화용 스냅샷을 떴습니다({data.stat().st_size // 1024}KB, {time.time() - t0:.1f}초). 이제 make wipe는 몇 초면 됩니다.")


def restore(stack: str) -> float:
    data, _ = _paths(stack)
    t0 = time.time()
    if stack == "spring":
        from concurrent.futures import ThreadPoolExecutor
        # 랩 DB라 되돌리는 동안만 디스크 동기화를 늦춘다(표 80여 개를 다시 만드는 시간이 1/3로 준다). 끝에서 원래대로.
        relax = b"SET GLOBAL innodb_flush_log_at_trx_commit=2; SET GLOBAL sync_binlog=0;\n"
        strict = b"\nSET GLOBAL innodb_flush_log_at_trx_commit=1; SET GLOBAL sync_binlog=1;\n"
        side = []
        with ThreadPoolExecutor(max_workers=3) as pool:
            if _running(stack, "mongo"):
                side.append(pool.submit(_exec, stack, "mongo", "sh", "-c", _MONGO_DROP))
            if _running(stack, "rabbitmq"):
                side.append(pool.submit(_purge_rabbit))
            try:
                _exec(stack, "mysql", *MYSQL, "--init-command=SET sql_log_bin=0", stdin=relax + data.read_bytes() + strict)
            except SystemExit:
                _exec(stack, "mysql", *MYSQL, "-e", strict.decode())
                raise
            for f in side:
                f.result()
        if _running(stack, "redis"):
            # 회원 캐시·주문 번호 카운터·속도 제한·토큰 폐기 목록(DB를 되돌린 뒤에). A3(4-3) 뒤에는 lab-ops 사용자로 붙는다
            r = st.redis_cli("FLUSHALL")
            if r.returncode != 0 or "OK" not in r.stdout:
                raise SystemExit(f"Redis를 비우지 못했습니다: {(r.stdout + r.stderr).strip()[-300:]}")
    else:
        truncate = ("DO $$ DECLARE t text; BEGIN SELECT string_agg(format('%I.%I', schemaname, tablename), ', ') INTO t "
                    "FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'migration'; "
                    "EXECUTE 'TRUNCATE ' || t || ' RESTART IDENTITY CASCADE'; END $$;")
        _exec(stack, "postgres", "psql", "-q", "-v", "ON_ERROR_STOP=1", *PG, "-c", truncate)
        _exec(stack, "postgres", "pg_restore", *PG, "--data-only", "--disable-triggers", "--single-transaction", stdin=data)
    return time.time() - t0


def fast_wipe(stack: str | None) -> bool:
    """스냅샷으로 되돌릴 수 있으면 되돌리고 True. 아니면 False(느린 초기화로)."""
    stacks = [stack] if stack else list(st.STACKS)
    db = {"spring": "mysql", "express": "postgres"}
    todo = [s for s in stacks if _running(s, db[s])]
    if not todo:
        return False
    for s in todo:
        if not ready(s):
            print(f"[{s}] 초기화용 스냅샷이 없거나 시드가 바뀌었습니다. 이번 한 번은 볼륨을 지우는 느린 초기화로 갑니다.")
            return False
    for s in todo:
        secs = restore(s)
        print(f"[{s}] 시드 상태로 되돌렸습니다({secs:.1f}초). 컨테이너는 그대로 떠 있습니다.")
    return True
