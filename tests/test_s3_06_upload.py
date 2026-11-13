"""3-6 파일 업로드: 확장자·내용·저장 위치 · T08. 원본 /minio/upload와 MinIO 버킷 mall."""
import uuid

import httpx
import pytest

from daon.config import URLS
from replay import _browser, _logs
from replay import t08_upload as t08
from replay._http import new_trace_id

PNG = t08.PNG_1x1


def _staff():
    return t08.staff_token()


def test_upload_requires_login(stack):
    status, body = t08.upload(PNG, f"anon-{uuid.uuid4().hex[:6]}.png")
    assert status == 401 or body.get("code") == 401, f"기대: 로그인 없는 업로드는 401. 실제: HTTP {status} code={body.get('code')}"


def test_upload_policy_requires_login(stack):
    r = httpx.get(URLS["spring"]["admin"] + "/aliyun/oss/policy", timeout=10)
    code = r.json().get("code") if r.headers.get("content-type", "").startswith("application/json") else None
    assert r.status_code == 401 or code == 401, f"기대: 클라우드 업로드 서명도 로그인 필요(401). 실제: HTTP {r.status_code} code={code}"


def test_non_image_rejected(stack):
    status, body = t08.upload("그림이 아닌 글자 파일".encode(), f"note-{uuid.uuid4().hex[:6]}.png", "image/png", _staff())
    assert status == 400, f"기대: 내용이 그림이 아니면(이름·Content-Type이 png여도) 400. 실제: HTTP {status} code={body.get('code')}"


def test_too_large_rejected(stack):
    big = PNG + b"\0" * (3 * 1024 * 1024)
    status, body = t08.upload(big, f"big-{uuid.uuid4().hex[:6]}.png", "image/png", _staff())
    assert status == 413, f"기대: 크기 상한(2MB)을 넘으면 413. 실제: HTTP {status} code={body.get('code')}"


def test_same_name_not_overwritten(stack):
    token = _staff()
    name = f"same-{uuid.uuid4().hex[:6]}.png"
    s1, b1 = t08.upload(PNG, name, "image/png", token)
    s2, b2 = t08.upload(PNG + b"second", name, "image/png", token)
    u1, u2 = (b1.get("data") or {}).get("url"), (b2.get("data") or {}).get("url")
    assert u1 and u2, f"정상 그림 업로드가 실패했습니다: {b1.get('message')} / {b2.get('message')}"
    assert u1 != u2 and not u1.endswith("/" + name), f"기대: 저장 이름은 서버가 새로 만든다(무작위 ID, 덮어쓰지 않음). 실제: {u1} · {u2}"
    assert t08.fetch(u1).content == PNG, "기대: 앞 파일은 그대로"


def test_bucket_listing_closed(stack):
    r = t08.list_bucket()
    assert r.status_code == 403, f"기대: 로그인 없는 버킷 목록은 403. 실제: HTTP {r.status_code}"


@pytest.mark.normal
def test_staff_uploads_image_and_it_is_public(stack):
    status, body = t08.upload(PNG, f"goods-{uuid.uuid4().hex[:6]}.png", "image/png", _staff())
    url = (body.get("data") or {}).get("url")
    assert status == 200 and body.get("code") == 200 and url, f"기대: 상품 담당 직원의 그림 업로드는 성공. 실제: HTTP {status} {body.get('message')!r}"
    # 주소를 보정하는 helper를 쓰면 브라우저에서 열리지 않는 내부 주소도 통과한다.
    r = httpx.get(url, timeout=10, trust_env=False)
    assert r.status_code == 200 and r.headers.get("content-type") == "image/png" and r.content == PNG, \
        f"기대: 상품 사진은 공개 주소로 보입니다. 실제: HTTP {r.status_code} {r.headers.get('content-type')}"


@pytest.mark.normal
def test_admin_web_upload_component_works(stack, tmp_path):
    """관리자 화면(브랜드 추가 화면의 로고 업로드)에서 그림을 올린다. 업로드 주소가 로그인을 요구해도 화면이 토큰을 실어야 한다."""
    f = tmp_path / "daon-logo.png"
    f.write_bytes(PNG)
    with _browser.admin_page("admin") as page:
        page.goto(_browser.ADMIN_WEB + "/#/pms/addBrand", wait_until="domcontentloaded")
        page.wait_for_selector("input[type=file]", state="attached", timeout=15000)
        with page.expect_response(lambda r: "/minio/upload" in r.url, timeout=15000) as resp:
            page.locator("input[type=file]").first.set_input_files(str(f))
        r = resp.value
        body = r.json() if r.status == 200 else {}
        page.wait_for_function("""() => {
            const image = document.querySelector('.el-upload-list__item img');
            return image && image.complete && image.naturalWidth > 0;
        }""", timeout=10000)
    assert r.status == 200 and body.get("code") == 200, f"기대: 관리자 화면 업로드 성공. 실제: HTTP {r.status} code={body.get('code')}"


def test_admin_web_upload_tip_matches_2mb_limit(stack):
    """화면 안내도 서버의 2MB 상한과 같다. 시작 지점(상한 없음)의 안내는 원본 그대로 10MB라 레드(정상 업로드 시험과 나눔)."""
    with _browser.admin_page("admin") as page:
        page.goto(_browser.ADMIN_WEB + "/#/pms/addBrand", wait_until="domcontentloaded")
        tip = page.locator(".el-upload__tip").first
        tip.wait_for(state="attached", timeout=15000)
        text = tip.inner_text()
    assert "2MB" in text, f"기대: 화면 안내도 MinIO의 2MB 상한과 같음. 실제: {text!r}"


def test_rejected_upload_is_logged(stack):
    since = _logs.now()
    tid = new_trace_id("s3-06")
    with t08.admin() as c:
        r = c.post("/minio/upload", files={"file": ("x.png", b"<svg onload=1>", "image/png")},
                   headers={"Authorization": _staff(), "X-Trace-Id": tid})
    e = _logs.wait_event("mall-admin", since, "upload.rejected.type", "3-6", stack, trace_id=tid)
    assert e, f"기대: 거절한 업로드는 mall-admin 로그에 SECURITY upload.rejected.type(3-6)으로 남아야 합니다(HTTP {r.status_code})"
    assert e.get("actor") == "admin:staff_goods" and "x.png" not in str(e), f"기대: actor admin:staff_goods, 파일 이름은 넣지 않음. 실제: {e}"


def test_t08_replay_green(stack):
    r = t08.run(stack)
    assert not r.vulnerable, "기대: 업로드 네 경우 모두 막힘\n" + r.show()
