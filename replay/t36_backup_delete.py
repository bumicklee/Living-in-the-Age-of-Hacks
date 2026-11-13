"""T36 · 백업이 앱과 같은 자격증명·같은 저장소에 있다(백업 삭제 가능). 수업 14-2(14-1 예고). [회사 설정]

「앱의 자격증명을 가진 공격자가 백업을 지우려 한다」를 랩 자신의 자격증명으로 흉내 낸다. 악성 코드·암호화는 없다.
지우는 대상은 가짜 시드 DB의 덤프뿐이고, 지우기 전에 사본을 .daon/replay-tmp/에 두었다가 끝에 되돌린다.

장면(이 순서로)
  ① 앱 MinIO 계정(관리자 앱이 쓰는 앱 전용 접근 키, infra/.env)으로 앱 버킷 backups/mysql/ 목록 → 가장 최근 덤프 하나 지우기
       레드: 지워짐(그리고 되돌림)          그린: 앱 버킷에 백업이 없음(옮겨졌다)
  ② 같은 앱 계정으로 백업 저장소 backup-vault에 닿아 보기(앱이 붙은 망 app·data에서)
       레드: 목록이 보임                    그린: 이름을 찾지 못함·연결 안 됨(다른 망) 또는 AccessDenied
  ③ 백업 작업 계정 backup-writer(올리기만)로 잠금 버킷의 최신 백업 지우기
       레드: 지워짐                         그린: 거부(권한 없음·잠금) → backup-audit가 backup.denied.delete
안전 기준: 127.0.0.1 랩의 도커 망 안에서만, 가짜 데이터, 비밀 키는 찍지 않는다(환경 변수·볼륨 파일로만 넘긴다).
"""
from __future__ import annotations

from daon import backup as bk
from daon.config import DATA, ENV
from replay import NotReady, Result

T = "T36"
TITLE = "백업이 앱과 같은 자격증명·같은 저장소에 있음(앱 계정으로 백업 삭제)"
LESSON = "14-2"
SOURCE = "[회사 설정] 14-2 시작 지점의 백업 작업이 앱 MinIO 계정으로 앱 버킷 backups/mysql/에 덤프를 쓴다(14-1 뒤 랩이 세워 준다)"
STACKS = ("spring",)
KEEP = DATA / "replay-tmp"


def app_env() -> dict[str, str]:
    k, s = ENV.get("DAON_MINIO_APP_ACCESS_KEY", ""), ENV.get("DAON_MINIO_APP_SECRET_KEY", "")
    if not k or not s:
        raise NotReady("infra/.env에 앱 MinIO 접근 키가 없습니다(6-4 이후). make up STACK=spring")
    return {"K": k, "S": s}


def legacy_backups() -> list[dict]:
    r = bk.mc("mc ls --json app/mall/backups/mysql/", net="data", alias="app", host="minio:9000", env=app_env())
    return sorted([d for d in bk._json_lines(r.stdout) if d.get("type") == "file"], key=lambda d: d.get("lastModified", ""))


def scene_app_bucket(ev: list[str]) -> bool:
    """①: 지워지면 True(레드). 지우기 전 사본을 .daon/replay-tmp/에 두고 끝에 되돌린다"""
    rows = legacy_backups()
    if not rows:
        ev.append("① 앱 계정 → 앱 버킷 backups/mysql/: 백업 없음(앱 계정이 닿는 곳에 백업을 두지 않음)")
        return False
    key = str(rows[-1]["key"])
    if not bk.KEY_OK.match(key):
        raise NotReady("백업 이름 모양이 예상과 다릅니다")
    KEEP.mkdir(parents=True, exist_ok=True)
    keep = KEEP / key
    keep.unlink(missing_ok=True)
    cp = bk.mc(f"mc cp --quiet app/mall/backups/mysql/{key} /keep/{key} >/dev/null", net="data", alias="app", host="minio:9000",
               env=app_env(), mounts=[f"{KEEP}:/keep"])
    if cp.returncode or not keep.exists():
        raise NotReady("지우기 전에 사본을 두지 못해 재연을 멈춥니다(.daon/replay-tmp/)")
    # 남았는지는 mc stat으로 본다. mc ls는 없는 객체에도 종료 코드 0을 돌려줘 「거부」로 잘못 판정했다(2026-11-12).
    rm = bk.mc(f"mc rm app/mall/backups/mysql/{key} >/dev/null 2>&1; mc stat app/mall/backups/mysql/{key} >/dev/null 2>&1 && echo STILL || echo GONE",
               net="data", alias="app", host="minio:9000", env=app_env())
    gone = rm.stdout.strip().endswith("GONE")
    ev.append(f"① 앱 계정 → 앱 버킷 backups/mysql/: 백업 {len(rows)}개 보임 · 최신 {key} 지우기 → {'지워짐' if gone else '거부'}")
    if gone:
        back = bk.mc(f"mc cp --quiet /keep/{key} app/mall/backups/mysql/{key} >/dev/null", net="data", alias="app", host="minio:9000",
                     env=app_env(), mounts=[f"{KEEP}:/keep:ro"])
        ev.append("   랩 데이터 보존: .daon/replay-tmp/의 사본으로 되돌림" + ("" if back.returncode == 0 else "(되돌리기 실패, 사본은 .daon/replay-tmp/에 남음)"))
        if back.returncode == 0:
            keep.unlink(missing_ok=True)
    else:
        keep.unlink(missing_ok=True)
    return gone


def scene_app_to_vault(ev: list[str]) -> bool:
    """②: 앱 계정으로 백업 저장소 목록이 보이면 True(레드)"""
    if not bk.vault_on():
        ev.append("② 백업 저장소 backup-vault: 없음(백업은 앱 버킷 안에만 있다)")
        return False
    seen = False
    for net in ("app", "data"):
        r = bk.mc(f"mc ls vault/{bk.BUCKET}/ >/dev/null 2>&1 && echo LISTED || echo NO", net=net, env=app_env())
        listed = r.stdout.strip().endswith("LISTED")
        seen = seen or listed
        ev.append(f"② 앱 계정 → backup-vault({net} 망에서): {'목록이 보임' if listed else '닿지 않음(이름 없음·다른 망) 또는 거부'}")
    return seen


def scene_writer_delete(ev: list[str]) -> bool:
    """③: backup-writer로 잠금 버킷 최신 백업이 지워지면 True(레드)"""
    if not bk.vault_on():
        return False
    rows = bk.list_backups()
    if not rows:
        bk.backup_now()
        rows = bk.list_backups()
    if not rows:
        raise NotReady("잠금 버킷에 백업이 없습니다(make backup-now)")
    key = str(rows[-1]["key"])
    status = bk.delete_probe_as_writer(key)
    deleted = 200 <= status < 300
    ev.append(f"③ backup-writer(올리기만) → 잠금 버킷 mysql/{key} 지우기: {'지워짐' if deleted else '거부(backup.denied.delete)'}")
    return deleted


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s14-02로 PostgreSQL 덤프를 같은 잠금 버킷에).")
    app_env()
    ev: list[str] = []
    red: list[str] = []
    if scene_app_bucket(ev):
        red.append("앱 자격증명으로 백업을 지울 수 있음")
    if scene_app_to_vault(ev):
        red.append("앱 자격증명으로 백업 저장소가 보임")
    if scene_writer_delete(ev):
        red.append("백업 작업 계정으로 잠금 기간 안의 백업이 지워짐")
    if not ev or (not bk.vault_on() and not legacy_backups() and not red):
        raise NotReady("백업이 아직 없습니다. 14-2 시작 지점에서 make up 뒤 1분쯤 기다리거나 make backup-now")
    if red:
        return Result(True, " / ".join(red), ev)
    return Result(False, "앱 자격증명으로 백업 저장소에 닿지 않음 · 백업 작업 계정으로도 지우기 거부(잠금)", ev)

