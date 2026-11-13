"""섹션 14 랩 도구: 첫 한 시간(14-4). 모의 침해 재생·증거 보존.

    make drill D=14-4                    모의 침해 재생(replay/drills/d14_04.py). 8-7의 make drill(경보 훈련)과 같은 명령, D로 고른다
    make evidence INCIDENT=INC-14-4      증거 묶음: 컨테이너 로그·DB 스냅샷(읽기 전용 덤프)·실행 중 이미지 다이제스트 → sha256 목록 →
                                         잠금 버킷(A11) incident/<번호>/에 올림(backup-writer, 올리기만. 사고 대응자도 지울 수 없다)

증거는 .daon/incident/<번호>/evidence/에 모이고 목록은 .daon/incident/<번호>/evidence.sha256(첫 줄에 모은 시각·모은 사람).
DB 스냅샷에는 가짜 시드의 개인정보 칸이 암호문 그대로 들어 있다(5-3). 묶음을 랩 밖으로 옮기지 않는다(git에 넣지 않음).
"""
from __future__ import annotations

import getpass
import gzip
import hashlib
import json
import re
import subprocess
import time
from datetime import datetime
from pathlib import Path

from daon import backup as bk
from daon import stack as st
from daon.config import DATA, ROOT, current_stack
from replay import NotReady

LOG_SERVICES = ["gateway", "mall-portal", "mall-admin", "approval", "backup-audit", "rabbitmq"]


def incident_dir(incident: str) -> Path:
    if not re.fullmatch(r"[A-Za-z0-9-]{3,40}", incident or ""):
        raise SystemExit("사고 번호는 영문·숫자·하이픈 3~40자(예: INC-14-4)")
    return DATA / "incident" / incident


def collect(incident: str, stack: str = "spring", echo=print) -> dict:
    """증거 묶음을 만들고 잠금 버킷에 올린다. 결과 dict(files·uploaded·seconds)"""
    t0 = time.monotonic()
    base = incident_dir(incident)
    out = base / "evidence"
    out.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().astimezone().isoformat(timespec="seconds")
    have = set((st.compose(stack, "config", "--services", check=False, capture=True).stdout or "").split())
    # 1) 가장 빨리 사라지는 것부터: 컨테이너 로그(컨테이너를 다시 만들면 사라진다)
    for svc in [s for s in LOG_SERVICES if s in have]:
        r = st.compose(stack, "logs", "--no-color", "--timestamps", svc, check=False, capture=True)
        (out / f"{svc}.log").write_text(r.stdout or "", "utf-8")
    echo(f"① 컨테이너 로그 {len([s for s in LOG_SERVICES if s in have])}개")
    audit = subprocess.run(["docker", "exec", bk.container("mall-admin"), "cat", "/var/logs/daon/audit.jsonl"], capture_output=True)
    if audit.returncode == 0:
        (out / "audit.jsonl").write_bytes(audit.stdout)
        echo("   감사 로그(8-3, 해시 체인 8-6) 사본")
    # 2) DB 스냅샷(읽기 전용 덤프). 원본 DB는 바꾸지 않는다
    dump = subprocess.run(["docker", "exec", "-e", "MYSQL_PWD=root", bk.container("mysql"), "mysqldump", "-uroot", "--single-transaction",
                           "--no-tablespaces", "--set-gtid-purged=OFF", "mall"], capture_output=True)
    if dump.returncode == 0:
        (out / "mall.sql.gz").write_bytes(gzip.compress(dump.stdout))
        echo(f"② DB 스냅샷 mall.sql.gz({len(dump.stdout) // 1024}KB 원본)")
    # 3) 실행 중 이미지 다이제스트(무엇이 돌고 있었나)
    imgs = st.compose(stack, "images", "--format", "json", check=False, capture=True)
    (out / "images.json").write_text(imgs.stdout or "[]", "utf-8")
    ps = subprocess.run(["docker", "ps", "--no-trunc", "--format", "{{.Names}}\t{{.Image}}\t{{.ID}}"], capture_output=True, text=True)
    (out / "containers.tsv").write_text(ps.stdout or "", "utf-8")
    echo("③ 이미지 다이제스트·컨테이너 목록")
    # 4) sha256 목록(첫 줄: 모은 시각·모은 사람)
    files = sorted(p for p in out.iterdir() if p.is_file())
    lines = [f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}" for p in files]
    who = getpass.getuser()
    (base / "evidence.sha256").write_text(f"# {stamp} · 모은 사람 {who} · 사고 {incident}\n" + "\n".join(lines) + "\n", "utf-8")
    echo(f"④ sha256 목록 {len(lines)}개: {(base / 'evidence.sha256').relative_to(ROOT)}")
    # 5) 잠금 버킷(A11)에 올림: backup-writer(올리기만). 올린 뒤에는 사고 대응자도 지울 수 없다
    uploaded = False
    if bk.vault_on():
        r = bk.mc(f"mc cp --quiet --recursive /ev/ vault/{bk.BUCKET}/incident/{incident}/ >/dev/null && echo OK",
                  net="backup", creds="backup-writer-creds", mounts=[f"{base}:/ev:ro"])
        uploaded = r.stdout.strip().endswith("OK")
        echo(f"⑤ 잠금 버킷 {bk.BUCKET}/incident/{incident}/ 올림: {'끝' if uploaded else '실패(' + (r.stderr or '').strip()[-120:] + ')'}")
    else:
        echo("⑤ 잠금 버킷이 없습니다(A11, 14-2 완료 지점부터). 묶음은 PC에만 있습니다")
    took = round(time.monotonic() - t0, 1)
    size = sum(p.stat().st_size for p in files)
    result = {"incident": incident, "files": len(lines), "bytes": size, "uploaded": uploaded, "seconds": took, "at": stamp}
    (base / "evidence.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), "utf-8")
    echo(f"증거 {len(lines)}개 · {size // 1024}KB · {took}초")
    return result


def uploaded_list(incident: str) -> list[str]:
    """잠금 버킷에 올라간 증거 파일 이름(backup-restorer로 읽기)"""
    r = bk.mc(f"mc ls --recursive --json vault/{bk.BUCKET}/incident/{incident}/", net="backup", creds="backup-restorer-creds")
    return [str(d.get("key")) for d in bk._json_lines(r.stdout) if d.get("type") == "file"]


def cmd_evidence(a) -> None:
    stack = a.stack or current_stack()
    try:
        r = collect(a.incident or "INC-LAB", stack)
    except NotReady as e:
        raise SystemExit(f"[준비 안 됨] {e}")
    if not r["uploaded"]:
        print("잠금 버킷에 올리지 못했습니다. 14-2 완료 지점(A11)인지, make backup-status로 확인하세요")


def drill(a) -> None:
    """make drill D=14-4 (observe.cmd_drill이 D를 보고 부른다)"""
    from replay.drills import d14_04
    print("모의 침해 재생 14-4 시작(127.0.0.1, 가짜 계정 daon117·118·119, 앞 장에서 막아 둔 요청만)")
    try:
        d14_04.run()
    except NotReady as e:
        raise SystemExit(f"[준비 안 됨] {e}")
    print("다음: python -m daon timeline --actor member:117 --since 10m --trace-prefix drill-14-4- --incident INC-14-4")


def register(add, stack_opt) -> None:
    add("evidence", cmd_evidence, stack_opt, (["--incident"], {}))
