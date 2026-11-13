# secure-kit 계약(두 스택 공통) · v1 (2026-11-10)

`packages/contract-tests/`의 시험이 이 문서를 그대로 확인한다. Spring 예제와 Express 예제는 **같은 주소·같은 응답 모양·같은 이벤트 이름**을 지켜야 한다.
모든 값은 랩 전용 가짜 값이다. 예제 앱은 127.0.0.1에만 연다.

| 앱 | 폴더 | 주소(기본) | 환경 변수 |
|---|---|---|---|
| Spring 부품 예제(부품 24개 전부) | `packages/examples/spring-kit-demo` | http://127.0.0.1:18181 | `KIT_SPRING_DEMO` |
| Express 부품 예제(부품 24개 전부) | `packages/examples/express-kit-demo` | http://127.0.0.1:18182 | `KIT_EXPRESS_DEMO` |
| Spring 가게 예제(15-4 수업, 부품 셋을 붙이기 전/후) | `packages/examples/spring-shop` | http://127.0.0.1:18183 | `KIT_SPRING_SHOP` |
| Express 가게 예제(15-4 수업) | `packages/examples/express-shop` | http://127.0.0.1:18184 | `KIT_EXPRESS_SHOP` |

## 0. 공통

- 설정 키는 두 스택이 같다(`application.yml`의 `secure-kit.*` = Express 설정 객체의 같은 키, kebab-case 그대로 문자열 키).
- JSON 응답. 오류 응답은 `{"code": <HTTP 상태>, "message": "<한국어 일반 문구>", "traceId": "<추적 번호>"}`(부품이 막은 경우도 같다).
- 모든 응답에 `X-Trace-Id`(요청의 `X-Trace-Id`가 `[A-Za-z0-9._-]{8,64}`이면 그 값, 아니면 새로 만든 값).
- **막으면 남긴다**: 부품이 막으면 보안 이벤트 한 줄. 모양(키 고정):
  `{"seq": <1부터 늘어나는 정수>, "event": "<이름>", "part": "<부품 키>", "level": "WARN|INFO", "actor": "<user:아이디|admin:아이디|partner:앱|anonymous>", "target": "<ID만>", "reason": "<설명, 값 없음>", "ts": "<ISO-8601>", "trace_id": "<추적 번호>"}`
  로그에는 `WARN SECURITY {json}` 한 줄로 찍는다(8장 수집기가 그대로 읽는 모양). 값(비밀번호·토큰·전화번호 원문)은 넣지 않는다.
- **실패하면 막는다**: 부품 내부 오류(저장소 연결 실패 등)는 막는 쪽(기본). 풀려면 설정에 `fail-open: true`를 명시.
- 데모 사용자(설정에 PBKDF2 해시로 둔다. 비밀번호는 가짜):
  - `kit-user` / `kit-pass-1!` (역할 `user`)
  - `kit-admin` / `kit-admin-pass-1!` (역할 `admin`)
- 토큰: `POST /kit/login` → `{"token": "<JWT>", "expiresIn": <초>}`. JWT 머리 `{"alg":"HS256","typ":"JWT","kid":"k1"}`, 내용 `sub`·`aud`(`kit`)·`iat`·`auth_time`·`exp`(iat + 900 이하)·`jti`·`roles`(배열). 요청은 `Authorization: Bearer <JWT>`.
  비밀값은 설정 `secure-kit.token.secret`(예제 값: `kit-lab-fake-token-secret-not-for-prod-32`).

### 0.1 랩 전용 엔드포인트(`/_kit/*`, 설정 `secure-kit.lab.expose: true`일 때만. 회사 서비스에서는 끈다)

| 주소 | 응답 |
|---|---|
| `POST /_kit/reset` | 204. 메모리 상태(속도 제한·예산·서킷·멱등·승인·발송함·재설정 토큰·데모 사용자 비밀번호)를 처음으로. 이벤트·감사 기록은 지우지 않는다 |
| `GET /_kit/events?after=<seq>` | `{"seq": <가장 큰 seq, 없으면 0>, "events": [after보다 큰 seq의 이벤트, 최근 500개까지]}` |
| `GET /_kit/audit` | `{"records": [최근 200개]}`. 기록 하나는 **정확히 8개 키** `ts trace_id actor action target count reason result` |
| `GET /_kit/logs` | `{"lines": ["…"]}` 최근 200줄(가리기를 거친 뒤 실제로 쓴 줄) |
| `GET /_kit/outbox` | `{"mails": [{"to": "…", "subject": "…", "body": "…"}]}` 최근 50통(재설정 메일) |
| `POST /_kit/startup/check` | 본문 `{설정 키: 값}` → `{"ok": bool, "denied": [키…]}`(기동 검사기와 같은 판단. 값은 돌려주지 않는다) |
| `GET /_kit/pii/raw/{id}` | `{"raw": "<저장소에 실제로 있는 값>"}` |
| `GET /_kit/flaky` | 늘 503(서킷 시험용 의존성). `GET /_kit/flaky/count` → `{"count": n}`, `POST /_kit/flaky/reset` → 204 |
| `GET /_kit/refund/count` | `{"count": n}` 실제로 처리한 환불 수 |
| `GET /_kit/webhook/count` | `{"count": n}` 실제로 처리한 웹훅 수 |
| `POST /_kit/partner/token` | 본문 `{"app": "courier-01", "scopes": ["orders:read"]}` → `{"token": "<JWT aud=partner, sub=앱, scope=공백 구분>"}` |
| `POST /_kit/approvals/{id}/approve` | 본문 `{"approver": "<아이디>"}` → 200 `{"status": "APPROVED"}` · 요청자 본인 → 403 + `approval.denied.self_approval` · 없는 번호 404 |

## 1. 부품 24개(키 · 넣는 법 · 주소 · 기대)

| # | 키 | 넣는 법 | Spring | Express |
|---|---|---|---|---|
| 1 | `safe-http-client` | ③ 바꿔 쓰기 | `SafeHttpClient` 빈 | `kit.safeFetch()` |
| 2 | `host-allowlist` | ① 설정 | `secure-kit.host-allowlist` | `kit.hostAllowlist()` |
| 3 | `safe-redirect` | ③ | `SafeRedirect` | `kit.safeRedirect()` |
| 4 | `upload` | ③ | `UploadValidator` | `kit.upload()` |
| 5 | `headers` | ① | `secure-kit.headers`·`secure-kit.cors` | `kit.headers()` |
| 6 | `rate-limit` | ①+② | `@RateLimited("login")` | `kit.rateLimit('login')` |
| 7 | `password-policy` | ③ | `PasswordPolicy` | `kit.passwordPolicy()` |
| 8 | `constant-time-login` | ③ | `ConstantTimeLogin` | `kit.constantTimeLogin()` |
| 9 | `reset-link` | ③ | `ResetLinkBuilder` | `kit.resetLink()` |
| 10 | `step-up` | ② | `@RequireStepUp` | `kit.requireStepUp()` |
| 11 | `token` | ① | `secure-kit.token` | `kit.token()` |
| 12 | `field-crypto` | ② | `@Encrypted`(변환기) | `kit.fieldCrypto()` |
| 13 | `startup-check` | ① | 기동 검사기(EnvironmentPostProcessor) | `kit.startupCheck()` |
| 14 | `error-response` | ① | `secure-kit.error-response` | `kit.errorResponse()` |
| 15 | `audit` | ② | `@Audited` | `kit.audited()` |
| 16 | `log-masking` | ① | Logback 변환기 | `kit.logger()`(가리기 포함) |
| 17 | `limits` | ①+③ | `secure-kit.limits`·`SafeRegex` | `kit.bodyLimit()`·`kit.safeRegex()` |
| 18 | `fail-closed` | ② | `@FailClosed` | `kit.failClosed()` |
| 19 | `resilient-client` | ① | 재시도 예산·서킷 기본값(`ResilientClient`) | `kit.resilientFetch()` |
| 20 | `idempotency` | ② | `@Idempotent`·`WebhookVerifier` | `kit.idempotent()`·`kit.webhook()` |
| 21 | `budget` | ② | `@Budget` | `kit.budget()` |
| 22 | `no-store` | ① | `secure-kit.cache-control` | `kit.noStoreAuthenticated()` |
| 23 | `partner-scope` | ② | `@PartnerScope` | `kit.partnerScope()` |
| 24 | `approval` | ② | `@RequiresApproval` | `kit.requiresApproval()` |

각 부품의 HTTP 계약(부품 예제 앱, 시험 이름은 `test_parts.py::test_<번호>_<키>`):

1. **safe-http-client** — `GET /kit/fetch?url=<주소>`: 서버가 안전한 HTTP 클라이언트로 대신 부른다(이름을 풀어 **모든** 주소를 확인하고, 확인한 주소로 붙는다. 리다이렉트는 따라가지 않는다).
   `http://127.0.0.1:1/x`·`http://169.254.169.254/latest/meta-data`·`http://[::1]/`·`http://10.0.0.5/` → 403 + `egress.denied.internal_address`(target `host:<이름>`).
   `file:///etc/passwd`·`gopher://x` → 400 + `egress.denied.scheme`. 형식이 틀리거나 풀 수 없는 이름 → 400(`egress.denied.bad_url`).
2. **host-allowlist** — 허용 목록(`127.0.0.1`, `localhost`, `shop.example`; 포트는 보지 않음) 밖의 `Host` → 400 + `request.denied.host`(target `host:<값 64자까지>`). 모든 경로에 적용.
3. **safe-redirect** — `GET /kit/redirect?next=<값>` → 302. `Location`은 `/`로 시작하고 `//`·`/\`로 시작하지 않는 상대 경로면 그 값, 허용 호스트(`shop.example`)의 https 주소면 그 값, 나머지(`//evil.example`, `https://evil.example/x`, `/\evil.example`, `javascript:alert(1)`)는 `/` + `redirect.denied.not_allowed`.
4. **upload** — `POST /kit/upload`, 본문 = 파일 바이트(`Content-Type: application/octet-stream`), 머리 `X-File-Name: <원래 이름>`.
   내용 앞 바이트로 PNG·JPEG·GIF·WebP만 받는다 → 201 `{"stored": "<32자 16진수>.<내용에서 정한 확장자>", "type": "image/png"}`(원래 이름은 쓰지 않는다).
   내용이 이미지가 아님 → 415 + `upload.rejected.type`. 2MB(2,097,152바이트) 초과 → 413 + `upload.rejected.size`.
5. **headers** — 모든 응답: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Content-Security-Policy`에 `default-src 'self'`와 `frame-ancestors 'none'`, `X-Powered-By` 없음.
   CORS(`secure-kit.cors.allowed-origins: [http://shop.example]`): `GET /kit/public`에 `Origin: http://shop.example` → `Access-Control-Allow-Origin: http://shop.example` + `Vary`에 `Origin`. `Origin: https://evil.example` → `Access-Control-Allow-Origin` 없음 + `cors.denied_origin`(INFO, target `origin:<값>`). 응답 자체는 200.
6. **rate-limit** — `POST /kit/login` `{"username","password"}`. 계정별 실패 5번/10분(`secure-kit.rate-limit.login.per-account: 5/10m`), IP별 50번/10분. 같은 계정 6번째 요청은 비밀번호가 맞아도 429 `{"code":429,"message":"잠시 후 다시 시도하세요",…}` + 머리 `Retry-After` + `auth.login.rate_limited`(target `account:<아이디>`). 실패 응답은 401 `{"message":"아이디 또는 비밀번호가 올바르지 않습니다"}`.
7. **password-policy** — `POST /kit/password/check` `{"password"}`: 10자 미만 → 422 `{"ok": false, "reason": "too_short"}`. 유출 목록(예제 목록: `password1234`, `qwerty123456`, `daon-pass-1!`, `1q2w3e4r5t`, `iloveyou123`)에 있으면 → 422 `{"ok": false, "reason": "breached"}` + `auth.password.rejected_breached`(INFO). 통과 → 200 `{"ok": true}`.
8. **constant-time-login** — 없는 아이디와 있는 아이디의 틀린 비밀번호가 **같은 상태·같은 본문**(401, 위 문구). 없는 아이디도 같은 비용의 해시 비교를 한다(시험: 없는 아이디 응답 시간 중앙값 ≥ 있는 아이디 틀린 비밀번호 중앙값의 0.5배).
9. **reset-link** — `POST /kit/reset/request` `{"username"}` → 늘 202 `{"message": "안내 메일을 보냈습니다(가입된 경우)"}`. 링크 주소는 설정 `secure-kit.reset-link.base-url`(예제 `http://shop.example`) + `/reset?token=<토큰>`이고 요청의 `Host`·`X-Forwarded-Host`로 정하지 않는다. `X-Forwarded-Host`가 기준과 다르면 `auth.reset.host_mismatch`.
   토큰은 1회용·15분. `POST /kit/reset/confirm` `{"token","newPassword"}` → 200 `{"ok": true}` · 같은 토큰 다시 → 400 + `auth.reset.token_reused`.
10. **step-up** — `POST /kit/account/email` `{"email"}`(로그인 필요). 머리 `X-Reauth-Password` 없음 → 401 `{"stepUp": "required", …}` + `auth.stepup.required`(INFO) · 틀림 → 401 `{"stepUp": "failed", …}` + `auth.stepup.failed` · 맞음 → 200 `{"ok": true}` + `account.change.high_risk`(INFO).
11. **token** — `GET /kit/me` → 200 `{"sub": "kit-user", "roles": ["user"]}`. `POST /kit/logout` → 204, 그 토큰으로 `/kit/me` → 401 + `auth.token.revoked_used`(target `jti:<앞 8자>`).
    머리 `{"alg":"none"}`로 바꾼 토큰(서명 없음) → 401 + `auth.token.denied_alg`. `exp` 없는 토큰·만료 토큰 → 401. 발급 토큰의 `exp - iat` ≤ 900.
12. **field-crypto** — `POST /kit/pii` `{"phone": "010-0000-3333"}`(로그인 필요) → 201 `{"id": <정수>}` · `GET /kit/pii/{id}` → `{"phone": "010-****-3333"}` · `GET /_kit/pii/raw/{id}` → `raw`가 `enc:1:`로 시작하고 원래 번호(하이픈 있든 없든)가 들어 있지 않다 · `GET /kit/pii/search?phone=010-0000-3333` → `{"ids": [...]}`에 그 id(검색용 해시 칸, 평문 비교 아님). 같은 번호를 두 번 넣어도 `raw`는 다르다(논스).
13. **startup-check** — `POST /_kit/startup/check`: 이름이 `secret`·`password`·`key`·`token`으로 끝나는 키의 값이 알려진 기본값(`changeme`, `change-me`, `secret`, `password`, `root`, `admin`, `minioadmin`, `guest`, `test`, `123456`, `macro123`) 이거나 12자 미만이면 `denied`. 예: `{"secure-kit.token.secret": "changeme", "spring.datasource.password": "root", "app.name": "shop", "app.api-key": "k-7f3a9c2e1b4d8a60"}` → `{"ok": false, "denied": ["secure-kit.token.secret", "spring.datasource.password"]}`(순서 무관). 앱 기동 때 같은 검사기가 돌고 걸리면 기동을 멈춘다(`config.startup.denied_default_secret`, 값은 찍지 않음).
14. **error-response** — `GET /kit/error`(처리 중 내부 예외 `IllegalStateException("SQLSyntaxErrorException: Unknown column 'x' in 'oms_order'")`) → 500 `{"code": 500, "message": "요청을 처리하지 못했습니다", "traceId": "<X-Trace-Id와 같은 값>"}`. 본문에 `Exception`·`oms_order`·`at `(스택 줄)·`SQL`이 없다.
15. **audit** — `GET /kit/admin/members?pageNum=1&pageSize=20`(kit-admin, 머리 `X-Access-Reason: CS-0001`) → 200 `{"list": [{"id": …, "name": …, "phone": "010-****-NNNN"}, …]}`(가짜 회원 25명 중 20명) · `/_kit/audit` 마지막 기록 = `{"actor": "admin:kit-admin", "action": "member.list", "target": "member:list?page=1&size=20", "count": 20, "reason": "CS-0001", "result": "ok", "ts", "trace_id"}` · kit-user가 부르면 403 + 감사 기록 `result: "denied"`, `count: 0`. 감사 기록에 `010-`이 없다.
    (actor 규칙: 역할 `admin`이면 `admin:<sub>`, 아니면 `user:<sub>`, 파트너는 `partner:<앱>`, 없으면 `anonymous`. 다른 인증을 쓰는 앱은 요청 속성 `secure-kit.actor`(Express `req.secureKitActor`)에 넣는다.)
16. **log-masking** — `POST /kit/log` `{"phone": "010-0000-4444", "password": "p@ss-word-77", "token": "<아무 JWT 모양>"}` → 204. 앱이 이 본문을 로그 한 줄에 그대로 찍지만, `/_kit/logs`에는 `010-****-4444`만 있고 `010-0000-4444`·`p@ss-word-77`·그 JWT가 없다.
17. **limits** — 본문 상한 1MB(1,048,576바이트, `/kit/upload`만 3MB): `POST /kit/echo` 1MB+1바이트 → 413 + `input.denied.too_large`, 작은 본문 → 200 `{"bytes": n}`. 쿠폰 코드: `GET /kit/coupon?code=<값>` 20자 초과 → 400 + `input.denied.too_long`(정규식 전에, 1초 안), `DAON-2026` → 200 `{"valid": true}`, 형식 밖 → 200 `{"valid": false}`. 정규식은 `^[A-Z0-9]+(-[A-Z0-9]+)*$`처럼 되돌아가기 폭주가 없는 모양.
18. **fail-closed** — `GET /kit/failclosed?dep=down`(본인 확인 의존성이 예외) → 503 + `auth.fail_closed.dependency_error` · `dep=up` → 200 `{"ok": true}`.
19. **resilient-client** — `GET /kit/resilient`(의존성 `/_kit/flaky`, 늘 503): 요청마다 재시도는 최대 1번이고 재시도 예산(최근 1분 새 요청의 10%, 최소 3번)을 넘지 않는다. 연속 실패 5번이면 서킷이 20초 열린다. 열려 있으면 의존성을 부르지 않고 503 + 머리 `X-Circuit: open` + 처음 열릴 때 `resilience.circuit.opened`. 의존성 실패(서킷 닫힘)는 502. 시험: 12번 부르면 `/_kit/flaky/count` ≤ 12 + 3이고 마지막 응답은 503 + `X-Circuit: open`.
20. **idempotency** — `POST /kit/refund` `{"orderId", "amount"}` + `Idempotency-Key`: 같은 키 두 번 → 같은 본문(`refundId` 같음), 처리 수는 한 번 늘어난다. 키 없음 → 400. 같은 키에 다른 본문 → 422.
    웹훅 `POST /kit/webhook`: 머리 `X-Timestamp: <유닉스 초>`, `X-Signature: sha256=<hex(HMAC-SHA256(비밀값, "<timestamp>.<본문 바이트>"))>`, 비밀값 `secure-kit.webhook.secret`(예제 `kit-lab-fake-webhook-secret`). 본문 `{"eventId","type","amount"}` → 200 `{"processed": true}` · 같은 `eventId` 다시 → 200 `{"duplicate": true}` + `payment.duplicate.event`(INFO) · 서명 틀림 또는 5분 넘은 시각 → 401 + `payment.denied.bad_signature`.
21. **budget** — `POST /kit/sms` `{"phone"}` → 202 `{"sent": true}`. 같은 번호 60초 안에 다시 → 429 + `budget.denied.sms`(target `phone:<sha256 앞 8자>`, 번호 원문 없음).
22. **no-store** — `Authorization`이 있는 요청의 응답(`GET /kit/me`)은 `Cache-Control`에 `no-store`. 로그인 없는 `GET /kit/public` → `Cache-Control: public, max-age=60`(no-store 없음), 본문 `{"ok": true}`.
23. **partner-scope** — 파트너 토큰(`/_kit/partner/token`, 범위 `orders:read`)으로 `GET /kit/partner/orders` → 200 `{"orders": [...]}` · `GET /kit/partner/export` → 403 + `partner.denied.scope`(actor `partner:courier-01`) · 사람 토큰(aud `kit`)으로 `/kit/partner/orders` → 401.
24. **approval** — `POST /kit/refund/high` `{"orderId", "amount"}`(kit-admin): 기준(`secure-kit.approval.policies.refund-high-value.threshold: 300000`) 미만 → 200 `{"status": "DONE"}` · 이상 → 202 `{"status": "PENDING_APPROVAL", "approvalId": "<번호>"}` + `approval.pending.second_approver`(INFO) · 같은 본문 + `X-Approval-Id` 승인 전 → 403 · 다른 사람(`fin-lee`) 승인 뒤 → 200 `{"status": "DONE"}` · 같은 승인 번호 다시 → 409. 금액·주문이 승인 때와 다르면 403.

## 2. 가게 예제(15-4 수업) — `test_shop.py`

가게 앱은 회사 서비스 흉내다. 자기 로그인(토큰 `shop-<무작위>`)을 쓰고, 15-4에서 부품 셋만 붙인다. 계정 `shop-admin` / `shop-pass-1!`.

| 시험 | 부품을 붙이기 전(시작 지점) | 붙인 뒤(완료 지점) |
|---|---|---|
| `test_error_response` · `GET /api/orders/x` | 500 본문에 내부 예외 이름·SQL 조각(`oms_order`)이 나간다 | 500 `{"code":500,"message":"요청을 처리하지 못했습니다","traceId"}`, 내부 문구 없음 |
| `test_rate_limit_login` · `POST /api/login` 틀린 비밀번호 6번 | 6번째도 401 | 6번째 429 + `auth.login.rate_limited` |
| `test_audited_member_read` · `GET /api/admin/members/3`(shop-admin, `X-Access-Reason: CS-0002`) | 감사 기록 없음(`/_kit/audit` 404) | 기록 `action member.read`, `target member:3`, `actor admin:shop-admin`, `count 1`, `reason CS-0002` |

가게 앱의 랩 전용 엔드포인트: 부품을 붙이면 `/_kit/reset`·`/_kit/events`·`/_kit/audit`이 생긴다(설정 `secure-kit.lab.expose: true`). 붙이기 전에는 `POST /api/_reset`(204)만 있다. 두 상태 모두 `GET /api/health` → 200 `{"ok": true}`.
