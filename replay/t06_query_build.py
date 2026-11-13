"""T06 · 검색어·정렬 값이 쿼리 문장의 일부가 됨(SQL 인젝션, A05). 수업 3-2(검색어)·3-3(정렬).

로그인 없이 다온마켓 상품 검색(GET /daon/product/search, [교육용 추가])을 부르고 **검색어 칸이나 정렬 값만** 바꾼다.
쿼리 문장의 모양이 바뀌었다는 것을 보이는 데서 멈춘다(다른 표를 읽는 단계·우회는 하지 않는다).

  검색어 ① 작은따옴표 하나 → 응답에 DB 문법 오류 문구가 실리나
  검색어 ② 조건이 늘 참이 되는 값 → 공개하지 않은 상품(시드 231·232)이 섞여 나오나
  정렬 ③ 화면에 없는 컬럼(재고) 이름 → 그 순서로 정렬되나(오름·내림이 정확히 뒤집히면 서버가 그 컬럼으로 정렬한 것)
  정렬 ④ 컬럼 이름이 아닌 값 → DB 문법 오류가 나나
하나라도 보이면 레드. 3-2를 마치면 ①②가, 3-3까지 마치면 ③④도 그린이 된다.
"""
from __future__ import annotations

import httpx

from replay import NotReady, Result
from replay._http import portal

T = "T06"
TITLE = "검색어·정렬 값이 쿼리 문장의 일부가 됨(SQL 인젝션)"
LESSON = "3-2·3-3"
SOURCE = "[교육용 추가] 상품 검색 DaonProductSearchDao.xml이 검색어·정렬을 ${}로 이어 붙임(원본 매퍼에는 없음)"
STACKS = ("spring",)

PATH = "/daon/product/search"
HIDDEN = {231, 232}                         # seed/spring/20-daon-accounts.sql 의 비공개 가짜 상품
# 재연이 바꾸는 값(교재·영상에는 싣지 않는다)
KEYWORD_QUOTE = "'"
KEYWORD_ALWAYS = "' or 1=1 or name like '"
SORT_HIDDEN_DESC = "stock desc, id asc"
SORT_HIDDEN_ASC = "stock asc, id desc"
SORT_BROKEN = "price desc,("
NORMAL_KEYWORD = "이어폰"


def search(keyword: str = "", sort: str | None = None, trace_id: str | None = None) -> httpx.Response:
    params = {"keyword": keyword}
    if sort is not None:
        params["sort"] = sort
    headers = {"X-Trace-Id": trace_id} if trace_id else {}
    try:
        with portal() as c:
            return c.get(PATH, params=params, headers=headers)
    except httpx.TransportError as e:
        raise NotReady(f"mall-portal에 닿지 않습니다. make up STACK=spring 먼저 실행하세요. ({e.__class__.__name__})")


def body(r: httpx.Response) -> dict:
    try:
        return r.json()
    except ValueError:
        return {}


def ids(r: httpx.Response) -> list[int] | None:
    b = body(r)
    data = b.get("data") if b.get("code") == 200 else None
    return [p["id"] for p in data] if isinstance(data, list) else None


def sql_error(r: httpx.Response) -> str | None:
    """응답에 DB 문법 오류 문구가 실렸으면 그 앞부분(80자)."""
    msg = str(body(r).get("message") or "")
    if any(w in msg for w in ("SQL syntax", "SQLSyntaxError", "bad SQL grammar", "Unknown column")):
        return msg[:80] + ("…" if len(msg) > 80 else "")
    return None


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 원본 쿼리 빌더가 바인딩을 쓴다, lessons/s3-02.md).")
    normal = ids(search(NORMAL_KEYWORD))
    if normal is None:
        raise NotReady(f"{PATH}가 정상 검색에도 응답하지 않습니다. 섹션 3 랩이 들어간 앱인지 확인하세요(make up BUILD=1).")
    red, ev = [], [f"정상 검색 「{NORMAL_KEYWORD}」: {len(normal)}개 {normal}"]

    r = search(KEYWORD_QUOTE)
    err = sql_error(r)
    ev.append(f"① 검색어 칸에 작은따옴표 하나: HTTP {r.status_code} code={body(r).get('code')} "
              + (f"DB 오류 문구 「{err}」" if err else f"결과 {len(ids(r) or [])}개"))
    if err:
        red.append("검색어 ①")

    r = search(KEYWORD_ALWAYS)
    got = ids(r) or []
    hidden = sorted(HIDDEN & set(got))
    ev.append(f"② 검색어 칸 값을 바꿈(조건이 늘 참): 결과 {len(normal)}개 → {len(got)}개"
              + (f", 비공개 상품 포함 {hidden}" if hidden else ", 비공개 상품 없음"))
    if hidden:
        red.append("검색어 ②")

    keyword = "다온"
    desc, asc, new = search(keyword, SORT_HIDDEN_DESC), search(keyword, SORT_HIDDEN_ASC), ids(search(keyword, "new")) or []
    d, a = ids(desc), ids(asc)
    sorted_by_hidden = d is not None and a is not None and len(d) > 2 and d == list(reversed(a)) and d != new
    ev.append(f"③ 정렬 값을 화면에 없는 컬럼(재고) 이름으로: HTTP {desc.status_code} code={body(desc).get('code')} "
              + (f"→ 재고 많은 순 앞 5개 {d[:5]}, 반대 순서도 정확히 뒤집힘" if sorted_by_hidden else "→ 그 순서로 정렬되지 않음"))
    if sorted_by_hidden:
        red.append("정렬 ③")

    r = search(keyword, SORT_BROKEN)
    err = sql_error(r)
    ev.append(f"④ 정렬 값을 컬럼 이름이 아닌 값으로: HTTP {r.status_code} code={body(r).get('code')} "
              + (f"DB 오류 문구 「{err}」" if err else f"message={str(body(r).get('message'))[:60]!r}"))
    if err:
        red.append("정렬 ④")

    if red:
        return Result(True, f"값 하나로 쿼리 문장의 모양이 바뀐다({', '.join(red)})", ev)
    return Result(False, "검색어는 값으로만, 정렬은 허용 목록으로만 쓰인다", ev)
