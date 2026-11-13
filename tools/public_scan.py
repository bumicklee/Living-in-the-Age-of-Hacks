"""공개 저장소로 내보내기 전 검사(tools/sync_public_repo.sh가 부른다). 값은 찍지 않고 파일·줄·종류만.

찾는 것(하나라도 있으면 종료 코드 1)
  home      : 개인 홈 경로(/Users/<이름>/ · /home/<이름>/ · C:\\Users\\<이름>\\). 컨테이너 안 흔한 경로(/home/node 등)는 허용
  key       : 개인키 머리(-----BEGIN … PRIVATE KEY-----)
  token     : 클라우드·코드 저장소·메신저 토큰 모양(AWS AKIA…, GitHub ghp_…, Slack xox…, Google AIza…, OpenAI sk-…),
              JWT 세 덩어리(eyJ….eyJ….서명) — 문서의 예시 토큰도 실제 서명 모양이면 걸린다
  phone     : 실제처럼 보이는 휴대전화 번호. 한국 01X-XXXX-XXXX(가짜 범위 010-0000-NNNN·가린 값 *는 허용), 중국 휴대전화 11자리(1[3-9]…)
허용은 tools/public-allow.txt(한 줄에 「경로 정규식<TAB>줄 정규식<TAB>이유」)로만, 이유를 적어서 한다.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

RULES = {
    "home": re.compile(r"(?<![A-Za-z0-9_])(?:/Users/(?!Shared/)[A-Za-z0-9._-]+/|/home/(?!node/|runner/|mall/|app/|daon/|user/|ubuntu/|linuxbrew/)[A-Za-z0-9._-]+/|[A-Za-z]:\\\\Users\\\\[A-Za-z0-9._-]+)"),
    "key": re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED |PGP )?PRIVATE KEY(?: BLOCK)?-----"),
    "token": re.compile(r"\b(?:AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,}|xox[baprs]-[A-Za-z0-9-]{10,}|AIza[0-9A-Za-z_-]{35}|sk-[A-Za-z0-9]{32,})\b"
                        r"|eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{20,}"),
    "phone": re.compile(r"(?<![0-9A-Za-z])(?:01[016789][-. ]?\d{3,4}[-. ]?\d{4}|1[3-9]\d{9})(?![0-9A-Za-z])"),
}
FAKE_PHONE = re.compile(r"^010[-. ]?0000[-. ]?\d{4}$")
SKIP_SUFFIX = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".jar", ".tar", ".gz", ".zip", ".woff", ".woff2", ".ttf", ".pdf", ".mp4", ".mp3"}


def allow_rules(root: Path) -> list[tuple[re.Pattern, re.Pattern]]:
    f = root / "tools" / "public-allow.txt"
    out = []
    if f.exists():
        for line in f.read_text("utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                parts = line.split("\t")
                if len(parts) >= 3 and parts[2].strip():
                    out.append((re.compile(parts[0]), re.compile(parts[1])))
    return out


def scan(dest: Path, root: Path) -> list[tuple[str, int, str]]:
    allow = allow_rules(root)
    hits = []
    for f in sorted(p for p in dest.rglob("*") if p.is_file() and ".git" not in p.parts):
        if f.suffix.lower() in SKIP_SUFFIX or f.stat().st_size > 5_000_000:
            continue
        rel = str(f.relative_to(dest))
        try:
            text = f.read_text("utf-8")
        except UnicodeDecodeError:
            continue
        for no, line in enumerate(text.splitlines(), 1):
            for kind, rx in RULES.items():
                for m in rx.finditer(line):
                    if kind == "phone" and (FAKE_PHONE.match(m.group(0)) or "*" in m.group(0)):
                        continue
                    if any(pr.search(rel) and lr.search(line) for pr, lr in allow):
                        continue
                    hits.append((rel, no, kind))
    return hits


def main(argv: list[str]) -> int:
    dest, root = Path(argv[1]).resolve(), Path(argv[2]).resolve()
    hits = scan(dest, root)
    by: dict[str, int] = {}
    for _, _, kind in hits:
        by[kind] = by.get(kind, 0) + 1
    for rel, no, kind in hits[:200]:
        print(f"  [{kind}] {rel}:{no}")
    if len(hits) > 200:
        print(f"  … 그 밖 {len(hits) - 200}곳")
    print(f"검사: 찾은 곳 {len(hits)}개 " + (" · ".join(f"{k} {v}" for k, v in sorted(by.items())) if by else "(없음)"))
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
