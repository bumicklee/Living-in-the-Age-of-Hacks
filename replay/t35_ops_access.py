"""T35 · 운영 접근이 공용 계정·데모 계정·상시 권한으로 열려 있다. 수업 13-5(예고)·13-6.

[회사 설정] 운영팀 공용 계정 daon_ops(슈퍼 관리자, 비밀번호는 팀 위키에 있다는 설정) — 13-1이 끝나면 앱이 뜰 때 생긴다(DaonStaffSeed)
[원본 기본값] 원본 데모 관리자 8개(admin·macro·test …)가 시드에 그대로 열려 있다(점검표 F19)
[원본 기본값] 슈퍼 관리자 역할이면 관리자 역할 변경 같은 고위험 기능을 언제나 바로 쓴다(시간 제한·다른 사람 승인 없음)

장면(이 순서로 한 번씩):
  ① 공용 계정 daon_ops로 로그인          레드: 성공         그린: 다른 실패와 같은 문구로 거부 + auth.denied.disabled_account
  ② 원본 데모 관리자 macro로 로그인       레드: 성공         그린: 위와 같음
  ③ admin(슈퍼 관리자)이 권한 상승 없이 관리자 역할 변경(같은 값으로 다시 쓰기, 바뀌는 것 없음)
                                         레드: 200          그린: 403 + ops.denied.no_elevation
  참고 줄: 슈퍼 관리자 역할을 가진 열린 계정 수, F11(다른 관리자 비밀번호를 수정 API로 바꾸기)은 4-7에서 이미 막혔는지(바뀌면 되돌린다)
안전 기준: 127.0.0.1의 랩만, 가짜 계정. 비밀번호는 찍지 않는다. 역할 변경은 같은 값으로만 한다.
"""
from __future__ import annotations

from replay import NotReady, Result
from replay import _approval as ap
from replay._http import admin
from replay._money import clear_counters, mysql, one

T = "T35"
TITLE = "운영 접근이 공용 계정·데모 계정·상시 권한으로 열려 있음"
LESSON = "13-6"
SOURCE = "[회사 설정] 공용 계정 daon_ops(DaonStaffSeed, 13-1 뒤) + [원본 기본값] 데모 관리자 8개(F19)·슈퍼 관리자 상시 고위험 권한"
STACKS = ("spring",)
PROBE = "lab-f11-probe-1!"     # F11 확인용 가짜 새 비밀번호(바뀌면 바로 되돌린다)


def f11_probe() -> str:
    """admin이 관리자 수정 API에 password 칸을 넣어 staff_order를 수정한다. 4-7 뒤에는 비밀번호가 바뀌지 않아야 한다"""
    before = one("SELECT password FROM ums_admin WHERE username='staff_order'")
    tok = ap.login("admin")
    if not tok or not before:
        return "확인 못 함(admin 로그인 실패)"
    with admin() as c:
        r = c.post("/admin/update/102", headers={"Authorization": tok}, json={"username": "staff_order", "password": PROBE, "nickName": "주문 담당"})
    after = one("SELECT password FROM ums_admin WHERE username='staff_order'")
    if after != before:
        mysql(f"UPDATE ums_admin SET password='{before}' WHERE username='staff_order'")
        from daon import stack as st
        st.redis_cli("DEL", "mall:ums:admin:staff_order")
        return f"HTTP {r.status_code} · 비밀번호가 바뀜(되돌림) — 4-7 step-up 스위치를 확인하세요"
    return f"HTTP {r.status_code} · 비밀번호 그대로(4-7에서 막힘)"


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s13-06).")
    red, ev = [], []
    clear_counters()                     # 4-3 로그인 실패 제한 카운터(재연을 연달아 돌려도 같은 결과가 나오게)
    ok1, msg1 = ap.login_message("daon_ops")
    ev.append(f"① 공용 계정 daon_ops 로그인: {'성공' if ok1 else '거부'} · 문구 「{msg1}」")
    if ok1:
        red.append("공용 운영 계정으로 로그인됨(누가 했는지 모름)")
    ok2, msg2 = ap.login_message("macro")
    ev.append(f"② 원본 데모 관리자 macro 로그인: {'성공' if ok2 else '거부'} · 문구 「{msg2}」")
    if ok2:
        red.append("원본 데모 관리자 계정으로 로그인됨(F19)")
    r = ap.role_update_as("admin")
    code = r.status_code
    try:
        code = r.json().get("code", code) if r.status_code == 200 else code
    except ValueError:
        pass
    ev.append(f"③ admin이 권한 상승 없이 관리자 역할 변경(같은 값): HTTP {r.status_code} · 본문 code {code}")
    if r.status_code == 200 and code == 200:
        red.append("슈퍼 관리자가 상시로 고위험 기능을 바로 씀(사유·다른 사람 승인·시간 제한 없음)")
    ev.append(f"참고: 슈퍼 관리자 역할을 가진 열린 계정 {ap.super_admin_count()}개 · F11 {f11_probe()}")
    if red:
        return Result(True, " / ".join(red), ev)
    return Result(False, "공용·데모 계정 닫힘, 고위험 기능은 시간 제한 권한 상승 뒤에만", ev)
