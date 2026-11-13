"""T14 · DB 덤프 하나로 개인정보가 읽힌다(전화번호·주소를 평문으로 저장, Redis 회원 캐시도 평문). 수업 5-3(저장 암호화)·5-4(검색·가리기·원문 보기는 tests/test_s5_04_*).

이 PC의 다온마켓 MySQL·Redis(가짜 시드)를 읽기만 한다. 값은 화면에 찍지 않고 건수만 센다.
  ① 개인정보 칸 다섯 개에 평문이 몇 행 남았나(전화번호 모양이거나 enc:로 시작하지 않는 주소)
  ② Redis 회원 캐시에 평문 전화번호가 든 키가 몇 개인가
하나라도 있으면 레드.
"""
from __future__ import annotations

import re
import subprocess

from replay import NotReady, Result

T = "T14"
TITLE = "개인정보가 평문으로 저장됨(DB 덤프·Redis 캐시)"
LESSON = "5-3"
SOURCE = "[원본 확인됨] 전화번호·주소 평문 저장, 회원 캐시도 평문"
STACKS = ("spring",)

PHONE_RE = r"^[0-9]{2,3}-?[0-9]{3,4}-?[0-9]{4}$"
# (표, 칸, 종류)  종류 phone = 전화번호 모양이면 평문, addr = enc:로 시작하지 않으면 평문
COLUMNS = [
    ("ums_member", "phone", "phone"),
    ("oms_order", "receiver_phone", "phone"),
    ("oms_order", "receiver_detail_address", "addr"),
    ("ums_member_receive_address", "phone_number", "phone"),
    ("ums_member_receive_address", "detail_address", "addr"),
]
CACHE_PHONE = re.compile(r'"phone":"(\d{2,3}-?\d{3,4}-?\d{4})"')


def _mysql(sql: str) -> str:
    r = subprocess.run(["docker", "exec", "daon-mysql-1", "mysql", "-uroot", "-proot", "--default-character-set=utf8mb4", "mall", "-N", "-e", sql],
                       capture_output=True, text=True, timeout=60)
    if r.returncode:
        raise NotReady("MySQL에 닿지 않습니다(make up STACK=spring)")
    return r.stdout.strip()


def column_counts() -> list[tuple[str, str, int, int]]:
    """(표, 칸, 평문 행 수, 값이 든 전체 행 수)"""
    out = []
    for table, col, kind in COLUMNS:
        filled = f"{col} IS NOT NULL AND {col} <> ''"
        plain = f"{col} REGEXP '{PHONE_RE}'" if kind == "phone" else f"{filled} AND {col} NOT LIKE 'enc:%'"
        n_plain = int(_mysql(f"SELECT COUNT(*) FROM {table} WHERE {plain}") or 0)
        n_all = int(_mysql(f"SELECT COUNT(*) FROM {table} WHERE {filled}") or 0)
        out.append((table, col, n_plain, n_all))
    return out


def cache_plain_keys() -> tuple[int, int]:
    """(평문 전화번호가 든 회원 캐시 키 수, 회원 캐시 키 수)"""
    from daon import stack
    keys = stack.redis_cli("--scan", "--pattern", "mall:ums:member:*").stdout.split()
    bad = sum(1 for k in keys if CACHE_PHONE.search(stack.redis_cli("GET", k).stdout))
    return bad, len(keys)


def summary() -> tuple[list[str], int]:
    lines, total = [], 0
    for table, col, n_plain, n_all in column_counts():
        lines.append(f"{table}.{col}: 평문 {n_plain}행 / 값이 든 {n_all}행")
        total += n_plain
    bad, n = cache_plain_keys()
    lines.append(f"Redis 회원 캐시: 평문 전화번호가 든 키 {bad}개 / 캐시 키 {n}개")
    return lines, total + bad


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s5-03/express.diff).")
    lines, leaked = summary()
    if leaked:
        return Result(True, f"덤프 하나로 읽히는 평문 개인정보가 {leaked}곳 남아 있다", lines + ["값은 찍지 않고 건수만 셌습니다"])
    return Result(False, "개인정보 칸이 모두 잠겨 있고 캐시에도 평문이 없다", lines + ["값은 찍지 않고 건수만 셌습니다"])
