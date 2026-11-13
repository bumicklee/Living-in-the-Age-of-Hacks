"""15-2 사고 대응 랩 도구(이름은 TODO H 「daon keys/tokens/gitea/incident」). 수업에서 손으로 하는 대응을 명령 하나씩으로 부른다.

    python -m daon incident lock --admin staff_night [--incident capstone]      격리(POST /daon/incident/lock-actor, 15-2 정답 뒤)
    python -m daon incident unlock --admin staff_night
    python -m daon incident reset-password --admin staff_night                   새 임시 비밀번호(화면에 찍지 않고 .daon/incident/<번호>/에)
    python -m daon keys rotate | retire | reset | status                         관리자·고객 토큰 서명키(OpenBao transit daon-jwt, 5-2)
    python -m daon tokens revoke --admin staff_night                             폐기 목록의 계정 단위(키를 바꾸지 않을 때)
    python -m daon gitea scan [--repo daon/ops-scripts]                          로컬 Gitea 저장소 이력 시크릿 스캔(6-2 규칙, 값은 가림)

격리·초기화는 고위험 기능이라 admin의 권한 상승(13-6, 묶음 incident-response)이 켜져 있어야 한다. --elevate는 랩 편의로 staff_lead가 승인한다.
비밀번호·토큰 값은 어디에도 찍지 않는다(파일은 .daon/, git에 넣지 않는다).
"""
from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

from daon.config import DATA
from replay import NotReady


def _call(path: str, body: dict, incident: str, reason: str, elevate: bool):
    from replay import _approval as ap
    from replay._http import admin
    if elevate:
        from replay import _incident
        _incident.elevate("admin")
    with admin() as c:
        return c.post(f"/daon/incident/{path}", headers={"Authorization": ap.must_login("admin")},
                      json=body | {"incidentId": incident, "reason": reason})


def cmd_incident(a) -> None:
    if not a.admin:
        raise SystemExit("--admin <직원 아이디>가 필요합니다(회원 격리는 14-4의 memberId API)")
    path = {"lock": "lock-actor", "unlock": "unlock-actor", "reset-password": "reset-password"}[a.action]
    try:
        r = _call(path, {"admin": a.admin}, a.incident, a.reason, a.elevate)
    except NotReady as e:
        raise SystemExit(f"[준비 안 됨] {e}")
    if r.status_code != 200:
        raise SystemExit(f"HTTP {r.status_code}: {r.text[:200]}\n(권한 상승 incident-response가 켜져 있나요? 직원 격리는 15-2 정답 뒤에 됩니다)")
    data = r.json().get("data") or {}
    if a.action == "reset-password":
        d = DATA / "incident" / a.incident
        d.mkdir(parents=True, exist_ok=True)
        f = d / f"{a.admin}.temporary-password.json"
        f.write_text(json.dumps({"admin": a.admin, "temporary": data.get("temporaryPassword")}, ensure_ascii=False), "utf-8")
        f.chmod(0o600)
        print(f"{a.admin} 비밀번호를 초기화했습니다. 임시 비밀번호는 {f.relative_to(DATA.parent)}(본인 확인 뒤 다른 경로로 전한다). 토큰도 끊었습니다")
    else:
        print(f"{a.admin}: {data.get('status')} · 토큰 {data.get('tokens')}")


def cmd_keys(a) -> None:
    from daon import cli
    cli.cmd_rotate_key(argparse.Namespace(key="jwt", action=a.action))
    if a.action == "retire":
        print("옛 키 버전의 토큰은 이제 모두 401입니다. 모든 관리자·고객이 다시 로그인합니다(누구 토큰이 새었는지 모를 때)")


def cmd_tokens(a) -> None:
    from daon import capstone
    if a.admin:
        capstone.revoke_tokens(a.admin, "daon-admin")
        print(f"admin:{a.admin}의 지금 이전 토큰을 모두 폐기했습니다(deny:sub:daon-admin:{a.admin})")
    elif a.member:
        capstone.revoke_tokens(a.member, "daon-portal")
        print(f"회원 {a.member}의 지금 이전 토큰을 모두 폐기했습니다(deny:sub:daon-portal:{a.member})")
    else:
        raise SystemExit("--admin 또는 --member")


def cmd_gitea(a) -> None:
    from daon import capstone, ci, secretscan as ss
    if not ci.available():
        raise SystemExit("로컬 Gitea(A8)가 꺼져 있습니다(7-5 이후 make up)")
    owner, _, name = a.repo.partition("/")
    if owner != "daon" or not name:
        raise SystemExit("--repo daon/<이름>")
    old, capstone.REPO = capstone.REPO, name
    try:
        repo = capstone._clone_gitea()
        try:
            found = ss._run(["git", str(repo), "-c", str(ss.config())])
        except ss.ScanUnavailable as e:
            raise SystemExit(str(e))
        finally:
            shutil.rmtree(Path(repo).parent, ignore_errors=True)
    except NotReady as e:
        raise SystemExit(f"[준비 안 됨] {e}")
    finally:
        capstone.REPO = old
    print(f"{a.repo} 이력 시크릿 스캔 · 찾은 곳 {len(found)}건(값은 가렸습니다)")
    print("\n".join(ss.summarize(found)))
    print("저장소에서 지우기보다 비밀번호를 바꾸는 것이 먼저다(6-2). 이력 정리는 그다음, 선택")


def register(add, stack_opt) -> None:
    add("incident", cmd_incident, (["action"], {"choices": ["lock", "unlock", "reset-password"]}), (["--admin"], {}),
        (["--incident"], {"default": "capstone"}), (["--reason"], {"default": "캡스톤 대응(가짜)"}), (["--elevate"], {"action": "store_true"}))
    add("keys", cmd_keys, (["action"], {"choices": ["rotate", "retire", "reset", "status"]}))
    add("tokens", cmd_tokens, (["action"], {"choices": ["revoke"]}), (["--admin"], {}), (["--member"], {}))
    add("gitea", cmd_gitea, (["action"], {"choices": ["scan"]}), (["--repo"], {"default": "daon/ops-scripts"}))
