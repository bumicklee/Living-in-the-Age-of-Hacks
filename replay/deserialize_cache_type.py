"""7-7 캐시 값의 타입 고르기 · 방어 확인(새 T 번호는 주지 않는다, 가칭 T18d: make replay T=18d).

「Redis에 쓸 수 있는 사람」(앱 자신·다른 앱·백업 복원 …)을 랩 운영자 계정으로 흉내 낸다.
관리자 캐시 키(mall:ums:admin:admin)의 값을 **허용 목록 밖의 무해한 JDK 클래스**(java.util.concurrent.atomic.AtomicLong, 숫자 하나를 담는 클래스)를 가리키는 글자로 바꾼 뒤,
그 사용자의 요청을 한 번 보내 앱이 무엇을 만드는지 본다. 위험한 클래스·공격 체인은 쓰지 않는다(안전 정책). 바뀌는 것은 「앱이 무엇을 만들까」 하나다.
  레드: 앱이 그 클래스로 객체를 만든다(앱 로그에 「AtomicLong cannot be cast to … UmsAdmin」 형 변환 실패 줄이 남음)
  그린: 허용 목록이 거부하고, 캐시를 버린 뒤 DB에서 다시 읽는다(SECURITY deserialize.denied.type이 남고 형 변환 실패 줄은 없음)
끝나면 그 키를 지운다(캐시는 다음 로그인 때 다시 채워진다).
"""
from __future__ import annotations

import httpx

from daon import stack as st
from replay import NotReady, Result, _logs
from replay._http import ADMINS, admin, admin_login

T = "T18d"
TITLE = "캐시 값의 글자가 앱이 만들 타입을 고름(방어 확인, 무해한 JDK 클래스)"
LESSON = "7-7"
SOURCE = "[원본 확인됨] BaseRedisConfig(LaissezFaireSubTypeValidator)"
STACKS = ("spring",)
KEY = "mall:ums:admin:admin"
HARMLESS = '["java.util.concurrent.atomic.AtomicLong",1]'
CAST_MARK = "AtomicLong cannot be cast"


def write_cache(value: str) -> None:
    r = st.redis_cli("SET", KEY, value, "EX", "300")
    if r.returncode != 0 or "OK" not in r.stdout:
        raise NotReady("Redis에 쓰지 못했습니다(make up STACK=spring): " + (r.stderr or r.stdout).strip()[:80])


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다.")
    since = _logs.now()
    try:
        with admin() as c:
            token = admin_login(c, "admin")
            write_cache(HARMLESS)
            r = c.get("/admin/info", headers={"Authorization": token})
    except httpx.TransportError as e:
        raise NotReady(f"mall-admin에 닿지 않습니다: {e.__class__.__name__}")
    finally:
        st.redis_cli("DEL", KEY)
    import time
    time.sleep(1.5)
    logs = st.compose(stack, "logs", "--no-log-prefix", "--since", since, "mall-admin", check=False, capture=True).stdout or ""
    created = CAST_MARK in logs
    denied = [e for e in _logs.security_events("mall-admin", since, stack) if e.get("event") == "deserialize.denied.type"]
    ev = [f"캐시 키 {KEY} 의 값을 허용 목록 밖 JDK 클래스(AtomicLong)를 가리키는 글자로 바꿈(값은 숫자 1)",
          f"/admin/info 응답 HTTP {r.status_code} · 앱 로그에 형 변환 실패 줄: {'있음' if created else '없음'} · SECURITY deserialize.denied.type: {len(denied)}건"]
    if denied:
        ev.append(f"이벤트 target: {denied[0].get('target')}")
    if created:
        return Result(True, "앱이 캐시 글자가 고른 클래스(AtomicLong)로 객체를 만들었다", ev)
    if not denied:
        return Result(True, "형 변환 실패 줄은 없지만 거부 이벤트도 없다(확인 필요)", ev)
    return Result(False, "허용 목록이 거부했고, 캐시를 버린 뒤 DB에서 다시 읽었다", ev)
