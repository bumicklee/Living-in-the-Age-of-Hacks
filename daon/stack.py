"""docker compose 감싸기: 띄우기·내리기·다시 빌드·상태·메모리."""
from __future__ import annotations

import hashlib
import json
import shutil
import socket
import subprocess
import sys
import time
from pathlib import Path

from daon.config import DATA, ENV, INFRA, ROOT, STACKS

COMPOSE = INFRA / "compose.yml"
LAB = INFRA / "compose.lab.yml"   # 늘 겹치는 랩 도구: 고객 웹·스텁·띠·넉넉한 시드(단계와 무관)
ARCH = INFRA / "arch.txt"          # 켠 아키텍처 단계(a02 …). 수업 이동이 관리한다(course/base, 정답)

# 스택마다: 앱 서비스(소스가 바뀌면 다시 빌드), 소스 지문을 잴 곳, PC 쪽 포트(겹침 검사)
APPS = {
    "spring": ["mall-admin", "mall-portal"],
    "express": ["evershop"],
}
SOURCES = {
    "spring": ["spring/pom.xml"] + [f"spring/{m}" for m in
               ("mall-common", "mall-mbg", "mall-security", "mall-demo", "mall-admin", "mall-search", "mall-portal")],
    "express": ["express/package.json", "express/package-lock.json", "express/packages"],
}
PORTS = {
    "spring": ["DAON_MYSQL_PORT", "DAON_REDIS_PORT", "DAON_RABBIT_PORT", "DAON_RABBIT_UI_PORT", "DAON_MONGO_PORT",
               "DAON_MINIO_PORT", "DAON_MINIO_UI_PORT", "DAON_ADMIN_API_PORT", "DAON_PORTAL_API_PORT", "DAON_ADMIN_WEB_PORT",
               "DAON_OPS_PORT"],
    "express": ["DAON_PG_PORT", "DAON_EVERSHOP_PORT"],
}
# 랩 도구(compose.lab.yml): 고객 웹은 spring, 스텁·메일은 두 스택 모두
LAB_PORTS = ["DAON_PAYMENT_PORT", "DAON_SMS_PORT", "DAON_COURIER_PORT", "DAON_MAILPIT_PORT", "DAON_SMTP_PORT"]
PORTS["spring"] = PORTS["spring"] + ["DAON_WEB_PORT"] + LAB_PORTS
PORTS["express"] = PORTS["express"] + LAB_PORTS
SKIP_PARTS = {"target", "node_modules", "dist", ".git", "__pycache__"}
# 인프라 지문: 이것이 바뀌면 앱만이 아니라 스택 전체를 다시 맞춘다(새 서비스·망·설정)
INFRA_SOURCES = ["infra/compose.yml", "infra/compose.lab.yml", "infra/lab-banner", "infra/arch.txt", "infra/egress", "stubs"]


def arch_layers() -> list[str]:
    """infra/arch.txt에서 켠 단계(a02 …). 파일이 있는 덧붙임만 쓴다."""
    if not ARCH.exists():
        return []
    out = []
    for line in ARCH.read_text("utf-8").splitlines():
        name = line.split("#", 1)[0].strip().lower()
        if name and (INFRA / f"compose.{name}.yml").exists() and name not in out:
            out.append(name)
    return out


def compose_files() -> list[Path]:
    return [COMPOSE] + ([LAB] if LAB.exists() else []) + [INFRA / f"compose.{n}.yml" for n in arch_layers()]


def compose(stack: str | None, *args: str, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess:
    if not shutil.which("docker"):
        raise SystemExit("docker가 없습니다. Docker Desktop·OrbStack·Rancher Desktop 중 하나를 설치하세요.")
    profiles = []
    for s in ([stack] if stack else STACKS):
        profiles += ["--profile", s]
    files = []
    for f in compose_files():
        files += ["-f", str(f)]
    cmd = ["docker", "compose", *files, "--project-directory", str(INFRA), *profiles, *args]
    return subprocess.run(cmd, check=check, text=True, capture_output=capture)


def ensure_env() -> None:
    env = INFRA / ".env"
    if not env.exists():
        shutil.copy(INFRA / ".env.example", env)
        print("infra/.env를 만들었습니다(infra/.env.example 복사). 포트를 바꾸려면 이 파일을 고치세요.")


def _our_ports() -> set[int]:
    r = compose(None, "ps", "--format", "json", check=False, capture=True)
    ports: set[int] = set()
    for line in (r.stdout or "").splitlines():
        try:
            items = json.loads(line)
        except ValueError:
            continue
        for item in items if isinstance(items, list) else [items]:
            for p in item.get("Publishers") or []:
                if p.get("PublishedPort"):
                    ports.add(int(p["PublishedPort"]))
    return ports


def check_ports(stack: str) -> None:
    ours = _our_ports()
    busy = []
    for key in PORTS[stack]:
        p = int(ENV.get(key, 0) or 0)
        if not p or p in ours:
            continue
        with socket.socket() as s:
            s.settimeout(0.3)
            if s.connect_ex(("127.0.0.1", p)) == 0:
                busy.append((key, p))
    if busy:
        lines = "\n".join(f"  - {k}={p}" for k, p in busy)
        raise SystemExit(f"이 PC에서 이미 쓰는 포트가 있습니다:\n{lines}\ninfra/.env에서 그 값을 다른 번호로 바꾼 뒤 다시 실행하세요.")


def fingerprint(stack: str) -> str:
    return _hash(SOURCES[stack])


def infra_fingerprint(stack: str) -> str:
    return _hash(INFRA_SOURCES + [f"infra/compose.{n}.yml" for n in arch_layers()] + [f"stack:{stack}"])


def _hash(rels: list[str]) -> str:
    h = hashlib.sha256()
    for rel in rels:
        if rel.startswith("stack:"):
            h.update(rel.encode())
            continue
        base = ROOT / rel
        if not base.exists():
            continue
        files = [base] if base.is_file() else sorted(p for p in base.rglob("*") if p.is_file())
        for f in files:
            if SKIP_PARTS & set(f.relative_to(ROOT).parts):
                continue
            h.update(str(f.relative_to(ROOT)).encode())
            h.update(f.read_bytes())
    return h.hexdigest()[:16]


def _stamp_path(stack: str) -> Path:
    return DATA / f"build-{stack}.txt"


def _infra_stamp(stack: str) -> Path:
    return DATA / f"infra-{stack}.txt"


def built_fingerprint(stack: str) -> str | None:
    p = _stamp_path(stack)
    return p.read_text().strip() if p.exists() else None


def wait_healthy(stack: str, timeout: int = 600) -> None:
    """healthcheck가 있는 서비스가 모두 healthy가 될 때까지 기다린다."""
    t0 = time.time()
    last = ""
    while True:
        r = compose(stack, "ps", "-a", "--format", "json", check=False, capture=True)
        states = []
        for line in (r.stdout or "").splitlines():
            try:
                items = json.loads(line)
            except ValueError:
                continue
            states += items if isinstance(items, list) else [items]
        pending = []
        for s in states:
            name, health, state = s.get("Service"), s.get("Health", ""), s.get("State", "")
            if state == "exited" and s.get("ExitCode", 0) != 0:
                raise SystemExit(f"{name} 컨테이너가 멈췄습니다(종료 코드 {s.get('ExitCode')}). 로그: make logs S={name}")
            if health == "unhealthy":
                raise SystemExit(f"{name}가 unhealthy입니다. 로그: make logs S={name}")
            if health and health != "healthy":
                pending.append(f"{name}({health})")
        if states and not pending:
            print(f"모두 준비됐습니다({int(time.time() - t0)}초).")
            return
        msg = "기다리는 중: " + ", ".join(pending)
        if msg != last:
            print(msg)
            last = msg
        if time.time() - t0 > timeout:
            raise SystemExit(f"{timeout}초 안에 준비되지 않았습니다. make logs S=<서비스>로 확인하세요.")
        time.sleep(3)


def up(stack: str, *, build: bool = False) -> None:
    ensure_env()
    check_ports(stack)
    fp = fingerprint(stack)
    need_build = build or built_fingerprint(stack) != fp
    args = ["up", "-d", "--remove-orphans"] + (["--build"] if need_build else [])
    compose(stack, *args)
    wait_healthy(stack)
    DATA.mkdir(exist_ok=True)
    if need_build:
        _stamp_path(stack).write_text(fp)
    _infra_stamp(stack).write_text(infra_fingerprint(stack))
    if stack == "express":
        setup_express()


def ensure_fresh(stack: str) -> bool:
    """수업 이동 등으로 앱 소스가 바뀌었으면 앱 이미지만 다시 빌드해 띄운다. 다시 빌드했으면 True."""
    fp = fingerprint(stack)
    ifp = infra_fingerprint(stack)
    p = _infra_stamp(stack)
    app_changed = built_fingerprint(stack) != fp
    infra_changed = (p.read_text().strip() if p.exists() else None) != ifp
    if not app_changed and not infra_changed:
        return False
    if infra_changed:
        layers = ", ".join(arch_layers()) or "A0만"
        print(f"[{stack}] 인프라 구성이 바뀌어 스택을 다시 맞춥니다(켠 단계: {layers})")
        compose(stack, "up", "-d", "--remove-orphans", *(["--build"] if app_changed else []))
    else:
        print(f"[{stack}] 소스가 바뀌어 앱을 다시 빌드합니다: {', '.join(APPS[stack])}")
        compose(stack, "up", "-d", "--build", "--no-deps", *APPS[stack])
    wait_healthy(stack)
    DATA.mkdir(exist_ok=True)
    _stamp_path(stack).write_text(fp)
    p.write_text(ifp)
    return True


EVERSHOP_ADMIN = {"name": "다온 관리자", "email": "admin@daon.example", "password": "daon-admin-1!"}


def setup_express() -> None:
    """EverShop 처음 한 번: 관리자 계정과 데모 상품. 표는 앱이 뜰 때 스스로 만든다(migration)."""
    mark = DATA / "express-setup.txt"
    if mark.exists():
        return
    a = EVERSHOP_ADMIN
    cli = ["exec", "-T", "evershop", "node", "packages/evershop/dist/bin/evershop.js"]   # 저장소 안에서는 evershop 명령이 PATH에 없다
    compose("express", *cli, "user:create", "--name", a["name"], "--email", a["email"], "--password", a["password"])
    r = compose("express", *cli, "seed", "--all", check=False)
    if r.returncode != 0:
        print("데모 상품 넣기(evershop seed)가 실패했습니다. 상품 없이 계속합니다.", file=sys.stderr)
    seed_express()
    DATA.mkdir(exist_ok=True)
    mark.write_text(a["email"])
    print(f"EverShop 관리자: {a['email']} / {a['password']}  (http://127.0.0.1:{ENV.get('DAON_EVERSHOP_PORT', 3000)}/admin)")


def seed_express() -> None:
    """다온마켓 가짜 시드(seed/express/*.sql, tools/gen_seed.py가 만든다)를 PostgreSQL에 넣는다. Spring과 같은 계정·번호."""
    for f in sorted((ROOT / "seed" / "express").glob("*.sql")):
        files = []
        for c in ([COMPOSE] + ([LAB] if LAB.exists() else [])):
            files += ["-f", str(c)]
        cmd = ["docker", "compose", *files, "--project-directory", str(INFRA), "--profile", "express",
               "exec", "-T", "postgres", "psql", "-q", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "postgres"]
        with open(f, "rb") as fin:
            r = subprocess.run(cmd, stdin=fin, capture_output=True)
        if r.returncode != 0:
            raise SystemExit(f"Express 시드({f.name})를 넣지 못했습니다:\n{r.stderr.decode('utf-8', 'replace')[-800:]}")
        print(f"Express 시드를 넣었습니다: seed/express/{f.name}")


def down(stack: str | None, *, wipe: bool = False) -> None:
    compose(stack, "down", *(["-v"] if wipe else []))
    if wipe:
        for s in ([stack] if stack else STACKS):
            _stamp_path(s).unlink(missing_ok=True)
            _infra_stamp(s).unlink(missing_ok=True)
            if s == "express":
                (DATA / "express-setup.txt").unlink(missing_ok=True)


def mem() -> None:
    """지금 떠 있는 다온마켓 컨테이너의 메모리(docker stats 한 번)."""
    r = subprocess.run(["docker", "stats", "--no-stream", "--format", "{{json .}}"], text=True, capture_output=True, check=True)
    rows = []
    total = 0.0
    for line in r.stdout.splitlines():
        d = json.loads(line)
        if not d["Name"].startswith("daon-"):
            continue
        used = d["MemUsage"].split("/")[0].strip()
        rows.append((d["Name"], used, d["MemUsage"].split("/")[1].strip(), d["CPUPerc"]))
        total += _mib(used)
    if not rows:
        print("떠 있는 다온마켓 컨테이너가 없습니다. make up STACK=spring")
        return
    w = max(len(r[0]) for r in rows)
    print(f"{'컨테이너'.ljust(w)}  사용      상한      CPU")
    for name, used, limit, cpu in sorted(rows):
        print(f"{name.ljust(w)}  {used:<9} {limit:<9} {cpu}")
    print(f"합계 약 {total / 1024:.2f} GiB")


def _mib(s: str) -> float:
    s = s.strip()
    for unit, mul in (("GiB", 1024), ("MiB", 1), ("KiB", 1 / 1024), ("B", 1 / 1024 / 1024)):
        if s.endswith(unit):
            return float(s[: -len(unit)]) * mul
    return 0.0
