"""T28 · 검사한 이동 주소와 브라우저가 쓰는 주소가 다르다(로그인 뒤 이동, P7). 수업 11-1(원리)·11-2(고치기).

[교육용 추가] GET /daon/go?next= 가 next를 문자열 앞부분으로만 검사한다(「/」 또는 https://shop.daon.example 로 시작하면 통과).
재연은 OWASP 치트시트 수준의 교과서 예시 세 개만 보낸다(도메인은 모두 .example). 브라우저는 열지 않고 응답의 Location 헤더만 본다.
  레드: Location이 허용 목록(shop.daon.example, 상대 경로) 밖의 호스트로 읽힌다
  그린: 세 예시 모두 Location: / (11-2 SafeRedirect: 한 번 파싱한 URI 객체로 검사와 이동)
"""
from __future__ import annotations

from urllib.parse import urljoin, urlsplit

import httpx

from daon.config import URLS
from replay import NotReady, Result

T = "T28"
TITLE = "검사한 이동 주소와 브라우저가 쓰는 주소가 다르다(로그인 뒤 이동)"
LESSON = "11-1·11-2"
SOURCE = "[교육용 추가] DaonRedirectController GET /daon/go?next= 의 문자열 앞부분 비교(startsWith)"
STACKS = ("spring",)

ALLOWED_HOSTS = {"shop.daon.example"}
# (설명, next 값). 세 개로 끝(안전 기준: 더 많은 변형은 싣지 않는다)
EXAMPLES = [
    ("슬래시 두 개로 시작", "//other.example/welcome"),
    ("허용 이름 뒤에 다른 도메인", "https://shop.daon.example.other.example/welcome"),
    ("허용 이름이 사용자 정보 자리에", "https://shop.daon.example@other.example/welcome"),
]


def go(next_value: str) -> str | None:
    """GET /daon/go?next=… 의 Location 헤더(따라가지 않는다)."""
    base = URLS["spring"]["portal"]
    try:
        r = httpx.get(base + "/daon/go", params={"next": next_value}, follow_redirects=False, timeout=10)
    except httpx.TransportError as e:
        raise NotReady(f"mall-portal({base})에 닿지 않습니다. make up STACK=spring ({e.__class__.__name__})")
    if r.status_code == 404:
        raise NotReady("/daon/go가 없습니다(교육용 추가 기능). make goto L=11-1 로 지점을 맞추세요")
    return r.headers.get("location")


def browser_host(location: str | None) -> str | None:
    """브라우저가 이 Location을 읽으면 갈 호스트. 상대 주소면 None(지금 사이트 안)."""
    if not location:
        return None
    absolute = urljoin("http://app.daon.example/daon/go", location)
    host = urlsplit(absolute).hostname
    return None if host == "app.daon.example" else host


def checks() -> list[dict]:
    out = []
    for label, nxt in EXAMPLES:
        loc = go(nxt)
        host = browser_host(loc)
        out.append({"label": label, "next": nxt, "location": loc, "host": host,
                    "escaped": host is not None and host not in ALLOWED_HOSTS})
    return out


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s11-02).")
    rows = checks()
    ev = [f"{r['label']}: next={r['next']}  →  Location: {r['location']}  →  브라우저가 갈 호스트: {r['host'] or '(이 사이트 안)'}"
          for r in rows]
    bad = [r for r in rows if r["escaped"]]
    if bad:
        return Result(True, f"교과서 예시 {len(bad)}/{len(rows)}개가 허용 목록 밖 .example 호스트로 이동한다(검사 코드는 문자열 앞부분만 봤다)", ev)
    return Result(False, "세 예시 모두 이 사이트 안(Location: /)으로 간다", ev)
