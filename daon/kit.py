"""15-4 secure-kit 예제 앱 네 개를 도커로 빌드·실행하고 계약 시험을 돌린다(랩 전용, 127.0.0.1에만 연다).

    make kit-up [APPS=spring-shop,express-shop]   빌드(바뀐 것만) + 실행. 처음에는 Maven·npm 내려받기(인터넷 필요)
    make kit-down                                  예제 앱 컨테이너 지우기
    make kit-contract [APPS=…]                     packages/contract-tests(부품 24개 + 가게 셋 + 목록 정적 확인)

앱                주소                     소스
spring-demo       127.0.0.1:18181          packages/examples/spring-kit-demo  (+ packages/secure-kit-spring)
express-demo      127.0.0.1:18182          packages/examples/express-kit-demo (+ packages/secure-kit-express)
spring-shop       127.0.0.1:18183          packages/examples/spring-shop      (15-4: 부품 셋을 붙이기 전/후, make goto가 바꾼다)
express-shop      127.0.0.1:18184          packages/examples/express-shop

빌드는 PC에 JDK·Node가 없어도 되도록 컨테이너 안에서 한다(Maven 캐시는 도커 볼륨 daon-m2, 5-2 단위 시험과 같은 것).
소스 지문이 바뀌면 다시 빌드하고 다시 띄운다(수업 이동 뒤 시험이 저절로 새 코드를 본다).
"""
from __future__ import annotations

import hashlib
import os
import subprocess
import sys
import time
from pathlib import Path

import httpx

from daon.config import DATA, ROOT

PKG = ROOT / "packages"
MAVEN = "maven:3.9.12-eclipse-temurin-17"
JRE = "eclipse-temurin:17.0.17_10-jre"
NODE = "node:22.21.1-alpine"
APPS = {
    "spring-demo": {"stack": "spring", "dir": "examples/spring-kit-demo", "jar": "spring-kit-demo.jar", "port": 18181, "probe": "/kit/public"},
    "express-demo": {"stack": "express", "dir": "examples/express-kit-demo", "port": 18182, "probe": "/kit/public"},
    "spring-shop": {"stack": "spring", "dir": "examples/spring-shop", "jar": "spring-shop.jar", "port": 18183, "probe": "/api/health"},
    "express-shop": {"stack": "express", "dir": "examples/express-shop", "port": 18184, "probe": "/api/health"},
}
STAMPS = DATA / "kit"
SKIP = {"target", "node_modules", "__pycache__", ".DS_Store", "package-lock.json"}   # 잠금 파일은 npm install이 만든다(지문이 계속 바뀌지 않게)


def container(app: str) -> str:
    return f"daon-kit-{app}"


def fingerprint(app: str) -> str:
    a = APPS[app]
    roots = [PKG / a["dir"], PKG / ("secure-kit-spring" if a["stack"] == "spring" else "secure-kit-express")]
    h = hashlib.sha256()
    for root in roots:
        for f in sorted(p for p in root.rglob("*") if p.is_file() and not (SKIP & set(p.relative_to(root).parts))):
            h.update(str(f.relative_to(PKG)).encode())
            h.update(f.read_bytes())
    return h.hexdigest()[:16]


def _docker(*args: str, timeout: int = 1800) -> subprocess.CompletedProcess:
    return subprocess.run(["docker", *args], capture_output=True, text=True, timeout=timeout)


def build(app: str) -> None:
    a = APPS[app]
    if a["stack"] == "spring":
        script = (f"mvn -B -q -f secure-kit-spring/pom.xml install -DskipTests && mvn -B -q -f {a['dir']}/pom.xml package -DskipTests")
        r = _docker("run", "--rm", "-v", f"{PKG}:/src", "-v", "daon-m2:/root/.m2", "-w", "/src", MAVEN, "sh", "-c", script)
    else:
        r = _docker("run", "--rm", "-v", f"{PKG}:/src", "-w", f"/src/{a['dir']}", NODE, "npm", "install", "--no-audit", "--no-fund")
    if r.returncode:
        raise SystemExit(f"{app} 빌드 실패:\n" + "\n".join((r.stdout + r.stderr).strip().splitlines()[-15:]))


def start(app: str) -> None:
    a = APPS[app]
    _docker("rm", "-f", container(app))
    pub = f"127.0.0.1:{a['port']}:{a['port']}"
    if a["stack"] == "spring":
        args = ["run", "-d", "--name", container(app), "-p", pub, "--memory", "384m",
                "-v", f"{PKG / a['dir'] / 'target'}:/app:ro", JRE, "java", "-Xmx192m", "-jar", f"/app/{a['jar']}"]
    else:
        args = ["run", "-d", "--name", container(app), "-p", pub, "--memory", "192m", "-e", f"PORT={a['port']}",
                "-v", f"{PKG}:/src:ro", "-w", f"/src/{a['dir']}", NODE, "node", "server.js"]
    r = _docker(*args, timeout=120)
    if r.returncode:
        raise SystemExit(f"{app}을 띄우지 못했습니다: {r.stderr.strip()[-200:]}")


def running(app: str) -> bool:
    r = _docker("inspect", "-f", "{{.State.Running}}", container(app), timeout=20)
    return r.returncode == 0 and r.stdout.strip() == "true"


def wait(app: str, timeout: float = 120.0) -> bool:
    a = APPS[app]
    t0 = time.time()
    while time.time() - t0 < timeout:
        try:
            if httpx.get(f"http://127.0.0.1:{a['port']}{a['probe']}", timeout=3).status_code < 500:
                return True
        except httpx.HTTPError:
            pass
        time.sleep(2)
    return False


def ensure(apps: list[str] | None = None, echo=print) -> None:
    """소스가 바뀌었거나 떠 있지 않으면 빌드·실행. 시험(15-4)이 먼저 부른다"""
    STAMPS.mkdir(parents=True, exist_ok=True)
    for app in apps or list(APPS):
        fp = fingerprint(app)
        stamp = STAMPS / f"{app}.stamp"
        if stamp.exists() and stamp.read_text().strip() == fp and running(app):
            continue
        echo(f"[kit] {app}: 빌드·실행(소스 지문 {fp})")
        build(app)
        start(app)
        if not wait(app):
            raise SystemExit(f"{app}이 뜨지 않습니다: docker logs {container(app)}")
        stamp.write_text(fp)


def down() -> None:
    for app in APPS:
        _docker("rm", "-f", container(app), timeout=60)
        (STAMPS / f"{app}.stamp").unlink(missing_ok=True)


def _apps(arg: str | None) -> list[str]:
    names = [x.strip() for x in (arg or "").split(",") if x.strip()] or list(APPS)
    bad = [n for n in names if n not in APPS]
    if bad:
        raise SystemExit(f"없는 예제 앱: {bad} (있는 것: {', '.join(APPS)})")
    return names


def cmd_kit_up(a) -> None:
    ensure(_apps(a.apps))
    for app in _apps(a.apps):
        print(f"  {app:<13} http://127.0.0.1:{APPS[app]['port']}")


def cmd_kit_down(a) -> None:
    down()
    print("예제 앱 컨테이너를 지웠습니다")


def cmd_kit_contract(a) -> None:
    apps = _apps(a.apps)
    ensure(apps)
    env = os.environ | {"KIT_APPS": ",".join(apps)}
    sys.exit(subprocess.call([sys.executable, "-m", "pytest", "-c", str(PKG / "contract-tests" / "pytest.ini"), str(PKG / "contract-tests")],
                             cwd=PKG / "contract-tests", env=env))


def register(add, stack_opt) -> None:
    add("kit-up", cmd_kit_up, (["--apps"], {}))
    add("kit-down", cmd_kit_down)
    add("kit-contract", cmd_kit_contract, (["--apps"], {}))
