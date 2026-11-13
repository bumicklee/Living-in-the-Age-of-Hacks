"""3-5 출력 인코딩·CSP·쿠키 속성 · T07의 두 번째 겹(관리자 화면 CSP)과 위반 보고."""
import httpx
import pytest

from replay import _browser, _logs

LAB_TITLE = "[교육용 랩] CSP 시험"


def _inject_inline_handler(page) -> str:
    """출력 인코딩이 빠진 칸을 흉내 낸다: 이 페이지에 인라인 처리기가 든 HTML을 넣고 실행되는지 본다(밖으로 보내지 않음)."""
    page.evaluate("""t => { const d = document.createElement('div');
        d.innerHTML = '<img src="x" onerror="document.title=\\'' + t + '\\'">'; document.body.appendChild(d); }""", LAB_TITLE)
    page.wait_for_timeout(1500)
    return page.title()


def test_admin_web_sends_csp(stack):
    r = httpx.get(_browser.ADMIN_WEB + "/", timeout=10)
    csp = r.headers.get("content-security-policy", "")
    assert "script-src 'self'" in csp and "report-uri" in csp, f"기대: 관리자 화면 응답에 CSP(script-src 'self', report-uri). 실제: {csp!r}"


def test_inline_script_blocked_even_without_encoding(stack):
    with _browser.admin_page("staff_goods") as page:
        _browser.open_reviews(page, settle=0.2)
        title = _inject_inline_handler(page)
        events = _browser.csp_events(page)
    assert title != LAB_TITLE, "기대: 출력 인코딩이 빠진 칸이 있어도 CSP가 인라인 스크립트를 막습니다(탭 이름 그대로)"
    assert any(e['directive'] == 'script-src-attr' and e['blockedURI'] == 'inline'
               and e['disposition'] == 'enforce' for e in events), "기대: 실제 inline 처리기 차단 DOM 이벤트"


def test_csp_violation_reported_and_logged(stack):
    since = _logs.now()
    with _browser.admin_page("staff_goods") as page:
        _browser.open_reviews(page, settle=0.2)
        _inject_inline_handler(page)
        e = _logs.wait_event("mall-admin", since, "csp.violation", "3-5", stack, timeout=10.0)   # 보고는 페이지가 열려 있을 때 간다
    assert e, "기대: 브라우저의 CSP 위반 보고가 mall-admin에 도착해 SECURITY csp.violation(3-5)으로 남아야 합니다"
    assert (e.get("target") or "").startswith("directive:script-src") and e.get("actor") == "anonymous", f"실제: {e}"
    assert "inline" in (e.get("reason") or ""), f"기대: reason에 막힌 출처(inline). 실제: {e.get('reason')}"


@pytest.mark.normal
def test_admin_pages_work(stack):
    with _browser.admin_page("admin") as page:
        page.goto(_browser.ADMIN_WEB + "/#/pms/product", wait_until="domcontentloaded")
        page.wait_for_selector(".el-table__row", timeout=15000)
        products = page.locator(".el-table__row").count()
        _browser.open_reviews(page, settle=0.2)
        banner = page.locator('[data-testid="lab-banner"]').is_visible()
        blocked_app = [e for e in _browser.csp_events(page)
                       if e['directive'].startswith('script') and 'assets/' in e['blockedURI']]
    assert products > 0 and banner and not blocked_app, \
        f"기대: 로그인·상품 목록·후기 목록·「교육용 랩」 띠가 정상. 실제: 상품 줄 {products}, 띠 {banner}, 막힌 앱 스크립트 {blocked_app[:2]}"
