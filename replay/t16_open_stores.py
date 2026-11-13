"""T16 · Redis·MongoDB·RabbitMQ·MinIO가 비밀번호 없이(또는 기본값으로) PC에 열려 있다. 수업 6-4(막기는 A1·A3).

PC에서 바로 접속해 본다. 하나라도 열리면 레드.
6-4: 포트를 닫았어도 저장소 안의 상태가 기본값이면 레드다. 컨테이너 안에서 **읽기 전용 상태**만 본다(계정 이름·건수·참/거짓. 비밀번호·키 값은 어디에도 찍지 않는다):
MongoDB가 인증 없이 응답하는지, RabbitMQ에 기본 계정(guest·원본 데모 계정)이 남았는지, MinIO 루트 계정이 알려진 기본값인지(값 자체는 출력하지 않고 목록과 같은지만).
"""
from __future__ import annotations

import socket
import subprocess

import httpx

from daon.config import HOST, ROOT, port
from replay import NotReady, Result

T = "T16"
TITLE = "내부 저장소가 비밀번호 없이 PC에 열림"
LESSON = "6-4"
SOURCE = "[회사 설정]"
STACKS = ("spring",)


def _redis_noauth() -> str | None:
    p = port("DAON_REDIS_PORT", 6379)
    try:
        with socket.create_connection((HOST, p), timeout=1.0) as s:
            s.sendall(b"PING\r\n")
            reply = s.recv(64)
    except OSError:
        return None
    return f"Redis {HOST}:{p} 비밀번호 없이 PING → {reply.strip().decode(errors='replace')}" if reply.startswith(b"+PONG") else None


def _mongo_open() -> str | None:
    p = port("DAON_MONGO_PORT", 27017)
    try:
        with socket.create_connection((HOST, p), timeout=1.0):
            return f"MongoDB {HOST}:{p} 포트가 PC에 열림(인증 설정 없음)"
    except OSError:
        return None


def _rabbit_default() -> str | None:
    p = port("DAON_RABBIT_UI_PORT", 15672)
    try:
        r = httpx.get(f"http://{HOST}:{p}/api/whoami", auth=("mall", "mall"), timeout=2.0)
    except httpx.TransportError:
        return None
    return f"RabbitMQ 관리 화면 {HOST}:{p} 기본 계정 mall/mall 로그인 성공" if r.status_code == 200 else None


def _minio_default() -> str | None:
    p = port("DAON_MINIO_UI_PORT", 9001)
    try:
        r = httpx.post(f"http://{HOST}:{p}/api/v1/login", json={"accessKey": "minioadmin", "secretKey": "minioadmin"}, timeout=2.0)
    except httpx.TransportError:
        return None
    return f"MinIO 관리 화면 {HOST}:{p} 기본 계정 minioadmin 로그인 성공" if r.status_code in (200, 204) else None


def _dx(service: str, *cmd: str) -> subprocess.CompletedProcess | None:
    """실행 중인 컨테이너 안에서 한 번 실행. 컨테이너가 없으면 None."""
    try:
        r = subprocess.run(["docker", "exec", f"daon-{service}-1", *cmd], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        return None
    return r if "No such container" not in r.stderr and "is not running" not in r.stderr else None


def _mongo_noauth_inside() -> str | None:
    r = _dx("mongo", "mongosh", "--quiet", "--eval", "db.adminCommand({listDatabases: 1}).ok")
    return "MongoDB 인증 없이 DB 목록 조회가 됨(인증 꺼짐)" if r and r.returncode == 0 and r.stdout.strip() == "1" else None


def _rabbit_default_users() -> str | None:
    r = _dx("rabbitmq", "rabbitmqctl", "-q", "list_users", "--silent")
    if not r or r.returncode != 0:
        return None
    names = {l.split("\t")[0] for l in r.stdout.splitlines() if l}
    left = sorted(names & {"guest", "mall"})
    return f"RabbitMQ에 기본 계정이 남아 있음({len(left)}개: {', '.join(left)})" if left else None


def _minio_default_root() -> str | None:
    defaults = ROOT / "spring/mall-security/src/main/resources/daon/known-default-secrets.txt"
    # 1-12 비교 지점에는 6-3의 기본값 목록 파일이 아직 없다.
    known = {l.strip().lower() for l in defaults.read_text("utf-8").splitlines()
             if l.strip() and not l.startswith("#")} if defaults.exists() else {"minioadmin"}
    u, p = _dx("minio", "printenv", "MINIO_ROOT_USER"), _dx("minio", "printenv", "MINIO_ROOT_PASSWORD")
    if not u or not p:
        return None
    bad = [n for n, r in (("계정 이름", u), ("비밀번호", p)) if r.stdout.strip().lower() in known]
    return f"MinIO 루트 {' · '.join(bad)}가 원본 기본값(값은 가림)" if bad else None


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다.")
    found = [x for x in (_redis_noauth(), _mongo_open(), _rabbit_default(), _minio_default(),
                         _mongo_noauth_inside(), _rabbit_default_users(), _minio_default_root()) if x]
    if found:
        return Result(True, f"PC에서 내부 저장소 {len(found)}곳에 바로 들어간다", found)
    return Result(False, "PC에서 내부 저장소에 바로 들어갈 수 없다", ["Redis·MongoDB·RabbitMQ·MinIO 모두 PC에서 닫힘, 안쪽은 인증 켜짐·기본 계정 없음"])
