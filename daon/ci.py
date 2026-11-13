"""A8 로컬 CI 도우미(7-5·7-6): Gitea·레지스트리를 PC에서 다루는 랩 도구. 랩 전용, 127.0.0.1만 부른다.

- Gitea API(관리자 계정, 값은 infra/.env의 가짜 값)로 저장소·태그·작업 결과를 다룬다. 값은 어디에도 찍지 않는다.
- 「CI 저장소」(daon/market)에는 monorepo 전체가 아니라 CI에 필요한 파일만 올린다: .gitea/workflows, tools/ci, tools/secevent.py, ci/, infra/dns(11-5 영역 파일 점검).
- 이미지 파일(tar)은 러너 밖의 빌드 단계(여기서는 PC의 docker)가 만든다. 러너는 도커 소켓을 받지 않는다(7-5).
"""
from __future__ import annotations

import base64
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from daon.config import DATA, ENV, ROOT, port

CI_URL = f"http://127.0.0.1:{port('DAON_CI_PORT', 3300)}"
REG_HOST = f"127.0.0.1:{port('DAON_CI_REGISTRY_PORT', 5050)}"
CI_DIR = DATA / "ci"
IN_DIR = CI_DIR / "in"
ADMIN = "lab-admin"
SCAN_STEP = "daon-lab/scan-step"
SERVICE = "mall-portal"
NET = "daon_ci-run"                      # 러너·Gitea·레지스트리가 있는 internal 망(compose 프로젝트 daon)
TOOLS_IMAGE = "daon/ci-tools:lab"
MIRROR = [".gitea/workflows", "tools/ci", "ci", "infra/dns"]   # infra/dns: 11-5 DNS 영역 파일 점검(tools/ci/audit_dns.py)
MIRROR_FILES = ["tools/secevent.py"]


class CiNotReady(Exception):
    pass


def _pw() -> str:
    return ENV.get("DAON_CI_ADMIN_PASSWORD", "")


def api(method: str, path: str, **kw) -> httpx.Response:
    try:
        return httpx.request(method, f"{CI_URL}/api/v1{path}", auth=(ADMIN, _pw()), timeout=20.0, **kw)
    except httpx.TransportError as e:
        raise CiNotReady(f"로컬 Gitea({CI_URL})에 닿지 않습니다. 7-5 지점에서 make up STACK=spring 으로 A8을 켜세요. ({e.__class__.__name__})")


def available() -> bool:
    try:
        return httpx.get(f"{CI_URL}/api/healthz", timeout=3.0).status_code == 200
    except httpx.TransportError:
        return False


def require() -> None:
    if not available():
        raise CiNotReady("로컬 CI(Gitea)가 꺼져 있습니다. 7-5 지점에서 make up STACK=spring 으로 A8을 켜세요(infra/arch.txt에 a08).")
    if not _runner_online():
        raise CiNotReady("CI 러너가 아직 등록되지 않았습니다. 잠시 뒤 다시 하거나 make logs S=runner 로 확인하세요.")


def _runner_online() -> bool:
    r = api("GET", "/admin/actions/runners")
    return r.status_code == 200 and any(x.get("status") == "online" for x in r.json().get("runners", []))


# ───────────── 외부 단계(daon-lab/scan-step) ─────────────
def seed_sha() -> str:
    """씨앗 커밋(가장 오래된 커밋). 고정 시각·작성자라 모든 PC에서 같은 해시다."""
    r = api("GET", f"/repos/{SCAN_STEP}/commits", params={"sha": "main", "limit": 50})
    r.raise_for_status()
    return r.json()[-1]["sha"]


def tag_target(tag: str = "v1") -> str:
    r = api("GET", f"/repos/{SCAN_STEP}/git/refs/tags/{tag}")
    r.raise_for_status()
    data = r.json()
    data = data[0] if isinstance(data, list) else data
    return data["object"]["sha"]


def _set_tag(tag: str, sha: str) -> None:
    api("DELETE", f"/repos/{SCAN_STEP}/tags/{tag}")
    r = api("POST", f"/repos/{SCAN_STEP}/tags", json={"tag_name": tag, "target": sha})
    r.raise_for_status()


def tag_move() -> str:
    """로컬 Gitea 안에서만: v1 태그를 「바뀐 단계」 커밋으로 옮긴다(공급망 사고 재연). 반환: 새 커밋 해시"""
    changed = (ROOT / "infra/gitea/seed/scan-step-changed/action.yml").read_bytes()
    cur = api("GET", f"/repos/{SCAN_STEP}/contents/action.yml", params={"ref": "main"}).json()
    r = api("PUT", f"/repos/{SCAN_STEP}/contents/action.yml", json={
        "content": base64.b64encode(changed).decode(), "sha": cur["sha"], "branch": "main",
        "message": "scan-step: 점검 문구 갱신(교육용 시뮬레이션)", "author": {"name": "daon-lab", "email": "lab@example.invalid"}})
    r.raise_for_status()
    sha = r.json()["commit"]["sha"]
    _set_tag("v1", sha)
    return sha


def tag_restore() -> None:
    _set_tag("v1", seed_sha())


# ───────────── CI 저장소 올리기·작업 기다리기 ─────────────
def _mirror_tree(dest: Path) -> list[str]:
    copied = []
    for rel in MIRROR:
        src = ROOT / rel
        if src.is_dir():
            for f in sorted(src.rglob("*")):
                if f.is_file() and "__pycache__" not in f.parts:
                    t = dest / f.relative_to(ROOT)
                    t.parent.mkdir(parents=True, exist_ok=True)
                    t.write_bytes(f.read_bytes())
                    copied.append(str(f.relative_to(ROOT)))
    for rel in MIRROR_FILES:
        f = ROOT / rel
        if f.exists():
            t = dest / rel
            t.parent.mkdir(parents=True, exist_ok=True)
            t.write_bytes(f.read_bytes())
            copied.append(rel)
    return copied


def push_ci(message: str = "ci: 파일 올림", override: dict[str, str] | None = None) -> str:
    """CI 저장소(daon/market) main에 지금 CI 파일들을 새 커밋 하나로 올린다(이력은 쌓인다: 배포 확인이 「출처 커밋이 main에 있나」를 본다).
    반환: 커밋 해시. push가 작업을 시작시킨다."""
    require()
    auth = base64.b64encode(f"{ADMIN}:{_pw()}".encode()).decode()
    env = os.environ | {"GIT_AUTHOR_NAME": "daon-lab", "GIT_AUTHOR_EMAIL": "lab@example.invalid",
                        "GIT_COMMITTER_NAME": "daon-lab", "GIT_COMMITTER_EMAIL": "lab@example.invalid"}
    hdr = ["git", "-c", f"http.extraHeader=Authorization: Basic {auth}"]
    with tempfile.TemporaryDirectory() as d:
        w = Path(d) / "repo"
        clone = subprocess.run(hdr + ["clone", "-q", f"{CI_URL}/daon/market.git", str(w)], env=env, capture_output=True, text=True)
        if clone.returncode:                                  # 빈 저장소(처음)면 새로 시작
            w.mkdir()
            subprocess.run(["git", "init", "-q", "-b", "main"], cwd=w, env=env, check=True, capture_output=True)
            subprocess.run(["git", "remote", "add", "origin", f"{CI_URL}/daon/market.git"], cwd=w, env=env, check=True, capture_output=True)
        for child in w.iterdir():                             # 내려받은 파일을 모두 지우고(.git 제외) 지금 CI 파일로 채운다
            if child.name != ".git":
                shutil.rmtree(child) if child.is_dir() else child.unlink()
        files = _mirror_tree(w)
        for rel, text in (override or {}).items():            # 시험용: 올릴 파일 내용을 바꿔서(내 작업 폴더는 그대로)
            (w / rel).parent.mkdir(parents=True, exist_ok=True)
            (w / rel).write_text(text, "utf-8")
            files.append(rel)
        if not any(f.startswith(".gitea/workflows/") for f in files):
            raise CiNotReady("올릴 워크플로(.gitea/workflows/*.yml)가 없습니다. make goto L=7-5 로 지점을 맞추세요.")
        msg = f"{message} ({time.strftime('%H:%M:%S')}-{time.time_ns() % 100000})"        # 같은 내용을 다시 올려도 새 커밋이 되도록
        run = lambda *a: subprocess.run(list(a), cwd=w, env=env, check=True, capture_output=True, text=True)
        run("git", "add", "-A")
        run("git", "commit", "-q", "--allow-empty", "-m", msg)
        sha = run("git", "rev-parse", "HEAD").stdout.strip()
        r = subprocess.run(hdr + ["push", "-q", "origin", "HEAD:main"], cwd=w, env=env, capture_output=True, text=True)
        if r.returncode:
            raise CiNotReady("CI 저장소에 올리지 못했습니다: " + re.sub(r"Basic \S+", "Basic ***", r.stderr)[-300:])
        return sha


TERMINAL = {"success", "failure", "cancelled", "skipped"}


@dataclass
class Run:
    sha: str
    status: str                       # success | failure | timeout
    jobs: dict[str, str] = field(default_factory=dict)
    log: str = ""
    seconds: float = 0.0

    def has(self, text: str) -> bool:
        return text in self.log


def expected_jobs() -> int:
    n = 0
    for f in (ROOT / ".gitea/workflows").glob("*.yml"):
        text = f.read_text("utf-8")
        body = text.split("\njobs:", 1)[-1]
        n += len(re.findall(r"^  [A-Za-z0-9_-]+:\s*$", body, flags=re.M))
    return max(n, 1)


def wait_run(sha: str, timeout: float = 240.0) -> Run:
    t0 = time.time()
    want = expected_jobs()
    stable_since = None
    last = ""
    while time.time() - t0 < timeout:
        r = api("GET", "/repos/daon/market/actions/tasks", params={"limit": 50})
        tasks = [t for t in r.json().get("workflow_runs", []) if t.get("head_sha") == sha] if r.status_code == 200 else []
        state = ",".join(f"{t['name']}={t['status']}" for t in tasks)
        done = tasks and all(t["status"] in TERMINAL for t in tasks)
        failed = any(t["status"] in ("failure", "cancelled") for t in tasks)
        if done and (len(tasks) >= want or failed):
            if state != last:
                stable_since = time.time()
                last = state
            if time.time() - (stable_since or time.time()) >= 4:
                logs = []
                for t in sorted(tasks, key=lambda x: x["id"]):
                    logs.append(f"===== 작업 {t['name']} =====\n" + task_log(t["id"]))
                status = "failure" if failed or len(tasks) < want else "success"
                return Run(sha, status, {t["name"]: t["status"] for t in tasks}, "\n".join(logs), time.time() - t0)
        else:
            last = state
            stable_since = None
        time.sleep(2)
    return Run(sha, "timeout", {}, "", time.time() - t0)


_JOB_OFFSET = [0]


def task_log(task_id: int) -> str:
    """작업(task) 번호로 로그를 찾는다. API의 로그 주소는 작업 번호가 아니라 job 번호를 받고, 둘은 건너뛰는 때가 있어(막혀서 안 돈 job) 맞는 것을 찾아 본다."""
    start = max(1, task_id + _JOB_OFFSET[0])
    order = list(range(start, start + 64)) + list(range(max(1, start - 6), start))   # lint 실패로 건너뛴 job이 쌓이면 차이가 커진다(2026-11-10 캡처 때 12를 넘음)
    for jid in order:
        r = api("GET", f"/repos/daon/market/actions/jobs/{jid}/logs")
        if r.status_code == 200 and f"received task {task_id} of job" in r.text:
            _JOB_OFFSET[0] = jid - task_id
            return r.text
    return ""


def run_pipeline(message: str = "ci: 실행", override: dict[str, str] | None = None) -> Run:
    sha = push_ci(message, override)
    run = wait_run(sha)
    record_events(run.log)
    return run


EVENT_LINE = re.compile(r"SECURITY (\{.*\})")


def record_events(log: str) -> list[dict]:
    """CI 로그에 찍힌 보안 이벤트 줄을 PC의 reports/security-events.jsonl 에도 모은다(CI 안 reports는 작업이 끝나면 사라진다)."""
    import sys
    sys.path.insert(0, str(ROOT / "tools"))
    from secevent import reports_dir
    out = []
    for m in EVENT_LINE.finditer(log):
        try:
            d = json.loads(m.group(1))
        except ValueError:
            continue
        out.append(d)
        p = reports_dir()
        p.mkdir(parents=True, exist_ok=True)
        with (p / "security-events.jsonl").open("a", encoding="utf-8") as f:
            f.write(json.dumps(d, ensure_ascii=False) + "\n")
    return out


# ───────────── 이미지: 빌드 단계(러너 밖)와 레지스트리 ─────────────
def build_image(label: str, *, tag: str | None = None, out: Path | None = None) -> Path:
    """daon/mall-portal:dev 위에 라벨만 얹은 이미지를 만들어 tar로 저장한다(= 러너가 받아 올릴 빌드 결과). 값은 라벨 하나다."""
    src = f"daon/{SERVICE}:dev"
    if subprocess.run(["docker", "image", "inspect", src], capture_output=True).returncode:
        raise CiNotReady(f"{src} 이미지가 없습니다. make up STACK=spring 으로 먼저 빌드하세요.")
    IN_DIR.mkdir(parents=True, exist_ok=True)
    tag = tag or f"daon/{SERVICE}:lesson"
    nonce = f"LABEL daon.swap-id={time.time_ns()}\n" if label == "unsigned-demo" else ""      # 바꿔치기 이미지는 올릴 때마다 다른 다이제스트(이전 시험의 서명이 따라붙지 않게)
    dockerfile = f"FROM {src}\nLABEL daon.build={label}\n{nonce}"
    subprocess.run(["docker", "build", "-q", "-t", tag, "-"], input=dockerfile, text=True, check=True, capture_output=True)
    out = out or IN_DIR / f"{SERVICE}.tar"
    subprocess.run(["docker", "save", "-o", str(out), tag], check=True)
    return out


def _skopeo(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(["docker", "run", "--rm", "--network", NET, "--entrypoint", "skopeo", TOOLS_IMAGE, *args], capture_output=True, text=True)


def registry_digest(tag: str = "lesson") -> str | None:
    r = _skopeo("inspect", "--tls-verify=false", "--creds", f"{ADMIN}:{_pw()}", f"docker://registry:5000/daon/{SERVICE}:{tag}", "--format", "{{.Digest}}")
    return r.stdout.strip() or None


def image_swap() -> str:
    """CI 밖에서(운영자 계정으로) 같은 태그에 다른 이미지를 올린다: 레지스트리 쓰기 권한이 있는 다른 사람·작업을 흉내 낸다.
    다른 점은 라벨 하나(daon.build=unsigned-demo). 반환: 새 다이제스트"""
    tar = build_image("unsigned-demo", tag=f"daon/{SERVICE}:swap", out=CI_DIR / "swap.tar")
    # 임시 파일을 레지스트리와 같은 망에서 올린다(tar를 컨테이너에 넘긴다)
    r = subprocess.run(["docker", "run", "--rm", "--network", NET, "-v", f"{tar}:/in.tar:ro", "--entrypoint", "skopeo", TOOLS_IMAGE,
                        "copy", "--dest-tls-verify=false", "--dest-creds", f"{ADMIN}:{_pw()}", "docker-archive:/in.tar", f"docker://registry:5000/daon/{SERVICE}:lesson"],
                       capture_output=True, text=True)
    if r.returncode:
        raise CiNotReady("바꿔치기 이미지를 올리지 못했습니다: " + re.sub(r"creds \S+", "creds ***", r.stderr)[-300:])
    return registry_digest() or ""
