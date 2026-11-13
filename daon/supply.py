"""섹션 7 랩 명령: 공급망(SBOM·KEV·원본 패치)·로컬 CI(A8)·배포 확인·캐시 비우기. 교육용 랩 전용.

  make sbom                       7-2  SBOM(CycloneDX) 만들기
  make kev                        7-2  SBOM과 KEV 사본 대조 + 결정 기록 확인
  make upstream-sim               7-3  가짜 원본 저장소와 보안 패치 커밋 하나
  make upstream-status            7-3  반영 여부·걸린 시간(DAON_FAKE_NOW=+74h 로 시계를 돌려 본다)
  make ci-lint                    7-5  CI 파일의 외부 단계 고정 검사(tools/ci/check_pins.py)
  make ci-run                     7-5  CI 저장소에 올리고 작업이 끝날 때까지 기다려 결과를 본다
  make ci-sim-tag-move            7-5  로컬 Gitea 안에서만 외부 단계의 v1 태그를 다른 커밋으로 옮긴다(UNDO=1이면 되돌림)
  make ci-build                   7-5  이미지 빌드 단계(러너 밖): daon/mall-portal:lesson tar 만들기
  make ci-runner-status           7-5  러너 상태와 권한 한눈에
  make ci-sim-image-swap          7-6  CI 밖에서 같은 태그에 다른 이미지를 올린다
  make deploy                     7-6  tools/deploy/deploy.sh (스테이징 칸에 이미지 띄우기)
  make redis-flush                7-7  Redis 캐시 값 비우기(mall:* 키만)
"""
from __future__ import annotations

import os
import subprocess
import sys

from daon import ci
from daon.config import ROOT


def _need(path: str, lesson: str) -> None:
    if not (ROOT / path).exists():
        raise SystemExit(f"{path} 가 아직 없습니다({lesson}에서 만드는 파일입니다). make goto L={lesson} 로 지점을 확인하세요.")


def cmd_ci_lint(a):
    _need("tools/ci/check_pins.py", "7-5")
    sys.exit(subprocess.call([sys.executable, "tools/ci/check_pins.py"], cwd=ROOT))


def cmd_ci_build(a):
    out = ci.build_image("ci-lesson")
    print(f"빌드 결과(이미지 파일): {out.relative_to(ROOT)} ({out.stat().st_size // (1024 * 1024)}MB). 러너가 /ci-in/ 에서 읽습니다.")


def cmd_ci_run(a):
    try:
        if not (ci.IN_DIR / "mall-portal.tar").exists():
            cmd_ci_build(a)
        run = ci.run_pipeline(a.message)
    except ci.CiNotReady as e:
        raise SystemExit(str(e))
    print(f"CI 작업 결과: {run.status} ({run.seconds:.0f}초) 커밋 {run.sha[:12]}")
    for name, st in run.jobs.items():
        print(f"  작업 {name}: {st}")
    show = [l.split("Z ", 1)[-1] for l in run.log.splitlines()
            if any(k in l for k in ("[scan-step]", "SECURITY", "고정 검사", "단기 계정", "올렸습니다", "다이제스트", "서명", "→ 40자", "이(가) ci/pins.lock"))]
    for l in show:
        print("  | " + l[:200])
    if run.status != "success":
        sys.exit(1)


def cmd_ci_sim_tag_move(a):
    try:
        ci.require()
        if a.undo:
            ci.tag_restore()
            print(f"v1 태그를 처음 커밋으로 되돌렸습니다: {ci.tag_target()[:12]}")
        else:
            before = ci.tag_target()
            new = ci.tag_move()
            print(f"로컬 Gitea 안에서만: {ci.SCAN_STEP} 의 v1 태그를 {before[:12]} → {new[:12]} 로 옮겼습니다. 우리 CI 파일은 한 글자도 바뀌지 않았습니다.")
    except ci.CiNotReady as e:
        raise SystemExit(str(e))


def cmd_ci_sim_image_swap(a):
    try:
        ci.require()
        d = ci.image_swap()
    except ci.CiNotReady as e:
        raise SystemExit(str(e))
    print(f"CI 밖에서 {ci.REG_HOST}/daon/{ci.SERVICE}:lesson 태그에 다른 이미지를 올렸습니다 → 지금 다이제스트 {d[:19]}…(라벨 daon.build=unsigned-demo)")


def cmd_ci_runner_status(a):
    ok = False
    try:
        ok = ci.available() and ci._runner_online()
    except ci.CiNotReady:
        pass
    print(f"러너: {'온라인' if ok else '오프라인(또는 A8이 꺼져 있음)'}")
    r = subprocess.run(["docker", "inspect", "daon-runner-1", "--format",
                        "user={{.Config.User}} privileged={{.HostConfig.Privileged}} readonly_rootfs={{.HostConfig.ReadonlyRootfs}} cap_drop={{.HostConfig.CapDrop}} "
                        "mounts={{range .Mounts}}{{.Destination}} {{end}}"], capture_output=True, text=True)
    print("권한: " + (r.stdout.strip() or "컨테이너를 찾지 못했습니다"))


def cmd_deploy(a):
    _need("tools/deploy/deploy.sh", "7-5")
    env = os.environ | {"DAON_ROOT": str(ROOT), "DAON_PY": sys.executable}
    sys.exit(subprocess.call(["sh", "tools/deploy/deploy.sh", a.service], cwd=ROOT, env=env))


def cmd_redis_flush(a):
    """랩 전용: mall: 로 시작하는 캐시 키를 지운다(예전 형식의 값을 비울 때, 7-7). 세션·토큰 폐기 목록 같은 다른 키는 건드리지 않는다."""
    from daon import stack as st
    r = st.redis_cli("--scan", "--pattern", "mall:*")
    keys = [k for k in r.stdout.split() if k]
    n = 0
    for i in range(0, len(keys), 200):
        d = st.redis_cli("DEL", *keys[i:i + 200])
        n += int((d.stdout.strip() or "0").split()[-1] or 0)
    print(f"mall:* 캐시 키 {n}개를 지웠습니다(키 이름만 세었고 값은 읽지 않았습니다)")


def register(add, stack_opt):
    add("ci-lint", cmd_ci_lint)
    add("ci-build", cmd_ci_build)
    add("ci-run", cmd_ci_run, (["--message"], {"default": "ci: 실행"}))
    add("ci-sim-tag-move", cmd_ci_sim_tag_move, (["--undo"], {"action": "store_true"}))
    add("ci-sim-image-swap", cmd_ci_sim_image_swap)
    add("ci-runner-status", cmd_ci_runner_status)
    add("deploy", cmd_deploy, (["service"], {"nargs": "?", "default": "mall-portal"}))
    add("redis-flush", cmd_redis_flush)
    from daon import supply_extra
    supply_extra.register(add, stack_opt)
