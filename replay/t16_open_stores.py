"""T16 · Redis·MongoDB·RabbitMQ·MinIO가 비밀번호 없이(또는 기본값으로) PC에 열려 있다. 수업 6-4(막기는 A1·A3).

PC에서 바로 접속해 본다. 하나라도 열리면 레드.
"""
from __future__ import annotations

import socket

import httpx

from daon.config import HOST, port
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


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다.")
    found = [x for x in (_redis_noauth(), _mongo_open(), _rabbit_default(), _minio_default()) if x]
    if found:
        return Result(True, f"PC에서 내부 저장소 {len(found)}곳에 바로 들어간다", found)
    return Result(False, "PC에서 내부 저장소에 바로 들어갈 수 없다", ["Redis·MongoDB·RabbitMQ·MinIO 모두 닫힘 또는 인증 필요"])
