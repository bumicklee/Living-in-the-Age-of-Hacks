"""시크릿 점검(gitleaks 감싸기). 6-2 make scan-secrets · T15 재연 · tests/test_s6_02_*.py가 같은 코드를 쓴다.

원칙: **값은 어디에도 찍지 않는다.** gitleaks를 --redact로 돌리고, 결과에서 파일 이름·줄 번호·규칙 이름·건수만 꺼낸다.
점검 대상은 이 랩 저장소(작업 트리·git 이력)와 이 PC의 도커 이미지 속 설정 파일뿐이다(읽기 전용).
"""
from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
import zipfile
from collections import Counter
from pathlib import Path

from daon.config import ROOT

TREE_ROOTS = ["spring"]                      # 작업 트리 점검 범위: 앱 설정·문서(컴포즈 저장소 자격증명은 6-4 몫)
CONFIG_LAB = ROOT / ".gitleaks.toml"         # 수업에서 만드는 규칙(없으면 기준 규칙)
CONFIG_REF = ROOT / "tools" / "gitleaks-reference.toml"


class ScanUnavailable(Exception):
    pass


def config(use: str = "auto") -> Path:
    """auto: .gitleaks.toml이 있으면 그것, 없으면 기준 규칙. default: 기본 규칙만(None 반환 대신 빈 규칙 파일)."""
    if use == "default":
        p = Path(tempfile.gettempdir()) / "daon-gitleaks-default.toml"
        p.write_text('title = "gitleaks 기본 규칙만"\n[extend]\nuseDefault = true\n', "utf-8")
        return p
    if use == "reference":
        return CONFIG_REF
    return CONFIG_LAB if CONFIG_LAB.exists() else CONFIG_REF


def _run(args: list[str]) -> list[dict]:
    if not shutil.which("gitleaks"):
        raise ScanUnavailable("gitleaks가 없습니다(brew install gitleaks).")
    with tempfile.TemporaryDirectory() as d:
        out = Path(d) / "r.json"
        r = subprocess.run(["gitleaks", *args, "--no-banner", "--redact", "--exit-code", "0", "-f", "json", "-r", str(out)],
                           cwd=ROOT, capture_output=True, text=True)
        if r.returncode != 0:
            raise ScanUnavailable("gitleaks 실행 실패(종료 코드 %d)" % r.returncode)
        found = json.loads(out.read_text("utf-8") or "[]")
    # 값은 버린다: 남기는 것은 규칙·파일·줄·(이력이면) 커밋 앞 8자리
    return [{"rule": f["RuleID"], "file": f["File"], "line": f["StartLine"], "commit": (f.get("Commit") or "")[:8]} for f in found]


def scan_tree(use: str = "auto", roots: list[str] | None = None) -> list[dict]:
    out: list[dict] = []
    ignore = ROOT / ".gitleaksignore"
    for root in roots or TREE_ROOTS:
        args = ["dir", str(ROOT / root), "-c", str(config(use))]
        if ignore.exists():
            args += ["--gitleaks-ignore-path", str(ignore)]
        for f in _run(args):
            f["file"] = str(Path(f["file"]).resolve().relative_to(ROOT)) if Path(f["file"]).is_absolute() else f["file"]
            out.append(f)
    return out


def scan_history(use: str = "auto") -> list[dict]:
    """git 이력 전체(지운 파일의 옛 커밋 포함). 이력은 다시 쓰지 않으므로 0이 되지 않는다. 교체 대상 목록의 재료다."""
    args = ["git", str(ROOT), "-c", str(config(use))]
    ignore = ROOT / ".gitleaksignore"
    if ignore.exists():
        args += ["--gitleaks-ignore-path", str(ignore)]
    return _run(args)


def scan_image(image: str, use: str = "auto") -> list[dict]:
    """도커 이미지 속 앱 jar를 풀어 설정 파일을 점검한다(이미지 레이어에 구워진 설정 파일)."""
    name = image if ":" in image else f"daon/{image}:dev"
    if not shutil.which("docker"):
        raise ScanUnavailable("docker가 없습니다.")
    with tempfile.TemporaryDirectory() as d:
        jar = Path(d) / "app.jar"
        cid = subprocess.run(["docker", "create", name], capture_output=True, text=True)
        if cid.returncode != 0:
            raise ScanUnavailable(f"이미지 {name}가 없습니다(make up으로 빌드하세요).")
        try:
            subprocess.run(["docker", "cp", f"{cid.stdout.strip()}:/app/app.jar", str(jar)], capture_output=True, check=True)
        finally:
            subprocess.run(["docker", "rm", "-f", cid.stdout.strip()], capture_output=True)
        dest = Path(d) / "layer"
        with zipfile.ZipFile(jar) as z:                          # 설정 파일만 꺼낸다(.yml·.properties, 앱 jar 안과 BOOT-INF/lib 안의 설정 제외)
            for n in z.namelist():
                if n.startswith("BOOT-INF/classes/") and n.endswith((".yml", ".yaml", ".properties")):
                    z.extract(n, dest)
        args = ["dir", str(dest), "-c", str(config(use))]
        return _run(args)


def summarize(found: list[dict]) -> list[str]:
    """규칙별·파일별 건수(값 없음)."""
    by_rule = Counter(f["rule"] for f in found)
    by_file = Counter(Path(f["file"]).name for f in found)
    lines = [f"  규칙 {r}: {n}건" for r, n in sorted(by_rule.items())]
    lines += [f"  파일 {name}: {n}건" for name, n in sorted(by_file.items())]
    return lines
