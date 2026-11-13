"""T08 · 상품 사진 업로드 검사 부족(A05·A01). 수업 3-6. 원본 기능 그대로 쓴다([교육용 추가] 없음).

원본 관리자 앱의 /minio/upload와 MinIO 버킷 mall을 부른다(127.0.0.1만, 올리는 파일은 가짜 글자·가짜 그림).
  ① 로그인 없이 업로드 요청 → 받아지나
  ② 그림이 아닌 글자 파일을 그림 형식(.png, image/png)이라고 적어 올림 → 그대로 저장되고 공개 주소로 열리나, 이름이 올린 이름 그대로인가
  ③ 같은 이름으로 한 번 더 올림 → 앞 파일이 바뀌나(덮어쓰기)
  ④ 로그인 없이 버킷 목록 주소를 엶 → 객체 목록이 보이나
①이 막혀 있으면 ②③은 상품 담당 직원(staff_goods)으로 올린다(로그인한 직원이라도 그림이 아닌 파일은 받지 말아야 한다).
하나라도 보이면 레드.
"""
from __future__ import annotations

import uuid

import httpx

from daon.config import HOST, port
from replay import NotReady, Result
from replay._http import admin, admin_login

T = "T08"
TITLE = "상품 사진 업로드 검사 부족(로그인 없음·내용 확인 없음·덮어쓰기·버킷 목록 공개)"
LESSON = "3-6"
SOURCE = "[원본 기본값] 업로드·서명 주소 화이트리스트(F07·F08) + [원본 확인됨] 이름·형식을 요청에서 받음(F07) + [회사 설정] 버킷 목록 공개(F26)"
STACKS = ("spring",)

MINIO = f"http://{HOST}:{port('DAON_MINIO_PORT', 9000)}"
BUCKET = "mall"
UPLOADER = "staff_goods"
# 가장 작은 진짜 PNG(1x1)
PNG_1x1 = bytes.fromhex("89504e470d0a1a0a0000000d4948445200000001000000010806000000"
                        "1f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082")


def upload(data: bytes, filename: str, content_type: str = "image/png", token: str | None = None) -> tuple[int, dict]:
    headers = {"Authorization": token} if token else {}
    try:
        with admin() as c:
            r = c.post("/minio/upload", files={"file": (filename, data, content_type)}, headers=headers)
    except httpx.TransportError as e:
        raise NotReady(f"mall-admin에 닿지 않습니다. make up STACK=spring 먼저 실행하세요. ({e.__class__.__name__})")
    try:
        return r.status_code, r.json()
    except ValueError:
        return r.status_code, {}


def staff_token() -> str:
    with admin() as c:
        return admin_login(c, UPLOADER)


def public_url(url: str) -> str:
    """앱이 돌려준 주소(컨테이너 안 이름 minio:9000)를 이 PC 주소로."""
    path = url.split(f"/{BUCKET}/", 1)[1] if f"/{BUCKET}/" in url else url
    return f"{MINIO}/{BUCKET}/{path}"


def fetch(url: str) -> httpx.Response:
    return httpx.get(public_url(url), timeout=10.0)


def list_bucket() -> httpx.Response:
    return httpx.get(f"{MINIO}/{BUCKET}/", timeout=10.0)


def accepted(status: int, body: dict) -> bool:
    return status == 200 and body.get("code") == 200 and bool((body.get("data") or {}).get("url"))


def run(stack: str = "spring") -> Result:
    if stack not in STACKS:
        raise NotReady(f"{T} {stack} 재연은 아직 없습니다(Express는 diffs/s3-06/express.diff로 확인).")
    ref = uuid.uuid4().hex[:6]
    red, ev = [], []

    status, body = upload(PNG_1x1, f"daon-lab-{ref}.png")
    anon_ok = accepted(status, body)
    ev.append(f"① 로그인 없이 POST /minio/upload: HTTP {status} code={body.get('code')}" + (" → 받아짐" if anon_ok else ""))
    if anon_ok:
        red.append("① 로그인 없음")
    token = None if anon_ok else staff_token()
    who = "로그인 없이" if anon_ok else f"{UPLOADER}로"

    name = f"daon-lab-note-{ref}.png"
    text1 = f"교육용 랩 가짜 글자 파일 {ref} (그림 아님)".encode()
    status, body = upload(text1, name, "image/png", token)
    url1 = (body.get("data") or {}).get("url") if accepted(status, body) else None
    if url1:
        r = fetch(url1)
        same_name = url1.endswith("/" + name)
        ev.append(f"② {who} 글자 파일을 그림이라고 적어 올림: HTTP {status} → 공개 주소 HTTP {r.status_code} "
                  f"Content-Type={r.headers.get('content-type')} · 저장 이름이 올린 이름 그대로={same_name}")
        if r.status_code == 200 and r.content == text1:
            red.append("② 내용 확인 없음")
        text2 = f"교육용 랩 가짜 글자 파일 {ref} 두 번째".encode()
        status2, body2 = upload(text2, name, "image/png", token)
        url2 = (body2.get("data") or {}).get("url") if accepted(status2, body2) else None
        now = fetch(url1)
        overwritten = url2 == url1 and now.content == text2
        ev.append(f"③ 같은 이름으로 한 번 더: HTTP {status2} → 앞 파일이 {'바뀜(덮어쓰기)' if overwritten else '그대로'}")
        if overwritten:
            red.append("③ 덮어쓰기")
    else:
        ev.append(f"② {who} 글자 파일을 그림이라고 적어 올림: HTTP {status} code={body.get('code')} message={body.get('message')!r} → 거절")
        ev.append("③ 같은 이름으로 다시 올리기: ②가 거절되어 해당 없음")

    r = list_bucket()
    listed = r.status_code == 200 and "<ListBucketResult" in r.text
    keys = r.text.count("<Key>") if listed else 0
    ev.append(f"④ 로그인 없이 버킷 목록 {MINIO}/{BUCKET}/: HTTP {r.status_code}" + (f" → 객체 {keys}개 이름이 보임" if listed else ""))
    if listed:
        red.append("④ 버킷 목록 공개")

    if red:
        return Result(True, f"업로드·저장 위치 검사가 부족하다({', '.join(red)})", ev)
    return Result(False, "업로드는 로그인한 직원의 그림만 받고, 이름은 서버가 정하고, 목록은 닫혀 있다", ev)
