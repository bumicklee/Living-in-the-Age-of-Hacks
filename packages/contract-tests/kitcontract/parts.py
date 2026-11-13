"""부품 24개의 계약(CONTRACT.md 1절). 함수 하나 = 부품 하나. 실패하면 AssertionError(한국어 이유).

부품 예제 앱(spring-kit-demo·express-kit-demo)에 똑같이 돈다. 회사 서비스에 붙인 부품을 같은 함수로 확인해도 된다.
안전 기준: 127.0.0.1의 예제 앱만 부른다. SSRF 확인은 내부 주소로 「나가지 않는지」만 본다(밖으로 나가는 요청을 만들지 않는다).
"""
from __future__ import annotations

import hashlib
import hmac
import json
import statistics
import time
import uuid

from kitcontract.client import ADMIN, GENERIC_ERROR, LOGIN_FAILED, PNG, USER, Kit, alg_none, jwt_parts, trace

WEBHOOK_SECRET = b"kit-lab-fake-webhook-secret"


def p01_safe_http_client(k: Kit) -> None:
    s = k.seq()
    for url in ("http://127.0.0.1:1/x", "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://10.0.0.5/"):
        r = k.get("/kit/fetch", params={"url": url})
        assert r.status_code == 403, f"내부 주소 {url} → 403 기대, 실제 {r.status_code}"
    assert k.wait_event(s, "egress.denied.internal_address"), "egress.denied.internal_address 이벤트가 없다"
    for url in ("file:///etc/passwd", "gopher://x/"):
        r = k.get("/kit/fetch", params={"url": url})
        assert r.status_code == 400, f"스킴 {url} → 400 기대, 실제 {r.status_code}"
    assert k.wait_event(s, "egress.denied.scheme"), "egress.denied.scheme 이벤트가 없다"
    r = k.get("/kit/fetch", params={"url": "http://"})
    assert r.status_code == 400, f"형식이 틀린 주소 → 400 기대, 실제 {r.status_code}"


def p02_host_allowlist(k: Kit) -> None:
    s = k.seq()
    r = k.get("/kit/public", headers={"Host": "rebind.evil.example"})
    assert r.status_code == 400, f"허용 밖 Host → 400 기대, 실제 {r.status_code}"
    assert k.wait_event(s, "request.denied.host"), "request.denied.host 이벤트가 없다"
    assert k.get("/kit/public", headers={"Host": "shop.example"}).status_code == 200, "허용한 Host(shop.example)는 200"


def p03_safe_redirect(k: Kit) -> None:
    s = k.seq()
    ok = {"/orders/7": "/orders/7", "https://shop.example/cart": "https://shop.example/cart"}
    bad = ["//evil.example", "https://evil.example/x", "/\\evil.example", "javascript:alert(1)"]
    for nxt, want in ok.items():
        r = k.get("/kit/redirect", params={"next": nxt})
        assert r.status_code == 302 and r.headers.get("location") == want, f"next={nxt} → Location {want} 기대, 실제 {r.status_code} {r.headers.get('location')}"
    for nxt in bad:
        r = k.get("/kit/redirect", params={"next": nxt})
        assert r.status_code == 302 and r.headers.get("location") == "/", f"next={nxt} → Location / 기대, 실제 {r.headers.get('location')}"
    assert k.wait_event(s, "redirect.denied.not_allowed"), "redirect.denied.not_allowed 이벤트가 없다"


def p04_upload(k: Kit) -> None:
    import re
    s = k.seq()
    h = {"Content-Type": "application/octet-stream"}
    r = k.post("/kit/upload", content=PNG, headers=h | {"X-File-Name": "../../evil.php.png"})
    assert r.status_code == 201, f"PNG → 201 기대, 실제 {r.status_code} {r.text[:120]}"
    body = r.json()
    assert re.fullmatch(r"[0-9a-f]{32}\.png", body.get("stored", "")) and body.get("type") == "image/png", f"저장 이름은 무작위 32자 + 내용에서 정한 확장자. 실제 {body}"
    r = k.post("/kit/upload", content=b"<svg onload=alert(1)></svg>", headers=h | {"X-File-Name": "a.png"})
    assert r.status_code == 415, f"이미지가 아닌 내용 → 415 기대, 실제 {r.status_code}"
    assert k.wait_event(s, "upload.rejected.type"), "upload.rejected.type 이벤트가 없다"
    big = PNG + b"\x00" * (2 * 1024 * 1024 + 1 - len(PNG))
    r = k.post("/kit/upload", content=big, headers=h | {"X-File-Name": "big.png"})
    assert r.status_code == 413, f"2MB 초과 → 413 기대, 실제 {r.status_code}"
    assert k.wait_event(s, "upload.rejected.size"), "upload.rejected.size 이벤트가 없다"


def p05_headers(k: Kit) -> None:
    r = k.get("/kit/public")
    h = r.headers
    assert h.get("x-content-type-options") == "nosniff", "X-Content-Type-Options: nosniff"
    assert h.get("x-frame-options") == "DENY", "X-Frame-Options: DENY"
    assert h.get("referrer-policy") == "no-referrer", "Referrer-Policy: no-referrer"
    csp = h.get("content-security-policy", "")
    assert "default-src 'self'" in csp and "frame-ancestors 'none'" in csp, f"CSP에 default-src 'self'·frame-ancestors 'none'. 실제 {csp!r}"
    assert "x-powered-by" not in h, "X-Powered-By가 없어야 한다"
    s = k.seq()
    good = k.get("/kit/public", headers={"Origin": "http://shop.example"})
    assert good.headers.get("access-control-allow-origin") == "http://shop.example", "허용 출처는 ACAO에 그대로"
    assert "origin" in good.headers.get("vary", "").lower(), "Vary: Origin"
    evil = k.get("/kit/public", headers={"Origin": "https://evil.example"})
    assert evil.status_code == 200 and "access-control-allow-origin" not in evil.headers, "허용 밖 출처에는 ACAO가 없다"
    assert k.wait_event(s, "cors.denied_origin"), "cors.denied_origin 이벤트가 없다"


def p06_rate_limit(k: Kit) -> None:
    s = k.seq()
    for i in range(5):
        r = k.post("/kit/login", json={"username": USER[0], "password": f"wrong-{i}"})
        assert r.status_code == 401, f"틀린 비밀번호 {i + 1}번째 → 401 기대, 실제 {r.status_code}"
    r = k.post("/kit/login", json={"username": USER[0], "password": USER[1]})
    assert r.status_code == 429 and r.headers.get("retry-after"), f"6번째(맞는 비밀번호라도) → 429 + Retry-After 기대, 실제 {r.status_code}"
    assert k.wait_event(s, "auth.login.rate_limited", target=f"account:{USER[0]}"), "auth.login.rate_limited(target account:kit-user) 이벤트가 없다"


def p07_password_policy(k: Kit) -> None:
    s = k.seq()
    r = k.post("/kit/password/check", json={"password": "short-1"})
    assert r.status_code == 422 and r.json().get("reason") == "too_short", f"10자 미만 → 422 too_short. 실제 {r.status_code} {r.text[:80]}"
    r = k.post("/kit/password/check", json={"password": "qwerty123456"})
    assert r.status_code == 422 and r.json().get("reason") == "breached", f"유출 목록 → 422 breached. 실제 {r.status_code} {r.text[:80]}"
    assert k.wait_event(s, "auth.password.rejected_breached"), "auth.password.rejected_breached 이벤트가 없다"
    r = k.post("/kit/password/check", json={"password": "correct-horse-battery-7"})
    assert r.status_code == 200 and r.json().get("ok") is True, "규칙을 지킨 비밀번호는 200 ok"


def p08_constant_time_login(k: Kit) -> None:
    def timed(user: str) -> tuple[float, int, str]:
        t0 = time.perf_counter()
        r = k.post("/kit/login", json={"username": user, "password": "not-the-password-9"})
        return time.perf_counter() - t0, r.status_code, r.json().get("message", "")
    known = [timed(USER[0]) for _ in range(3)]
    k.reset()
    unknown = [timed(f"nobody-{uuid.uuid4().hex[:8]}") for _ in range(3)]
    assert {x[1] for x in known + unknown} == {401}, f"둘 다 401 기대. 실제 {[x[1] for x in known + unknown]}"
    assert {x[2] for x in known + unknown} == {LOGIN_FAILED}, f"문구가 같아야 한다. 실제 {set(x[2] for x in known + unknown)}"
    mk, mu = statistics.median(x[0] for x in known), statistics.median(x[0] for x in unknown)
    assert mu >= 0.5 * mk, f"없는 아이디도 같은 비용의 해시 비교: 있는 아이디 {mk * 1000:.0f}ms · 없는 아이디 {mu * 1000:.0f}ms"


def p09_reset_link(k: Kit) -> None:
    s = k.seq()
    r = k.post("/kit/reset/request", json={"username": USER[0]}, headers={"X-Forwarded-Host": "evil.example"})
    assert r.status_code == 202, f"재설정 요청 → 202 기대, 실제 {r.status_code}"
    unknown = k.post("/kit/reset/request", json={"username": "nobody-x"})
    assert unknown.status_code == 202 and unknown.json() == r.json(), "없는 아이디도 같은 응답"
    mails = k.get("/_kit/outbox").json()["mails"]
    assert mails, "재설정 메일이 없다"
    body = mails[-1]["body"]
    assert "http://shop.example/reset?token=" in body and "evil.example" not in body, f"링크는 설정한 base-url로만. 실제 {body[:160]}"
    assert k.wait_event(s, "auth.reset.host_mismatch"), "auth.reset.host_mismatch 이벤트가 없다"
    token = body.split("token=", 1)[1].split()[0].strip()
    ok = k.post("/kit/reset/confirm", json={"token": token, "newPassword": "new-kit-pass-2!"})
    assert ok.status_code == 200, f"처음 쓰는 토큰 → 200 기대, 실제 {ok.status_code}"
    again = k.post("/kit/reset/confirm", json={"token": token, "newPassword": "new-kit-pass-3!"})
    assert again.status_code == 400, f"이미 쓴 토큰 → 400 기대, 실제 {again.status_code}"
    assert k.wait_event(s, "auth.reset.token_reused"), "auth.reset.token_reused 이벤트가 없다"


def p10_step_up(k: Kit) -> None:
    s = k.seq()
    h = Kit.bearer(k.login())
    r = k.post("/kit/account/email", json={"email": "kit-user@shop.example"}, headers=h)
    assert r.status_code == 401 and r.json().get("stepUp") == "required", f"재인증 없음 → 401 required. 실제 {r.status_code} {r.text[:80]}"
    r = k.post("/kit/account/email", json={"email": "kit-user@shop.example"}, headers=h | {"X-Reauth-Password": "wrong-pass-0"})
    assert r.status_code == 401 and r.json().get("stepUp") == "failed", f"틀린 재인증 → 401 failed. 실제 {r.status_code}"
    r = k.post("/kit/account/email", json={"email": "kit-user@shop.example"}, headers=h | {"X-Reauth-Password": USER[1]})
    assert r.status_code == 200, f"맞는 재인증 → 200. 실제 {r.status_code}"
    for name in ("auth.stepup.required", "auth.stepup.failed", "account.change.high_risk"):
        assert k.wait_event(s, name), f"{name} 이벤트가 없다"


def p11_token(k: Kit) -> None:
    s = k.seq()
    token = k.login()
    head, body = jwt_parts(token)
    assert head.get("alg") == "HS256" and head.get("kid"), f"머리 alg HS256·kid. 실제 {head}"
    assert body.get("exp") and body.get("iat") and body["exp"] - body["iat"] <= 900 and body.get("jti"), f"exp-iat ≤ 900, jti. 실제 {body}"
    me = k.get("/kit/me", headers=Kit.bearer(token))
    assert me.status_code == 200 and me.json().get("sub") == USER[0], f"/kit/me 200. 실제 {me.status_code}"
    assert k.get("/kit/me", headers=Kit.bearer(alg_none(token))).status_code == 401, "alg=none 토큰은 401"
    assert k.wait_event(s, "auth.token.denied_alg"), "auth.token.denied_alg 이벤트가 없다"
    assert k.post("/kit/logout", headers=Kit.bearer(token)).status_code == 204, "로그아웃 204"
    assert k.get("/kit/me", headers=Kit.bearer(token)).status_code == 401, "로그아웃한 토큰은 401"
    assert k.wait_event(s, "auth.token.revoked_used"), "auth.token.revoked_used 이벤트가 없다"


def p12_field_crypto(k: Kit) -> None:
    h = Kit.bearer(k.login())
    phone = "010-0000-3333"
    ids = []
    for _ in range(2):
        r = k.post("/kit/pii", json={"phone": phone}, headers=h)
        assert r.status_code == 201, f"저장 201 기대, 실제 {r.status_code}"
        ids.append(r.json()["id"])
    raws = [k.get(f"/_kit/pii/raw/{i}").json()["raw"] for i in ids]
    for raw in raws:
        assert raw.startswith("enc:1:") and "01000003333" not in raw and "0000-3333" not in raw, f"저장 값은 enc:1:로 시작하는 암호문. 실제 {raw[:40]}"
    assert raws[0] != raws[1], "같은 번호도 암호문이 달라야 한다(논스)"
    got = k.get(f"/kit/pii/{ids[0]}", headers=h).json()
    assert got.get("phone") == "010-****-3333", f"화면 값은 가린 번호. 실제 {got}"
    found = k.get("/kit/pii/search", params={"phone": phone}, headers=h).json().get("ids", [])
    assert set(ids) <= set(found), f"검색용 해시로 두 id를 찾는다. 실제 {found}"


def p13_startup_check(k: Kit) -> None:
    props = {"secure-kit.token.secret": "changeme", "spring.datasource.password": "root", "app.name": "shop", "app.api-key": "k-7f3a9c2e1b4d8a60"}
    r = k.post("/_kit/startup/check", json=props)
    assert r.status_code == 200, f"/_kit/startup/check 200 기대, 실제 {r.status_code}"
    body = r.json()
    assert body.get("ok") is False and set(body.get("denied", [])) == {"secure-kit.token.secret", "spring.datasource.password"}, f"기본값 둘만 거부. 실제 {body}"
    assert "changeme" not in r.text and "root\"" not in r.text, "응답에 값을 돌려주지 않는다"


def p14_error_response(k: Kit) -> None:
    tid = trace()
    r = k.get("/kit/error", headers={"X-Trace-Id": tid})
    assert r.status_code == 500, f"500 기대, 실제 {r.status_code}"
    body = r.json()
    assert body.get("code") == 500 and body.get("message") == GENERIC_ERROR and body.get("traceId") == tid == r.headers.get("x-trace-id"), f"일반 문구 + 추적 번호. 실제 {body}"
    for word in ("Exception", "oms_order", "SQL", "at "):
        assert word not in r.text, f"응답에 내부 문구 {word!r}가 나간다"


def p15_audit(k: Kit) -> None:
    h = Kit.bearer(k.login(ADMIN))
    tid = trace()
    r = k.get("/kit/admin/members", params={"pageNum": 1, "pageSize": 20}, headers=h | {"X-Access-Reason": "CS-0001", "X-Trace-Id": tid})
    assert r.status_code == 200 and len(r.json().get("list", [])) == 20, f"관리자 회원 목록 20명. 실제 {r.status_code}"
    assert all(str(m.get("phone", "")).startswith("010-****-") for m in r.json()["list"]), "목록의 전화번호는 가린 값"
    rec = next((x for x in reversed(k.audit()) if x.get("trace_id") == tid), None)
    assert rec, "감사 기록이 없다"
    assert set(rec) == {"ts", "trace_id", "actor", "action", "target", "count", "reason", "result"}, f"감사 기록은 8개 키. 실제 {sorted(rec)}"
    want = {"actor": "admin:kit-admin", "action": "member.list", "target": "member:list?page=1&size=20", "count": 20, "reason": "CS-0001", "result": "ok"}
    assert {k2: rec[k2] for k2 in want} == want, f"감사 기록 값. 실제 {rec}"
    assert "010-" not in json.dumps(rec, ensure_ascii=False), "감사 기록에 전화번호가 없다"
    tid2 = trace()
    d = k.get("/kit/admin/members", params={"pageNum": 1, "pageSize": 20}, headers=Kit.bearer(k.login()) | {"X-Trace-Id": tid2})
    assert d.status_code == 403, f"일반 사용자 → 403. 실제 {d.status_code}"
    rec2 = next((x for x in reversed(k.audit()) if x.get("trace_id") == tid2), None)
    assert rec2 and rec2["result"] == "denied" and rec2["count"] == 0 and rec2["actor"] == "user:kit-user", f"거부도 기록(result denied). 실제 {rec2}"


def p16_log_masking(k: Kit) -> None:
    token = k.login()
    secret_pw = f"p@ss-word-{uuid.uuid4().hex[:4]}"
    r = k.post("/kit/log", json={"phone": "010-0000-4444", "password": secret_pw, "token": token})
    assert r.status_code == 204, f"204 기대, 실제 {r.status_code}"
    lines = "\n".join(k.get("/_kit/logs").json()["lines"][-50:])
    assert "010-****-4444" in lines, "가린 번호가 로그에 있어야 한다(줄이 찍혔는지)"
    assert "010-0000-4444" not in lines and secret_pw not in lines and token not in lines, "로그에 번호·비밀번호·토큰 원문이 남았다"


def p17_limits(k: Kit) -> None:
    s = k.seq()
    r = k.post("/kit/echo", content=b"a" * (1024 * 1024 + 1), headers={"Content-Type": "application/octet-stream"})
    assert r.status_code == 413, f"1MB 초과 본문 → 413 기대, 실제 {r.status_code}"
    assert k.wait_event(s, "input.denied.too_large"), "input.denied.too_large 이벤트가 없다"
    small = k.post("/kit/echo", content=b"hello", headers={"Content-Type": "application/octet-stream"})
    assert small.status_code == 200 and small.json().get("bytes") == 5, "작은 본문은 200"
    t0 = time.perf_counter()
    long = k.get("/kit/coupon", params={"code": "A" * 5000 + "!"})
    assert long.status_code == 400 and time.perf_counter() - t0 < 1.0, f"긴 코드 → 1초 안에 400. 실제 {long.status_code}"
    assert k.wait_event(s, "input.denied.too_long"), "input.denied.too_long 이벤트가 없다"
    assert k.get("/kit/coupon", params={"code": "DAON-2026"}).json().get("valid") is True, "DAON-2026은 맞는 형식"
    assert k.get("/kit/coupon", params={"code": "daon_2026"}).json().get("valid") is False, "형식 밖은 valid false"


def p18_fail_closed(k: Kit) -> None:
    s = k.seq()
    assert k.get("/kit/failclosed", params={"dep": "up"}).status_code == 200, "의존성 정상 → 200"
    r = k.get("/kit/failclosed", params={"dep": "down"})
    assert r.status_code == 503, f"의존성 오류 → 503(통과시키지 않음) 기대, 실제 {r.status_code}"
    assert k.wait_event(s, "auth.fail_closed.dependency_error"), "auth.fail_closed.dependency_error 이벤트가 없다"


def p19_resilient_client(k: Kit) -> None:
    s = k.seq()
    k.post("/_kit/flaky/reset")
    last = None
    for _ in range(12):
        last = k.get("/kit/resilient")
        assert last.status_code in (502, 503), f"의존성 실패 → 502/503. 실제 {last.status_code}"
    hits = k.get("/_kit/flaky/count").json()["count"]
    assert hits <= 15, f"재시도 폭주 없음: 12번 요청에 의존성 호출 {hits}번(15 이하 기대)"
    assert last.status_code == 503 and last.headers.get("x-circuit") == "open", f"마지막은 서킷 열림 503. 실제 {last.status_code} {last.headers.get('x-circuit')}"
    assert k.wait_event(s, "resilience.circuit.opened"), "resilience.circuit.opened 이벤트가 없다"


def p20_idempotency(k: Kit) -> None:
    before = k.get("/_kit/refund/count").json()["count"]
    key = f"k-{uuid.uuid4().hex}"
    body = {"orderId": 7, "amount": 1000}
    a = k.post("/kit/refund", json=body, headers={"Idempotency-Key": key})
    b = k.post("/kit/refund", json=body, headers={"Idempotency-Key": key})
    assert a.status_code == b.status_code == 200 and a.json() == b.json() and a.json().get("refundId"), f"같은 키 두 번 → 같은 응답. 실제 {a.text[:80]} / {b.text[:80]}"
    assert k.get("/_kit/refund/count").json()["count"] == before + 1, "처리는 한 번만"
    assert k.post("/kit/refund", json=body).status_code == 400, "키 없음 → 400"
    assert k.post("/kit/refund", json={"orderId": 7, "amount": 2000}, headers={"Idempotency-Key": key}).status_code == 422, "같은 키 다른 본문 → 422"
    s = k.seq()
    wh_before = k.get("/_kit/webhook/count").json()["count"]
    raw = json.dumps({"eventId": f"ev-{uuid.uuid4().hex[:8]}", "type": "payment.approved", "amount": 1000}).encode()

    def signed(ts: int, secret: bytes = WEBHOOK_SECRET) -> dict:
        mac = hmac.new(secret, f"{ts}.".encode() + raw, hashlib.sha256).hexdigest()
        return {"Content-Type": "application/json", "X-Timestamp": str(ts), "X-Signature": f"sha256={mac}"}
    now = int(time.time())
    first = k.post("/kit/webhook", content=raw, headers=signed(now))
    assert first.status_code == 200 and first.json().get("processed") is True, f"서명 맞는 웹훅 → processed. 실제 {first.status_code} {first.text[:80]}"
    dup = k.post("/kit/webhook", content=raw, headers=signed(now))
    assert dup.status_code == 200 and dup.json().get("duplicate") is True, "같은 eventId → duplicate"
    assert k.get("/_kit/webhook/count").json()["count"] == wh_before + 1, "웹훅 처리는 한 번만"
    assert k.post("/kit/webhook", content=raw, headers=signed(now, b"wrong-secret")).status_code == 401, "서명 틀림 → 401"
    assert k.post("/kit/webhook", content=raw, headers=signed(now - 3600)).status_code == 401, "1시간 전 시각 → 401"
    assert k.wait_event(s, "payment.denied.bad_signature") and k.wait_event(s, "payment.duplicate.event"), "웹훅 이벤트 둘(bad_signature·duplicate)"


def p21_budget(k: Kit) -> None:
    s = k.seq()
    phone = f"010-0000-{uuid.uuid4().int % 10000:04d}"
    assert k.post("/kit/sms", json={"phone": phone}).status_code == 202, "처음 발송 202"
    r = k.post("/kit/sms", json={"phone": phone})
    assert r.status_code == 429, f"60초 안 다시 → 429. 실제 {r.status_code}"
    e = k.wait_event(s, "budget.denied.sms")
    assert e and e.get("target") == "phone:" + hashlib.sha256(phone.encode()).hexdigest()[:8], f"target phone:<sha256 앞 8자>. 실제 {e}"
    assert phone not in json.dumps(e, ensure_ascii=False), "이벤트에 번호 원문이 없다"


def p22_no_store(k: Kit) -> None:
    me = k.get("/kit/me", headers=Kit.bearer(k.login()))
    assert "no-store" in me.headers.get("cache-control", ""), f"로그인한 응답은 no-store. 실제 {me.headers.get('cache-control')}"
    pub = k.get("/kit/public")
    cc = pub.headers.get("cache-control", "")
    assert "no-store" not in cc and "public" in cc, f"공개 응답은 public. 실제 {cc}"


def p23_partner_scope(k: Kit) -> None:
    s = k.seq()
    t = k.post("/_kit/partner/token", json={"app": "courier-01", "scopes": ["orders:read"]}).json()["token"]
    assert k.get("/kit/partner/orders", headers=Kit.bearer(t)).status_code == 200, "범위 안 → 200"
    r = k.get("/kit/partner/export", headers=Kit.bearer(t))
    assert r.status_code == 403, f"범위 밖 → 403. 실제 {r.status_code}"
    assert k.wait_event(s, "partner.denied.scope", actor="partner:courier-01"), "partner.denied.scope(actor partner:courier-01)"
    assert k.get("/kit/partner/orders", headers=Kit.bearer(k.login())).status_code == 401, "사람 토큰(aud kit) → 401"


def p24_approval(k: Kit) -> None:
    s = k.seq()
    h = Kit.bearer(k.login(ADMIN))
    small = k.post("/kit/refund/high", json={"orderId": 7, "amount": 10000}, headers=h)
    assert small.status_code == 200 and small.json().get("status") == "DONE", f"기준 미만 → 200 DONE. 실제 {small.status_code}"
    body = {"orderId": 8, "amount": 500000}
    p = k.post("/kit/refund/high", json=body, headers=h)
    assert p.status_code == 202 and p.json().get("status") == "PENDING_APPROVAL", f"고액 → 202 PENDING_APPROVAL. 실제 {p.status_code} {p.text[:80]}"
    aid = p.json()["approvalId"]
    assert k.wait_event(s, "approval.pending.second_approver"), "approval.pending.second_approver 이벤트가 없다"
    assert k.post("/kit/refund/high", json=body, headers=h | {"X-Approval-Id": aid}).status_code == 403, "승인 전 → 403"
    assert k.post(f"/_kit/approvals/{aid}/approve", json={"approver": "kit-admin"}).status_code == 403, "요청자 본인 승인 → 403"
    assert k.wait_event(s, "approval.denied.self_approval"), "approval.denied.self_approval 이벤트가 없다"
    assert k.post(f"/_kit/approvals/{aid}/approve", json={"approver": "fin-lee"}).json().get("status") == "APPROVED", "다른 사람 승인 → APPROVED"
    assert k.post("/kit/refund/high", json={"orderId": 8, "amount": 900000}, headers=h | {"X-Approval-Id": aid}).status_code == 403, "승인 때와 다른 금액 → 403"
    done = k.post("/kit/refund/high", json=body, headers=h | {"X-Approval-Id": aid})
    assert done.status_code == 200 and done.json().get("status") == "DONE", f"승인 뒤 → 200 DONE. 실제 {done.status_code}"
    assert k.post("/kit/refund/high", json=body, headers=h | {"X-Approval-Id": aid}).status_code == 409, "같은 승인 번호 다시 → 409"


PARTS = {
    "safe-http-client": p01_safe_http_client, "host-allowlist": p02_host_allowlist, "safe-redirect": p03_safe_redirect,
    "upload": p04_upload, "headers": p05_headers, "rate-limit": p06_rate_limit, "password-policy": p07_password_policy,
    "constant-time-login": p08_constant_time_login, "reset-link": p09_reset_link, "step-up": p10_step_up, "token": p11_token,
    "field-crypto": p12_field_crypto, "startup-check": p13_startup_check, "error-response": p14_error_response, "audit": p15_audit,
    "log-masking": p16_log_masking, "limits": p17_limits, "fail-closed": p18_fail_closed, "resilient-client": p19_resilient_client,
    "idempotency": p20_idempotency, "budget": p21_budget, "no-store": p22_no_store, "partner-scope": p23_partner_scope,
    "approval": p24_approval,
}
