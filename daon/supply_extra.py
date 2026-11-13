"""7-2 SBOM·KEV 대조, 7-3 원본 보안 패치 추적. 교육용 랩 전용(인터넷 없이 날짜를 박은 사본으로 돈다)."""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import date, datetime
from pathlib import Path

import yaml

from daon.config import DATA, ROOT

sys.path.insert(0, str(ROOT / "tools"))
import secevent  # noqa: E402

SBOM_DIR = ROOT / "build" / "sbom"
MAVEN_MODULES = ("mall-admin", "mall-portal")
SBOM_NAMES = ("mall-admin", "mall-portal", "evershop")
KEV_DIR = ROOT / "tools" / "kev"
KEV_FILES = {"fixture": "kev-lab-fixture.json", "real": "kev-2026-11-06.json"}
INDEX = KEV_DIR / "advisories-2026-11-09.json"
DECISIONS = ROOT / "security" / "vuln-decisions.yaml"
HIGH = {"HIGH", "CRITICAL"}


# ───────────── 7-2 SBOM ─────────────
def pom_has_plugin() -> bool:
    return "cyclonedx-maven-plugin" in (ROOT / "spring/pom.xml").read_text("utf-8")


def cmd_sbom(a):
    if not pom_has_plugin():
        print("[레드] SBOM이 없습니다: spring/pom.xml에 SBOM을 만드는 설정(CycloneDX 플러그인)이 없습니다(7-2에서 넣습니다).")
        sys.exit(1)
    SBOM_DIR.mkdir(parents=True, exist_ok=True)
    print("Maven으로 모듈별 SBOM을 만듭니다(처음에는 내려받기 때문에 몇 분 걸립니다)")
    r = subprocess.run(["docker", "run", "--rm", "-v", f"{ROOT}/spring:/src", "-v", "daon-m2:/root/.m2", "-w", "/src", "maven:3.9.12-eclipse-temurin-17",
                        "mvn", "-B", "-q", "-pl", ",".join(MAVEN_MODULES), "-am", "cyclonedx:makeBom", "-DoutputFormat=json", "-DoutputName=bom", "-Ddocker.skip=true"],
                       capture_output=True, text=True)
    if r.returncode:
        raise SystemExit("SBOM 만들기 실패:\n" + "\n".join(l for l in (r.stdout + r.stderr).splitlines() if "ERROR" in l)[:800])
    for m in MAVEN_MODULES:
        shutil.copy2(ROOT / "spring" / m / "target" / "bom.json", SBOM_DIR / f"{m}.cdx.json")
    r = subprocess.run(["docker", "run", "--rm", "-v", f"{ROOT}/express:/src:ro", "-v", f"{SBOM_DIR}:/out", "-w", "/src", "node:20.19.5-alpine",
                        "npx", "--yes", "@cyclonedx/cyclonedx-npm@4.1.2", "--package-lock-only", "--ignore-npm-errors", "--output-format", "JSON", "--output-file", "/out/evershop.cdx.json"],
                       capture_output=True, text=True)
    if r.returncode:
        print("Express SBOM은 만들지 못했습니다(건너뜀): " + (r.stderr.strip().splitlines() or ["원인 불명"])[-1][:160])
    show_sbom()


def sbom_components(name: str) -> list[dict]:
    p = SBOM_DIR / f"{name}.cdx.json"
    return json.loads(p.read_text("utf-8")).get("components", []) if p.exists() else []


def purl_of(c: dict) -> str:
    return (c.get("purl") or "").split("?")[0]


def show_sbom() -> None:
    for n in SBOM_NAMES:
        comps = sbom_components(n)
        print(f"  build/sbom/{n}.cdx.json: " + (f"구성 요소 {len(comps)}개" if comps else "없음"))


# ───────────── 7-2 KEV 대조 ─────────────
def load_decisions() -> dict[str, dict]:
    if not DECISIONS.exists():
        return {}
    rows = yaml.safe_load(DECISIONS.read_text("utf-8")) or []
    return {r["component"]: r for r in rows if isinstance(r, dict) and r.get("component")}


def decision_state(row: dict | None, today: date) -> str:
    """decided | undecided | overdue"""
    if not row:
        return "undecided"
    d = row.get("decision")
    if d in ("upgraded", "removed"):
        return "decided"
    if d == "exception":
        if not (row.get("reason") and row.get("until") and row.get("owner")):
            return "undecided"
        until = row["until"] if isinstance(row["until"], date) else date.fromisoformat(str(row["until"]))
        return "overdue" if until < today else "decided"
    return "undecided"


def kev_report(kev: str = "fixture", *, emit: bool = True, decisions: dict | None = None) -> dict:
    index = json.loads(INDEX.read_text("utf-8"))
    path = Path(kev) if kev not in KEV_FILES else KEV_DIR / KEV_FILES[kev]
    catalog = json.loads(path.read_text("utf-8"))
    kev_ids = {v["cveID"]: v for v in catalog["vulnerabilities"]}
    fixture = bool(catalog.get("labFixture"))
    decisions = load_decisions() if decisions is None else decisions
    today = secevent.now().date()
    comps: dict[str, str] = {}                       # purl -> scope(가장 넓은 쪽)
    for n in MAVEN_MODULES:
        for c in sbom_components(n):
            p = purl_of(c)
            if p.startswith("pkg:maven/"):
                comps[p] = "required" if c.get("scope", "required") == "required" else comps.get(p, c.get("scope"))
    out = {"sbom": {n: len(sbom_components(n)) for n in SBOM_NAMES}, "kev_source": path.name, "fixture": fixture,
           "kev": [], "runtime": [], "build": [], "npm": index.get("npm_audit_counts", {}), "undecided": 0, "events": []}
    for purl, scope in sorted(comps.items()):
        advs = index["maven"].get(purl, [])
        ids = {a["id"] for a in advs} | {c for a in advs for c in a["cve"]} | set(index.get("fixture_aliases", {}).get(purl, []))
        matched = sorted(ids & set(kev_ids))
        high = [a for a in advs if a["severity"] in HIGH]
        if matched:
            st = decision_state(decisions.get(purl), today)
            out["kev"].append({"purl": purl, "ids": matched, "state": st, "fixture": fixture})
            if emit:
                reason = "KEV 목록에 있는 취약점(" + {"undecided": "결정 없음", "overdue": "예외 기한 지남", "decided": "결정 있음"}[st] + ")"
                out["events"].append(secevent.emit("supply.kev_match", "7-2", "tool:make-kev", purl, reason,
                                                   level="INFO" if st == "decided" else "WARN", echo=False))
        elif advs and scope == "optional":
            out["build"].append({"purl": purl, "n": len(advs)})
        elif high:
            st = decision_state(decisions.get(purl), today)
            out["runtime"].append({"purl": purl, "n": len(high), "worst": "CRITICAL" if any(a["severity"] == "CRITICAL" for a in high) else "HIGH", "state": st})
    # 결정이 필요한 곳: KEV 일치 + 높음 이상 런타임 권고
    out["undecided"] = sum(1 for r in out["kev"] + out["runtime"] if r["state"] != "decided")
    out["total_required"] = len(out["kev"]) + len(out["runtime"])
    return out


def cmd_kev(a):
    if not any((SBOM_DIR / f"{n}.cdx.json").exists() for n in MAVEN_MODULES):
        print("[레드] SBOM이 없습니다. make sbom 으로 먼저 만드세요.")
        sys.exit(1)
    rep = kev_report(a.kev)
    tag = "(교육용 가짜 항목 포함)" if rep["fixture"] else "(실제 CISA 목록 사본)"
    print(f"KEV 대조 · 목록 {rep['kev_source']} {tag} · 권고 색인 {INDEX.name}")
    for n, c in rep["sbom"].items():
        print(f"  SBOM {n}: 구성 요소 {c}개")
    print(f"\n[1] KEV 일치: {len(rep['kev'])}건")
    for r in rep["kev"]:
        print(f"  - {r['purl']}  {', '.join(r['ids'])}{' [가짜 항목]' if r['fixture'] else ''}  → {_ko(r['state'])}")
    print(f"[2] 외부에 열린 런타임 권고(높음 이상): {len(rep['runtime'])}건")
    for r in rep["runtime"]:
        print(f"  - {r['purl']}  {r['worst']} {r['n']}개  → {_ko(r['state'])}")
    print(f"[3] 빌드 도구 권고: {len(rep['build'])}건(Maven) · 화면 도구 npm 참고 {json.dumps(rep['npm'], ensure_ascii=False)}")
    print(f"\n결정 없음: {rep['undecided']}건 (결정이 필요한 {rep['total_required']}건 중)")
    if rep["undecided"]:
        sys.exit(1)


def _ko(st: str) -> str:
    return {"decided": "결정 있음", "undecided": "결정 없음", "overdue": "예외 기한 지남"}[st]


# ───────────── 7-3 원본 보안 패치 ─────────────
UP_REPO = DATA / "upstream.git"
UP_REC = DATA / "upstream.json"
PATCH_OLD, PATCH_NEW = "<commons-lang3.version>3.20.0</commons-lang3.version>", "<commons-lang3.version>3.21.0</commons-lang3.version>"
FIXED_DATE = "2026-11-09T09:00:00+0900"


def _git(cwd, *args, env=None):
    import os
    e = os.environ | {"GIT_AUTHOR_NAME": "mall-upstream", "GIT_AUTHOR_EMAIL": "upstream@example.invalid", "GIT_COMMITTER_NAME": "mall-upstream",
                      "GIT_COMMITTER_EMAIL": "upstream@example.invalid", "GIT_AUTHOR_DATE": FIXED_DATE, "GIT_COMMITTER_DATE": FIXED_DATE} | (env or {})
    return subprocess.run(["git", *args], cwd=cwd, env=e, check=True, capture_output=True, text=True)


def upstream_sim() -> dict:
    """가짜 원본 저장소: 원본 pom.xml이 든 커밋 하나 + 그 위에 「의존성 한 줄 올림」 보안 패치 커밋 하나. 커밋 시각을 고정해 해시가 모든 PC에서 같다."""
    base_pom = (ROOT / "course/base/spring/pom.xml").read_text("utf-8")
    assert PATCH_OLD in base_pom
    if UP_REPO.exists():
        shutil.rmtree(UP_REPO)
    with tempfile.TemporaryDirectory() as d:
        w = Path(d)
        _git(w, "init", "-q", "-b", "main")
        (w / "spring").mkdir()
        (w / "spring/pom.xml").write_text(base_pom, "utf-8")
        _git(w, "add", "-A")
        _git(w, "commit", "-q", "-m", "mall: 원본 pom.xml(교육용 가짜 원본)")
        (w / "spring/pom.xml").write_text(base_pom.replace(PATCH_OLD, PATCH_NEW), "utf-8")
        _git(w, "commit", "-qam", "Security: bump commons-lang3 to 3.21.0\n\n교육용 가짜 원본 패치(의존성 버전 한 줄 올림).")
        sha = _git(w, "rev-parse", "HEAD").stdout.strip()
        UP_REPO.parent.mkdir(exist_ok=True)
        _git(w, "clone", "-q", "--bare", str(w), str(UP_REPO))
    rec = {"repo": "mall", "sha": sha, "published_at": datetime.now().astimezone().isoformat(), "subject": "Security: bump commons-lang3 to 3.21.0"}
    UP_REC.write_text(json.dumps(rec, ensure_ascii=False, indent=1), "utf-8")
    return rec


def upstream_state(now: datetime | None = None, *, emit: bool = True) -> dict:
    if not UP_REC.exists():
        raise SystemExit("가짜 원본이 아직 없습니다. make upstream-sim 을 먼저 실행하세요.")
    rec = json.loads(UP_REC.read_text("utf-8"))
    sha12 = rec["sha"][:12]
    pom = (ROOT / "spring/pom.xml").read_text("utf-8")
    md = ROOT / "spring/UPSTREAM.md"
    row = next((l for l in md.read_text("utf-8").splitlines() if l.startswith("|") and sha12 in l), None) if md.exists() else None
    patched = PATCH_NEW in pom
    out = {"sha12": sha12, "patched": patched, "row": row, "reflected": bool(patched and row)}
    if out["reflected"]:
        cells = [c.strip() for c in row.strip("|").split("|")]
        m = re.search(r"[\d.]+", cells[3]) if len(cells) > 3 else None
        out["hours"] = float(m.group(0)) if m else None
        out["state"] = "reflected"
        return out
    now = now or secevent.now()
    hours = (now - datetime.fromisoformat(rec["published_at"])).total_seconds() / 3600
    out["hours"] = hours
    out["state"] = "overdue" if hours >= 72 else "notice" if hours >= 48 else "pending"
    if emit and out["state"] in ("overdue", "notice"):
        secevent.emit("supply.upstream_patch.overdue", "7-3", "tool:upstream-status", f"upstream:mall@{sha12}",
                      "원본 보안 패치 미반영 72시간 초과" if out["state"] == "overdue" else "원본 보안 패치 미반영 48시간 초과(미리 알림)",
                      level="WARN" if out["state"] == "overdue" else "INFO", echo=False)
    return out


def cmd_upstream_sim(a):
    rec = upstream_sim()
    print(f"가짜 원본을 만들었습니다: {UP_REPO.relative_to(ROOT)} (보안 패치 커밋 {rec['sha'][:12]}: {rec['subject']})")
    print("  이 패치는 spring/pom.xml의 한 줄을 올립니다. 공개 시각 = 지금(0시간).")
    print("  다음: git remote add upstream .daon/upstream.git → git fetch upstream → git cherry-pick <커밋>")


def cmd_upstream_status(a):
    s = upstream_state()
    fake = " (DAON_FAKE_NOW로 시계를 돌린 값)" if __import__("os").environ.get("DAON_FAKE_NOW") else ""
    if s["reflected"]:
        print(f"반영됨, 걸린 시간 {s['hours']:g}시간 (원본 커밋 {s['sha12']}, spring/UPSTREAM.md 기록)")
        return
    label = {"pending": "반영 안 됨", "notice": "반영 안 됨, 48시간 넘음", "overdue": "72시간 초과"}[s["state"]]
    print(f"{label}, 공개 뒤 {s['hours']:.0f}시간{fake} (원본 커밋 {s['sha12']})")
    if s["state"] != "pending":
        print(f"보안 이벤트를 남겼습니다: supply.upstream_patch.overdue → reports/security-events.jsonl")
    sys.exit(1 if s["state"] == "overdue" else 0)


def register(add, stack_opt):
    add("sbom", cmd_sbom)
    add("kev", cmd_kev, (["--kev"], {"default": "fixture", "help": "fixture(기본, 가짜 항목 1건) | real(실제 CISA 사본) | 파일 경로"}))
    add("upstream-sim", cmd_upstream_sim)
    add("upstream-status", cmd_upstream_status)
