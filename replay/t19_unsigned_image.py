"""T19 · 서명 확인 없는 이미지가 배포된다(점검만, 로컬 레지스트리·스테이징 칸). 수업 7-6.

레지스트리 쓰기 권한이 있는 「다른 사람이나 다른 작업」이 같은 태그(daon/mall-portal:lesson)에 다른 이미지를 올렸다고 하고
(make ci-sim-image-swap: 다른 점은 라벨 하나, daon.build=unsigned-demo), 배포 스크립트(tools/deploy/deploy.sh)가 그 이미지를 띄우는지 본다.
레드: 아무 경고 없이 배포된다(종료 코드 0, 스테이징 컨테이너 라벨이 unsigned-demo). 그린: 서명이 없다고 거부(종료 코드 1, 컨테이너 안 뜸).
이미지는 이 PC의 로컬 레지스트리(127.0.0.1)에서만 오가고, 스테이징 컨테이너는 네트워크 없이 sleep만 한다.
"""
from __future__ import annotations

import os
import subprocess
import sys

from daon import ci
from daon.config import ROOT
from replay import NotReady, Result

T = "T19"
TITLE = "서명 확인 없는 이미지가 배포됨"
LESSON = "7-6"
SOURCE = "[회사 설정]"
STACKS = ("spring", "express")
STAGING = "daon-staging-mall-portal"


def deploy(reports_dir: str | None = None) -> subprocess.CompletedProcess:
    env = os.environ | {"DAON_ROOT": str(ROOT), "DAON_PY": sys.executable} | ({"DAON_REPORTS_DIR": reports_dir} if reports_dir else {})
    return subprocess.run(["sh", "tools/deploy/deploy.sh", "mall-portal"], cwd=ROOT, env=env, capture_output=True, text=True, timeout=300)


def staging_label() -> str | None:
    r = subprocess.run(["docker", "inspect", STAGING, "--format", '{{index .Config.Labels "daon.build"}}|{{.Config.Image}}'], capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else None


def remove_staging() -> None:
    subprocess.run(["docker", "rm", "-f", STAGING], capture_output=True)


def run(stack: str = "spring") -> Result:
    if not (ROOT / "tools/deploy/deploy.sh").exists():
        raise NotReady("tools/deploy/deploy.sh가 없습니다. 7-5 완료 지점 이후에서 하세요(make goto L=7-6).")
    try:
        ci.require()
        digest = ci.image_swap()
    except ci.CiNotReady as e:
        raise NotReady(str(e))
    remove_staging()
    r = deploy()
    got = staging_label()
    ev = [f"CI 밖에서 같은 태그에 다른 이미지를 올림 → 다이제스트 {digest[:19]}…",
          f"make deploy 종료 코드 {r.returncode}"] + ["  " + l for l in (r.stdout.strip().splitlines() or [])[-2:]]
    remove_staging()
    if r.returncode == 0 and got and got.startswith("unsigned-demo"):
        return Result(True, "서명 없는 이미지(daon.build=unsigned-demo)가 아무 경고 없이 배포됐다", ev + [f"스테이징 컨테이너 라벨: {got.split('|')[0]}"])
    if r.returncode == 0:
        return Result(True, "배포는 됐지만 확인 없이 태그로 띄웠다", ev + [f"스테이징 컨테이너: {got}"])
    return Result(False, "서명이 없는 이미지는 배포가 거부됐다(스테이징 컨테이너가 뜨지 않음)", ev)
