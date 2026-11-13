"""관리자 화면을 진짜 브라우저(Playwright, 헤드리스 Chromium)로 여는 도우미. T07·3-4·3-5·3-미션 시험이 쓴다.

XSS는 서버 응답이 아니라 브라우저에서 실행되는지가 핵심이라 HTTP만으로는 잴 수 없다.
처음 한 번: make setup (requirements.txt의 playwright + `python -m playwright install chromium`).
이 PC의 랩(127.0.0.1:8090)만 연다. 페이지가 밖으로 나가는 요청은 막는다(route).
"""
from __future__ import annotations

import contextlib
import time
from pathlib import Path
from urllib.parse import urlparse

from daon.config import URLS
from replay import NotReady
from replay._http import ADMINS

ADMIN_WEB = URLS["spring"]["admin_web"]
REVIEW_PAGE = "/#/daon-review"
LOCAL = {"127.0.0.1", "localhost"}
CSP_OBSERVER_SCRIPT = Path(__file__).with_name("csp_observer.js").read_text()


def csp_events(page) -> list[dict]:
    """현재 문서만 관찰한다. 페이지 이동 시 배열은 초기화된다."""
    return page.evaluate("() => window.__daonCspEvents || []")


@contextlib.contextmanager
def browser():
    try:
        from playwright.sync_api import Error, sync_playwright
    except ImportError:
        raise NotReady("브라우저 확인에는 Playwright가 필요합니다. make setup을 다시 실행하세요(playwright + chromium 설치).")
    with sync_playwright() as p:
        try:
            b = p.chromium.launch()
        except Error as e:
            raise NotReady(f"Chromium을 띄우지 못했습니다. .venv/bin/python -m playwright install chromium 을 실행하세요. ({str(e)[:120]})")
        try:
            yield b
        finally:
            b.close()


def _local_only(route):
    host = urlparse(route.request.url).hostname or ""
    if host in LOCAL:
        return route.continue_()
    return route.abort()        # 랩 밖으로는 아무것도 보내지 않는다(외부 상품 사진 주소 등)


@contextlib.contextmanager
def admin_page(user: str = "staff_goods"):
    """관리자 화면. 첫 스크립트 실행 전부터 DOM CSP 위반을 관찰한다."""
    with browser() as b:
        ctx = b.new_context()
        ctx.add_init_script(CSP_OBSERVER_SCRIPT)
        ctx.route("**/*", _local_only)
        page = ctx.new_page()
        try:
            page.goto(ADMIN_WEB + "/#/login", wait_until="domcontentloaded")
        except Exception as e:
            raise NotReady(f"관리자 화면({ADMIN_WEB})이 열리지 않습니다. make up STACK=spring 먼저 실행하세요. ({str(e)[:80]})")
        page.fill('[data-testid="login-username"]', user)
        page.fill('[data-testid="login-password"]', ADMINS[user]["password"])
        page.click('[data-testid="login-submit"]')
        try:
            page.wait_for_url(lambda u: "/login" not in u, timeout=15000)
        except Exception:
            raise NotReady(f"관리자 화면에 {user}로 로그인하지 못했습니다.")
        try:
            yield page
        finally:
            ctx.close()


def open_reviews(page, review_id: int | None = None, settle: float = 1.5) -> None:
    """후기 목록 화면을 열고(새로 고침), review_id 칸이 그려질 때까지 기다린다."""
    page.goto(ADMIN_WEB + "/?r=" + str(time.time_ns()) + REVIEW_PAGE, wait_until="domcontentloaded")
    page.wait_for_selector('[data-testid="review-table"]', timeout=15000)
    if review_id is not None:
        page.wait_for_selector(f'[data-testid="review-content-{review_id}"]', state="attached", timeout=15000)
    page.wait_for_timeout(int(settle * 1000))       # 이미지 오류 처리기(onerror) 같은 늦은 실행을 기다린다


def cell(page, testid: str) -> dict:
    """칸 하나: 안에 그려진 요소 수, 글자, 요소 이름들."""
    return page.eval_on_selector(f'[data-testid="{testid}"]', """el => ({
        elements: el.querySelectorAll('*').length,
        tags: Array.from(el.querySelectorAll('*')).map(e => e.tagName.toLowerCase()),
        text: el.textContent,
    })""")
