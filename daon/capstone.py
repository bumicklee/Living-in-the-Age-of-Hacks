"""15장 캡스톤: 다온마켓 연쇄 사고(가상 시나리오). make capstone PART=1 · PART=status · PART=answer · RESET=1

새 공격 코드는 없다. 앞 장의 재연·랩 도구가 이미 쓰는 요청을 순서대로 묶었을 뿐이다(127.0.0.1의 랩, 가짜 계정·가짜 값, 요청 수 고정).
  1) 시크릿 유출   : 개발자가 시험용 스크립트 설정 파일(yml, 직원 비밀번호 평문)을 로컬 Gitea(A8)에 올림 → CI 시크릿 스캔(6-2 규칙) 도구 이벤트
  2) 새 곳 로그인  : 그 비밀번호로 관리자 로그인(정상 자격증명이라 막히지 않음). 원본 로그인 기록(ums_admin_login_log)에만 남는다
  3) 대량 조회     : 주문 목록 100건 × 3쪽(9-3 상한 안, 5-4 마스킹) → 감사 로그·data.read.bulk·8-4 경보 메일(T20과 같은 길)
  4) 배송지 바꾸기 : 정상 기능으로 주문 20건의 받는 사람 칸을 한 주소로(15장 시작 모습: 감사 로그 change.order.receiver)
  5) 고액 환불 시도: 남은 결제 금액 초과(approval.denied.over_remaining) · 고액(두 번째 승인 대기). T34와 같은 길
  6) 권한 올리기   : 권한 상승 없이 관리자 역할 변경 · 닫힌 공용 계정 daon_ops 로그인. T35와 같은 길
  7) 사고 뒤 정상 일: 슈퍼 관리자가 고객 전화를 받고 바뀐 주문 하나의 주소를 다시 고침(골라 복구가 덮어쓰면 안 되는 행)
모든 요청에 X-Trace-Id: <접두>-<단계>-<순번>(cap-3-001 …). 정답 표는 .daon/capstone/<시나리오>/answer.json(수강생 화면에 찍지 않는다).

시나리오 둘: MAIN(수강생, 직원 staff_night, 사고 번호 capstone) · CHECK(make check가 따로 쓰는 직원 staff_drill, 주문 20건도 다르다).
CHECK는 수강생의 사고 상태를 건드리지 않는다.
"""
from __future__ import annotations

import base64
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx

from daon import stack as st
from daon.config import DATA, ROOT, URLS
from replay import NotReady
from replay import _approval as ap
from replay._http import ADMINS
from replay._money import clear_counters, mysql, one

ATTACK_UA = "Mozilla/5.0 (X11; Linux x86_64) Firefox/131.0 capstone-attacker"   # 랩 표시가 붙은 가짜 브라우저 이름
SEED_PASSWORD = "daon-pass-1!"          # 가짜 시드 직원 공통 비밀번호(seed/spring/20-daon-accounts.sql). 새어 나간 값도 이것
OFFICE_IP = "10.20.0.15"                # 「평소」 로그인 기록의 가짜 사무실 주소
PAGE_SIZE, PAGES, CHANGES = 100, 3, 20
OVER_ASK, HIGH_ASK = 900000, 400000     # 가짜 고액 주문(9890, 결제 450,000원)에 대한 환불 요청 금액
FAKE_RECEIVER = {"receiverName": "박가짜", "receiverPhone": "010-0000-0999", "receiverPostCode": "00000", "receiverProvince": "서울특별시",
                 "receiverCity": "서울특별시", "receiverRegion": "가짜구", "receiverDetailAddress": "가짜로 99 (랩 시나리오 수령지)"}
LEGIT_RECEIVER = {"receiverName": "정가짜", "receiverPhone": "010-0000-0415", "receiverPostCode": "00001", "receiverProvince": "경기도",
                  "receiverCity": "가짜시", "receiverRegion": "가짜동", "receiverDetailAddress": "고객이 전화로 알려 준 새 주소(가짜)"}
SKIP_MEMBERS = (101, 102, 117, 118, 119, 133)    # alice·bob·14-4 모의 침해·격리 시험 고객의 주문은 쓰지 않는다
REPO = "ops-scripts"
LEAK_PATH = "scripts/order-check/config.yml"


@dataclass(frozen=True)
class Scenario:
    name: str
    actor: str           # 침입에 쓰이는 직원 아이디(가짜)
    admin_id: int
    nick: str
    incident: str        # 사고 번호(.daon/incident/<번호>/ → 증거 없이 make wipe 금지)
    prefix: str          # X-Trace-Id 접두
    offset: int          # 주문 고르기 시작 위치(MAIN 0, CHECK 20)
    author: str          # 유출 커밋을 만든 가짜 개발자

    @property
    def dir(self) -> Path:
        return DATA / "capstone" / self.name

    @property
    def answer_path(self) -> Path:
        return self.dir / "answer.json"


MAIN = Scenario("main", "staff_night", 160, "야간 주문 담당", "capstone", "cap", 0, "dev-park")
CHECK = Scenario("check", "staff_drill", 161, "점검용 주문 담당", "CAP-CHECK", "capchk", CHANGES, "dev-check")


def _trace(sc: Scenario, step: int, n: int = 1) -> str:
    return f"{sc.prefix}-{step}-{n:03d}"


def _utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _admin_base() -> str:
    return URLS["spring"]["admin"]


def _client() -> httpx.Client:
    return httpx.Client(base_url=_admin_base(), timeout=20.0)


def load(sc: Scenario = MAIN) -> dict | None:
    try:
        return json.loads(sc.answer_path.read_text("utf-8"))
    except (FileNotFoundError, ValueError):
        return None


def _save(sc: Scenario, name: str, data: dict) -> None:
    sc.dir.mkdir(parents=True, exist_ok=True)
    p = sc.dir / name
    p.write_text(json.dumps(data, ensure_ascii=False, indent=2), "utf-8")
    p.chmod(0o600)


def _hex_rows(sql: str) -> list[dict]:
    return [json.loads(bytes.fromhex(x).decode("utf-8")) for x in mysql(sql).split()]


# ── 준비(랩 도구: 컨테이너 안 mysql·redis-cli) ──
def actor_exists(sc: Scenario) -> bool:
    return one(f"SELECT COUNT(*) FROM ums_admin WHERE username='{sc.actor}'") == "1"


def ensure_actor(sc: Scenario) -> None:
    """가짜 직원 하나(주문 관리자 역할 2, 비밀번호는 시드 공통 가짜 값 = staff_order와 같은 해시)와 「평소」 로그인 기록 7줄"""
    mysql(f"INSERT INTO ums_admin (id, username, password, nick_name, email, note, create_time, status) "
          f"SELECT {sc.admin_id}, '{sc.actor}', password, '{sc.nick}', '{sc.actor}@daon.example', '15장 캡스톤용 가짜 직원', NOW(), 1 "
          f"FROM ums_admin WHERE username='staff_order' AND NOT EXISTS (SELECT 1 FROM ums_admin WHERE username='{sc.actor}')")
    mysql(f"UPDATE ums_admin a JOIN ums_admin s ON s.username='staff_order' SET a.password=s.password, a.status=1 WHERE a.username='{sc.actor}'")
    mysql(f"INSERT INTO ums_admin_role_relation (admin_id, role_id) SELECT {sc.admin_id}, 2 FROM DUAL "
          f"WHERE NOT EXISTS (SELECT 1 FROM ums_admin_role_relation WHERE admin_id={sc.admin_id})")
    if int(one(f"SELECT COUNT(*) FROM ums_admin_login_log WHERE admin_id={sc.admin_id} AND ip='{OFFICE_IP}'") or 0) < 7:
        mysql(f"DELETE FROM ums_admin_login_log WHERE admin_id={sc.admin_id}")
        for d in range(1, 8):
            mysql(f"INSERT INTO ums_admin_login_log (admin_id, create_time, ip, address, user_agent) VALUES "
                  f"({sc.admin_id}, DATE_SUB(DATE(NOW()), INTERVAL {d} DAY) + INTERVAL 21 HOUR + INTERVAL {d * 3} MINUTE, '{OFFICE_IP}', '사무실(가짜)', 'Chrome(가짜)')")
    st.redis_cli("DEL", f"mall:ums:admin:{sc.actor}", f"deny:sub:daon-admin:{sc.actor}")
    clear_counters()


def victims(sc: Scenario) -> list[int]:
    rows = mysql(f"SELECT id FROM oms_order WHERE id BETWEEN 9201 AND 9300 AND member_id NOT IN ({','.join(map(str, SKIP_MEMBERS))}) "
                 f"AND status IN (1, 2, 3) AND delete_status = 0 ORDER BY id LIMIT {sc.offset}, {CHANGES}")
    ids = [int(x) for x in rows.split()]
    if len(ids) < CHANGES:
        raise NotReady("시나리오에 쓸 가짜 주문이 부족합니다(make wipe 뒤 make up)")
    return ids


def receivers(ids: list[int]) -> dict[int, dict]:
    from daon import restore
    return restore.live(ids)


def others_fingerprint(skip: list[int]) -> str:
    """시나리오 밖 모든 주문의 받는 사람 칸 지문(골라 복구가 다른 주문을 건드리지 않았는지)"""
    from daon.restore import COLUMNS
    expr = "CONCAT_WS('|', id, " + ", ".join(f"IFNULL({c}, '')" for c in COLUMNS) + ")"
    return one(f"SELECT CONCAT(COUNT(*), ':', SUM(CONV(SUBSTR(SHA2({expr}, 256), 1, 15), 16, 10))) FROM oms_order "
               f"WHERE id NOT IN ({','.join(map(str, skip))})")


# ── 1단계: 시크릿 유출(로컬 Gitea, 없으면 PC 안 git 저장소) ──
def _leak_text(sc: Scenario) -> str:
    return ("# 주문 점검 스크립트 설정(개발자 PC에서 돌리던 것). 랩 시나리오: 가짜 값\n"
            "admin:\n  url: http://127.0.0.1:8080\n"
            f"  username: {sc.actor}\n  password: {SEED_PASSWORD}\n  page-size: 100\n")


def leak(sc: Scenario, echo=print) -> dict:
    from daon import ci
    text = _leak_text(sc)
    if ci.available():
        ci.api("DELETE", f"/repos/daon/{REPO}")
        r = ci.api("POST", "/orgs/daon/repos", json={"name": REPO, "private": True, "auto_init": True, "default_branch": "main", "readme": "Default"})
        if r.status_code not in (201, 409):
            raise NotReady(f"Gitea 저장소를 만들지 못했습니다(HTTP {r.status_code})")
        who = {"name": sc.author, "email": f"{sc.author}@daon.example"}
        put = ci.api("POST", f"/repos/daon/{REPO}/contents/{LEAK_PATH}",
                     json={"content": base64.b64encode(text.encode()).decode(), "message": "주문 점검 스크립트 설정 추가",
                           "author": who, "committer": who, "branch": "main"})
        if put.status_code not in (200, 201):
            raise NotReady(f"Gitea에 올리지 못했습니다(HTTP {put.status_code})")
        commit = put.json().get("commit") or {}
        sha, at = commit.get("sha", ""), (commit.get("committer") or commit.get("author") or {}).get("date") or _utc_now()
        via = "gitea"
        repo_dir = _clone_gitea()
    else:
        repo_dir = Path(tempfile.mkdtemp(prefix="daon-ops-scripts-"))
        env = os.environ | {"GIT_AUTHOR_NAME": sc.author, "GIT_AUTHOR_EMAIL": f"{sc.author}@daon.example",
                            "GIT_COMMITTER_NAME": sc.author, "GIT_COMMITTER_EMAIL": f"{sc.author}@daon.example"}
        run = lambda *a: subprocess.run(list(a), cwd=repo_dir, env=env, check=True, capture_output=True, text=True)
        run("git", "init", "-q", "-b", "main")
        (repo_dir / LEAK_PATH).parent.mkdir(parents=True, exist_ok=True)
        (repo_dir / LEAK_PATH).write_text(text, "utf-8")
        run("git", "add", "-A")
        run("git", "commit", "-q", "-m", "주문 점검 스크립트 설정 추가")
        sha = run("git", "rev-parse", "HEAD").stdout.strip()
        at = run("git", "log", "-1", "--format=%cI").stdout.strip()
        via = "local-git"
    try:
        rule, how = _scan(repo_dir)
    finally:
        shutil.rmtree(repo_dir, ignore_errors=True)
    sys.path.insert(0, str(ROOT / "tools"))
    import secevent
    if rule:
        secevent.emit("secret.found.repository", "15-1", f"git:{sc.author}", f"repo:daon/{REPO}@{sha[:12]}#{LEAK_PATH}",
                      f"CI 시크릿 스캔({how}): 설정 파일에 평문 자격증명(규칙 {rule})", level="WARN", echo=False)
    echo(f"1) 유출: {via} daon/{REPO} 커밋 {sha[:12]} · 스캔 {how} → {'경고 남김' if rule else '못 찾음'}")
    return {"via": via, "repo": f"daon/{REPO}", "file": LEAK_PATH, "commit": sha[:12], "at": at, "author": f"git:{sc.author}",
            "scan_rule": rule, "scan": how}


def _clone_gitea() -> Path:
    from daon import ci
    d = Path(tempfile.mkdtemp(prefix="daon-ops-scripts-"))
    auth = base64.b64encode(f"{ci.ADMIN}:{ci._pw()}".encode()).decode()
    r = subprocess.run(["git", "-c", f"http.extraHeader=Authorization: Basic {auth}", "clone", "-q", f"{ci.CI_URL}/daon/{REPO}.git", str(d / "r")],
                       capture_output=True, text=True)
    if r.returncode:
        raise NotReady("유출 저장소를 내려받지 못했습니다(Gitea)")
    return d / "r"


def _scan(repo: Path) -> tuple[str | None, str]:
    """6-2 규칙(.gitleaks.toml)으로 저장소 이력을 훑는다. gitleaks가 없으면 같은 규칙의 정규식 하나로(간이)"""
    from daon import secretscan as ss
    try:
        found = ss._run(["git", str(repo), "-c", str(ss.config())])
        return (found[0]["rule"] if found else None), "gitleaks"
    except ss.ScanUnavailable:
        pat = re.compile(r"(?i)(?:password|passwd|secret)[\"']?\s*[:=]\s*[\"']?([^\s\"'#$\{][^\s\"'#]*)")
        hit = any(pat.search(f.read_text("utf-8", "replace")) for f in repo.rglob("*.yml"))
        return ("daon-config-plain-credential" if hit else None), "간이 규칙(gitleaks 없음)"


# ── 2~7단계 ──
def _login(c: httpx.Client, user: str, password: str, trace: str, ua: str = ATTACK_UA) -> str | None:
    r = c.post("/admin/login", json={"username": user, "password": password}, headers={"X-Trace-Id": trace, "User-Agent": ua})
    try:
        body = r.json()
    except ValueError:
        return None
    return body["data"]["tokenHead"] + body["data"]["token"] if body.get("code") == 200 else None


def run(sc: Scenario = MAIN, echo=print, fresh: bool | None = None) -> dict:
    """시나리오 한 번. 이미 일어난 사고(answer.json)가 있으면 「다시 돌리기」: 준비 없이 1·2단계만 해 보고 막히면 멈춘다"""
    ap.require_a10()
    from daon import backup as bk
    if fresh is None:
        fresh = load(sc) is None or not actor_exists(sc)
    if not fresh:
        return rerun(sc, echo)
    ensure_actor(sc)
    ap.reset()
    ids = victims(sc)
    pre = receivers(ids)
    _save(sc, "pre.json", {"receivers": {str(k): v for k, v in pre.items()}, "others": others_fingerprint(ids)})
    echo(f"0) 준비: 가짜 직원 {sc.actor} · 사고 직전 백업")
    bk.backup_now()
    time.sleep(2)
    started = _utc_now()
    (DATA / "incident" / sc.incident).mkdir(parents=True, exist_ok=True)     # 사고 기록 폴더: 증거를 모으기 전에는 make wipe 금지(14-4)
    ans: dict = {"scenario": sc.name, "incident": sc.incident, "started": started, "actor": f"admin:{sc.actor}", "victims": ids}
    ans["leak"] = leak(sc, echo)
    time.sleep(1)
    with _client() as c:
        token = _login(c, sc.actor, SEED_PASSWORD, _trace(sc, 2))
        if not token:
            raise NotReady(f"{sc.actor} 로그인이 되지 않습니다(직원 시드·13-6 확인)")
        ip = one(f"SELECT ip FROM ums_admin_login_log WHERE admin_id={sc.admin_id} ORDER BY id DESC LIMIT 1")
        ans["login"] = {"at": _utc_now(), "trace": _trace(sc, 2), "ip_seen": ip, "usual_ip": OFFICE_IP}
        _save(sc, "attack-token.json", {"token": token, "at": _utc_now()})     # 15-2 확인용(사고 전에 받은 토큰이 죽었나). 가짜 직원의 랩 토큰
        echo("2) 로그인")
        h = {"Authorization": token, "User-Agent": ATTACK_UA}
        rows, traces = 0, []
        for n in range(1, PAGES + 1):
            t = _trace(sc, 3, n)
            r = c.get("/order/list", params={"pageNum": n, "pageSize": PAGE_SIZE}, headers=h | {"X-Trace-Id": t})
            data = ((r.json() or {}).get("data") or {}) if r.status_code == 200 else {}
            rows += len(data.get("list") or [])
            traces.append(t)
        ans["bulk"] = {"requests": PAGES, "rows": rows, "page_size": PAGE_SIZE, "traces": traces}
        echo(f"3) 주문 목록 {PAGES}쪽")
        changed = []
        for n, oid in enumerate(ids, 1):
            status = one(f"SELECT status FROM oms_order WHERE id={oid}")
            r = c.post("/order/update/receiverInfo", headers=h | {"X-Trace-Id": _trace(sc, 4, n)},
                       json={"orderId": oid, "status": int(status or 1), **FAKE_RECEIVER})
            if r.status_code == 200 and (r.json() or {}).get("code") == 200:
                changed.append(oid)
        ans["changed"] = changed
        echo(f"4) 받는 사람 바꾸기 {len(changed)}건")
        ap.ensure_high_order()
        refund = {}
        for n, ask in ((1, OVER_ASK), (2, HIGH_ASK)):
            rid = ap.new_return(ap.HIGH_ORDER)
            t = _trace(sc, 5, n)
            r = c.post(f"/returnApply/update/status/{rid}", headers=h | {"X-Trace-Id": t},
                       json={"id": rid, "status": 1, "returnAmount": ask, "handleMan": sc.actor, "handleNote": ap.MARK, "companyAddressId": 1})
            time.sleep(1)
            a = ap.latest_approval(rid) or {}
            refund[t] = {"return": rid, "ask": ask, "http": r.status_code, "approval": a.get("status", "없음"),
                         "paid": len(ap.refunds(rid))}
        ans["refund"] = refund
        echo("5) 환불 요청 2건")
        r = c.post("/admin/role/update", headers=h | {"X-Trace-Id": _trace(sc, 6, 1)}, params={"adminId": 102, "roleIds": "2"})
        shared = _login(c, "daon_ops", ADMINS["daon_ops"]["password"], _trace(sc, 6, 2))
        ans["privilege"] = {"role_update_http": r.status_code, "role_update_code": _code(r), "daon_ops_login": bool(shared)}
        echo("6) 권한 올리기 시도")
        legit = ids[3]
        tok = _login(c, "admin", ADMINS["admin"]["password"], _trace(sc, 7, 0), ua="Mozilla/5.0 (Windows NT 10.0) Chrome/130.0")
        status = one(f"SELECT status FROM oms_order WHERE id={legit}")
        r = c.post("/order/update/receiverInfo", headers={"Authorization": tok or "", "X-Trace-Id": _trace(sc, 7, 1),
                                                          "X-Access-Reason": "%EA%B3%A0%EA%B0%9D%20%EC%A0%84%ED%99%94%20CS-0415"},
                   json={"orderId": legit, "status": int(status or 1), **LEGIT_RECEIVER})
        ans["legit_after"] = {"order": legit, "actor": "admin:admin", "ok": r.status_code == 200 and _code(r) == 200}
        echo("7) 사고 뒤 정상 일(고객 전화로 주소 한 건 수정)")
    clear_counters()
    ans["traces3"] = [_trace(sc, 3, 1), _trace(sc, 4, 1), _trace(sc, 5, 1)]
    ans["table"] = _table(ans)
    ans["finished"] = _utc_now()
    _save(sc, "answer.json", ans)
    echo(f"재생 끝({sc.name}). 수강생에게 주는 정보는 경보 메일 한 통뿐입니다: Mailpit에서 [다온 경보] 메일을 찾으세요")
    return ans


def _code(r: httpx.Response):
    try:
        return (r.json() or {}).get("code")
    except ValueError:
        return None


def _table(ans: dict) -> list[dict]:
    rf = list(ans["refund"].values())
    over_blocked = rf[0]["approval"] == "DENIED" and rf[0]["paid"] == 0
    high_blocked = rf[1]["approval"] in ("PENDING_SECOND", "DENIED") and rf[1]["paid"] == 0
    priv_blocked = ans["privilege"]["role_update_http"] in (401, 403) or ans["privilege"]["role_update_code"] in (401, 403)
    return [
        {"단계": "1 시크릿 유출", "결과": "탐지만" if ans["leak"]["scan_rule"] else "놓침", "근거": "CI 시크릿 스캔 도구 이벤트 secret.found.repository"},
        {"단계": "2 새 곳 로그인", "결과": "탐지만", "근거": "로그인 기록(평소 IP와 다름)만 있고 경보는 없음"},
        {"단계": "3 대량 조회", "결과": "탐지만", "근거": "감사 로그 order.list count · data.read.bulk · 8-4 경보 메일"},
        {"단계": "4 배송지 바꾸기", "결과": "놓침", "근거": "감사 로그 change.order.receiver에만 남음(경보 규칙 없음 → 15-2)"},
        {"단계": "5 남은 금액 초과 환불", "결과": "막힘" if over_blocked else "놓침", "근거": "approval.denied.over_remaining, 결제사 환불 0건"},
        {"단계": "5 고액 환불", "결과": "막힘" if high_blocked else "놓침", "근거": "두 번째 승인 대기(approval.pending.second_approver), 결제사 환불 0건"},
        {"단계": "6 권한 상승 없는 고위험 기능", "결과": "막힘" if priv_blocked else "놓침", "근거": "403(ops.denied.no_elevation 또는 access.denied.no_permission)"},
        {"단계": "6 닫힌 공용 계정", "결과": "놓침" if ans["privilege"]["daon_ops_login"] else "막힘", "근거": "auth.denied.disabled_account"},
    ]


def rerun(sc: Scenario, echo=print) -> dict:
    """대응을 끝낸 뒤 다시 돌리기(15-2 ⑤): 같은 유출 비밀번호로 2단계 로그인부터 막혀야 한다"""
    out = {"leak": leak(sc, echo)}
    with _client() as c:
        token = _login(c, sc.actor, SEED_PASSWORD, _trace(sc, 2, 900))
    out["stopped_at"] = 2 if not token else None
    out["at"] = _utc_now()
    _save(sc, "rerun.json", out)
    echo("다시 돌리기: " + ("2단계(로그인)에서 멈춤 — 새어 나간 비밀번호가 더는 통하지 않는다" if not token
                       else "2단계를 지나감 — 아직 새어 나간 비밀번호로 로그인된다(15-2 대응 전)"))
    return out


# ── 15-2: 대응(정답 순서, make check가 CHECK 시나리오로 돌린다) ──
def respond(sc: Scenario = CHECK, echo=print) -> dict:
    """격리 → 증거 → 비밀번호 초기화 → 서명키 회전 → (토큰 폐기) → 골라 복구. 정책·경보 규칙은 파일(15-2 완료 지점)"""
    from daon import incident as inc_tools
    from replay import _incident
    ans = load(sc)
    if not ans:
        raise NotReady("먼저 시나리오를 돌리세요(make capstone PART=1)")
    _incident.elevate("admin")
    out: dict = {}
    r = incident_call("lock-actor", {"admin": sc.actor}, sc)
    assert r.status_code == 200, f"① 격리: 직원 계정 lock-actor → 200 기대. 실제 HTTP {r.status_code} {r.text[:160]}"
    out["lock"] = r.json()["data"]
    out["evidence"] = inc_tools.collect(sc.incident, echo=lambda *_: None)
    r = incident_call("reset-password", {"admin": sc.actor}, sc)
    assert r.status_code == 200, f"③ 비밀번호 초기화 → 200 기대. 실제 HTTP {r.status_code} {r.text[:160]}"
    _save(sc, f"{sc.actor}.password.json", {"temporary": r.json()["data"]["temporaryPassword"]})
    rotate_key("rotate")
    rotate_key("retire")
    out["key"] = "rotated+retired"
    revoke_tokens(sc.actor)
    legit = ans["legit_after"]["order"]
    confirm = [o for o in ans["changed"] if o != legit]
    out["restore"] = restore_columns(sc, confirm, echo=echo)
    _save(sc, "response.json", out | {"evidence": {k: out["evidence"][k] for k in ("files", "uploaded")}})
    return out


def incident_call(path: str, body: dict, sc: Scenario, user: str = "admin") -> httpx.Response:
    with _client() as c:
        return c.post(f"/daon/incident/{path}", headers={"Authorization": ap.must_login(user), "X-Trace-Id": _trace(sc, 9, 1)},
                      json=body | {"incidentId": sc.incident, "reason": "캡스톤 대응(가짜)"})


def rotate_key(action: str) -> None:
    """5-2 서명키 회전 도구(make rotate-key)와 같은 일. 누구 토큰이 새었는지 모를 때는 키를 바꾼다"""
    import argparse

    from daon import cli
    cli.cmd_rotate_key(argparse.Namespace(key="jwt", action=action))


def revoke_tokens(user: str, audience: str = "daon-admin") -> None:
    """랩 도구: 4-8 폐기 목록의 계정 단위(deny:sub:<aud>:<아이디> = 지금 시각). 회전을 하지 않는 경우의 절차"""
    st.redis_cli("SET", f"deny:sub:{audience}:{user}", str(int(time.time())), "EX", str(14 * 24 * 3600))


def restore_columns(sc: Scenario, confirm: list[int], echo=print) -> dict:
    """골라 복구: 사고 직전 백업에서 확인한 주문의 받는 사람 칸만 되돌린다(관리자 API, 감사 로그에 남음)"""
    from daon import restore
    ans = load(sc) or {}
    b = restore.backup_before(ans["started"])
    old = restore.from_backup(b["key"], confirm, echo=echo)
    rows = [{"orderId": oid, "values": old[oid]} for oid in confirm if oid in old]
    r = incident_call("restore-columns", {"rows": rows}, sc)
    if r.status_code == 403:                                          # 권한 상승이 토큰과 함께 끝났으면(서명키 회전 뒤) 다시 켠다
        from replay import _incident
        _incident.elevate("admin")
        r = incident_call("restore-columns", {"rows": rows}, sc)
    assert r.status_code == 200, f"⑥ 골라 복구 API → 200 기대. 실제 HTTP {r.status_code} {r.text[:160]}"
    return {"backup": b["key"], "restored": r.json()["data"]["count"]}


# ── 상태판(수강생이 손으로 한 대응을 본다, 15-2 끝 기준 ①~⑤) ──
def status(sc: Scenario = MAIN) -> list[tuple[str, bool, str]]:
    ans = load(sc)
    if not ans:
        raise NotReady("아직 사고가 없습니다: make capstone PART=1")
    rows: list[tuple[str, bool, str]] = []
    with _client() as c:
        leaked = _login(c, sc.actor, SEED_PASSWORD, _trace(sc, 8, 1), ua="daon-capstone-status")
        rows.append(("① 새어 나간 비밀번호로 로그인 불가", not leaked, "로그인 됨" if leaked else "거부"))
        try:
            old = json.loads((sc.dir / "attack-token.json").read_text("utf-8"))["token"]
            code = c.get("/admin/info", headers={"Authorization": old}).status_code
            rows.append(("① 사고 때 받은 토큰 401", code == 401, f"HTTP {code}"))
        except FileNotFoundError:
            rows.append(("① 사고 때 받은 토큰 401", False, "토큰 기록 없음"))
    locked = one(f"SELECT status FROM ums_admin WHERE username='{sc.actor}'") == "0"
    rows.append(("격리(직원 잠금)", locked, "잠금" if locked else "열림"))
    rows.append(("② 증거 보존(복구 전)", (DATA / "incident" / sc.incident / "evidence.sha256").exists(), f".daon/incident/{sc.incident}/evidence.sha256"))
    pre = json.loads((sc.dir / "pre.json").read_text("utf-8"))
    now = receivers(ans["victims"])
    legit = ans["legit_after"]["order"]
    back = [o for o in ans["changed"] if o != legit and now.get(o) == pre["receivers"].get(str(o))]
    rows.append((f"③ 주문 {len(ans['changed']) - 1}건 받는 사람 복구", len(back) == len(ans["changed"]) - 1, f"{len(back)}건 복구"))
    rows.append(("③ 사고 뒤 정상 변경 1건은 그대로", now.get(legit) != pre["receivers"].get(str(legit)) and now.get(legit, {}).get("receiver_name") is not None,
                 f"주문 {legit}"))
    rows.append(("③ 다른 주문은 안 바뀜", others_fingerprint(ans["victims"]) == pre["others"], "지문 비교"))
    pol = (ROOT / "infra/approval/policy.yml").read_text("utf-8")
    rows.append(("④ 승인 정책 강화(받는 사람 변경 뒤 두 사람)", "dual_after_receiver_change_hours" in pol, "infra/approval/policy.yml"))
    rows.append(("④ 배송지 변경 경보 규칙", (ROOT / "infra/observability/rules/change.yaml").exists(), "infra/observability/rules/change.yaml"))
    rr = None
    try:
        rr = json.loads((sc.dir / "rerun.json").read_text("utf-8"))
    except FileNotFoundError:
        pass
    rows.append(("⑤ 다시 돌리면 2단계에서 멈춤", bool(rr and rr.get("stopped_at") == 2), "make capstone PART=1 다시"))
    return rows


# ── 되돌리기 ──
def reset(sc: Scenario = MAIN, echo=print) -> None:
    """시나리오가 바꾼 것을 처음으로: 직원 잠금·비밀번호·토큰 폐기, 받는 사람 칸(사고 전 값), 승인 기록, 서명키 폐기 해제"""
    pre = None
    try:
        pre = json.loads((sc.dir / "pre.json").read_text("utf-8"))
    except FileNotFoundError:
        pass
    if actor_exists(sc):
        ensure_actor(sc)
    if pre:
        from daon.restore import COLUMNS
        for oid, vals in pre["receivers"].items():
            sets = ", ".join(f"{c} = " + ("NULL" if vals.get(c) is None else f"UNHEX('{vals[c].encode('utf-8').hex()}')") for c in COLUMNS)
            mysql(f"UPDATE oms_order SET {sets} WHERE id = {int(oid)}")
        ids = ",".join(pre["receivers"])
        mysql(f"DELETE FROM oms_order_operate_history WHERE order_id IN ({ids}) AND note = '수령인 정보 수정' AND create_time >= NOW() - INTERVAL 7 DAY")
    ap.reset()
    try:
        rotate_key("reset")
    except SystemExit:
        pass
    for f in ("answer.json", "rerun.json", "response.json", "attack-token.json", f"{sc.actor}.password.json"):
        (sc.dir / f).unlink(missing_ok=True)
    clear_counters()
    echo(f"캡스톤 {sc.name} 되돌림(사고 기록 폴더 .daon/incident/{sc.incident}/는 남김: 지우려면 make wipe FORCE=1)")


# ── 명령 ──
def cmd_capstone(a) -> None:
    sc = CHECK if a.scenario == "check" else MAIN
    try:
        if a.reset:
            reset(sc)
            return
        part = a.part or "1"
        if part == "1":
            run(sc)
        elif part == "status":
            for name, ok, note in status(sc):
                print(f"  [{'그린' if ok else '레드'}] {name} · {note}")
        elif part == "answer":
            ans = load(sc)
            if not ans:
                raise NotReady("아직 사고가 없습니다: make capstone PART=1")
            print(f"정답 표(강사용) · 시작 {ans['started']} · 침입 계정 {ans['actor']} · 유출 커밋 {ans['leak']['commit']} {ans['leak']['at']}")
            print(f"  대량 조회 {ans['bulk']['rows']}건({ans['bulk']['requests']}번) · 바뀐 주문 {len(ans['changed'])}건 · 사고 뒤 정상 변경 주문 {ans['legit_after']['order']}")
            print(f"  환불 시도 {len(ans['refund'])}건: " + ", ".join(f"{t} {v['approval']}(결제사 {v['paid']}건)" for t, v in ans["refund"].items()))
            print(f"  trace_id 셋: {', '.join(ans['traces3'])}")
            for row in ans["table"]:
                print(f"  {row['단계']:<24} {row['결과']:<5} {row['근거']}")
        elif part == "respond":
            respond(sc)
            print("정답 대응 순서를 돌렸습니다(강사용). make capstone PART=status")
        else:
            raise SystemExit("PART=1|status|answer|respond 또는 RESET=1")
    except NotReady as e:
        raise SystemExit(f"[준비 안 됨] {e}")


def register(add, stack_opt) -> None:
    add("capstone", cmd_capstone, stack_opt, (["--part"], {}), (["--reset"], {"action": "store_true"}),
        (["--scenario"], {"choices": ["main", "check"], "default": "main"}))
