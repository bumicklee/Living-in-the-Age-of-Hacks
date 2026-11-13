"""T13 · 토큰이 오래 살고, 로그아웃해도 끝나지 않는다(A07). 수업 4-8.

127.0.0.1의 다온마켓 관리자 로그인에 가짜 직원 계정 staff_goods로
  ① 로그인 → 로그아웃 → 같은 토큰으로 /admin/info 를 부른다. 200이면 레드.
  ② 토큰 내용의 exp를 읽는다. 수명이 15분보다 길거나 exp가 13자리(밀리초)면 레드.
토큰 값은 화면에 찍지 않는다(수명·자릿수만).
"""
from __future__ import annotations

import base64
import json
import time

from replay import NotReady, Result
from replay._http import ADMINS, admin

T = "T13"
TITLE = "토큰 수명이 길고, 로그아웃해도 토큰이 계속 통함"
LESSON = "4-8"
SOURCE = "[원본 기본값] 수명 7일·exp 밀리초 + [원본 확인됨] 로그아웃이 캐시만 비움"
STACKS = ("spring",)

USER = "staff_goods"
MAX_SECONDS = 15 * 60


def claims(token: str) -> dict:
    part = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s4-08/express.diff).")
    with admin() as c:
        token = c.post("/admin/login", json={"username": USER, "password": ADMINS[USER]["password"]}).json()["data"]["token"]
        h = {"Authorization": "Bearer " + token}
        before = c.get("/admin/info", headers=h).json().get("code")
        c.post("/admin/logout", headers=h)
        after = c.get("/admin/info", headers=h).json().get("code")
    exp = int(claims(token).get("exp", 0))
    exp_s = exp / 1000 if exp > 100_000_000_000 else exp
    life = exp_s - time.time()
    ev = [f"① {USER} 로그인 → /admin/info {before} → 로그아웃 → 같은 토큰으로 /admin/info {after}",
          f"② exp {len(str(exp))}자리({'밀리초로 보임' if len(str(exp)) >= 13 else '초'}) · 남은 수명 약 {life / 3600:.1f}시간"
          if life > 3600 else f"② exp {len(str(exp))}자리(초) · 남은 수명 약 {life / 60:.0f}분",
          "토큰 값은 찍지 않았습니다"]
    problems = []
    if after == 200:
        problems.append("로그아웃한 토큰이 계속 통함")
    if life > MAX_SECONDS + 60:
        problems.append(f"토큰 수명이 15분보다 김(약 {life / 86400:.1f}일)" if life > 86400 else "토큰 수명이 15분보다 김")
    if len(str(exp)) >= 13:
        problems.append("exp가 밀리초(JWT 표준은 초)")
    if problems:
        return Result(True, ", ".join(problems), ev)
    return Result(False, "로그아웃하면 토큰이 바로 끝나고, 수명은 15분, exp는 초 단위다", ev)
